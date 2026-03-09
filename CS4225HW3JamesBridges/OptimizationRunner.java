import java.io.*;
import java.util.*;

public class OptimizationRunner {
	private static final int[] THREAD_COUNTS = {2, 4, 8, 16};
	private static final int[] EVENT_COUNTS = {100, 500, 1000};
	private static final String[] OPTIMIZATIONS = {
		"baseline",
		"executor",
		"padded_counter",
		"virtual_threads",
		"combined"
	};

	public static void main(String[] args) throws Exception {
		List<Metrics> allMetrics = new ArrayList<>();

		// Run experiments with different configurations
		for (String opt : OPTIMIZATIONS) {
			for (int threads : THREAD_COUNTS) {
				for (int events : EVENT_COUNTS) {
					System.out.println("\n=== Runnint: " + opt + " with " + threads + " threads, " + events + " events ===");

					// Run the experiment
					List<Metrics> metrics = runExperiment(opt, threads, events);
					allMetrics.addAll(metrics);

					// Wait between runs
					Thread.sleep(5000);
				}
			}
		}

		// Write results to CSV
		writeResultsToCSV(allMetrics);
	}

	private static List<Metrics> runExperiment(String optimization, int numThreads, int numEvents) throws Exception {
		// Kill any existing processes
		Runtime.getRuntime().exec("./stop.sh").waitFor();
		Thread.sleep(2000);

		Runtime.getRuntime().exec("javac *.java").waitFor();

		// Start nodes with modified code
		ProcessBuilder pb = new ProcessBuilder("./run-" + optimization + ".sh");
		Process process = pb.start();

		// Wait for completion
		Thread.sleep(30000);

		// Parse logs and collect metrics
		List<Metrics> metrics = parseLogs(optimization, numThreads, numEvents);

		return metrics;
	}

	private static List<Metrics> parseLogs(String optimization, int numThreads, int numEvents) throws Exception {
		List<Metrics> metrics = new ArrayList<>();

		for (int nodeId = 1; nodeId <= 5; nodeId++) {
			File logFile = new File("node" + nodeId + ".log");
			if (!logFile.exists()) continue;

			try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
				String line;
				long startTime = 0;
				long endTime = 0;
				long totalEvents = 0;
				int msgSent = 0;
				int msgReceived = 0;
				int finalLamport = 0;
				List<Long> counterValues = new ArrayList<>();

				while ((line = reader.readLine()) != null) {
					if (line.contains("Execution time=")) {
						String[] parts = line.split("=");
						endTime = Long.parseLong(parts[1].trim().replace("ms", "").trim());
					} else if (line.contains("Total events processed:")) {
						totalEvents = Long.parseLong(line.split(":")[1].trim());
					} else if (line.contains("Messages sent:")) {
						msgSent = Integer.parseInt(line.split(":")[1].trim());
					} else if (line.contains("Messages received:")) {
						msgReceived = Integer.parseInt(line.split(":")[1].trim());
					} else if (line.contains("Final Lamport time:")) {
						finalLamport = Integer.parseInt(line.split(":")[1].trim());
					} else if (line.contains("Counter")) {
						String[] parts = line.split(":");
						counterValues.add(Long.parseLong(parts[1].trim()));
					}
				}

				startTime = System.currentTimeMillis() - endTime;

				metrics.add(new Metrics(
						String.valueOf(nodeId), optimization, startTime, 
						System.currentTimeMillis(), totalEvents, msgSent,
						msgReceived, finalLamport, counterValues
				));
			}
		}

		return metrics;
	}

	private static void writeResultsToCSV(List<Metrics> metrics) throws IOException {
		try (FileWriter fw = new FileWriter("performance_results.csv");
				PrintWriter pw = new PrintWriter(fw)) {
			pw.println(Metrics.getHeader());
			for (Metrics m : metrics) {
				pw.println(m.toString());
			}
		}
		System.out.println("\nResults written to performance_results.csv");
	}
}

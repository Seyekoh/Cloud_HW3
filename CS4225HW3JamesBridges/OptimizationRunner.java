import java.io.*;
import java.util.*;

public class OptimizationRunner {
	private static final int[] THREAD_COUNTS = {2, 4, 8, 16};
	private static final int[] EVENT_COUNTS = {100, 500, 1000};
	private static final String[] OPTIMIZATIONS = {
		"baseline",		
		"executor",
		"padded",
		"virtual"
	};

	public static void main(String[] args) throws Exception {
		List<Metrics> allMetrics = new ArrayList<>();

		// Run experiments with different configurations
		for (String opt : OPTIMIZATIONS) {
			for (int threads : THREAD_COUNTS) {
				for (int events : EVENT_COUNTS) {
					System.out.println("\n=== Running: " + opt + " with " + threads + " threads, " + events + " events ===");

					// Run the experiment
					List<Metrics> metrics = runExperiment(opt, threads, events);
					allMetrics.addAll(metrics);

					// Quick summary
					double avgThroughput = metrics.stream()
						.mapToDouble(Metrics::getThroughput)
						.average()
						.orElse(0);
					System.out.println("<<< " + opt + " complete. Avg throughput: " + String.format("%.2f", avgThroughput) + " events/sec");

					// Wait between runs
					System.out.println("Waiting 5 seconds before next run...");
					Thread.sleep(5000);
				}
			}
		}

		// Write results to CSV
		writeResultsToCSV(allMetrics);
	}

	private static List<Metrics> runExperiment(String optimization, int numThreads, int numEvents) throws Exception {
		// Kill any existing processes
		Runtime.getRuntime().exec(new String[]{"bash", "./stop.sh"}).waitFor();
		Thread.sleep(2000);

		// Start nodes with modified code
		ProcessBuilder pb = new ProcessBuilder("bash", "./run-" + optimization + ".sh");
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
			// Determine log file name based on optimization
			String logFileName = "node" + nodeId;

			switch (optimization) {
				case "executor":
					logFileName += "-executor.log";
					break;
				case "padded":
					logFileName += "-padded.log";
					break;
				case "virtual":
					logFileName += "-virtual.log";
					break;
				default:
					logFileName += ".log";
					break;
			}

			File logFile = new File(logFileName);
			if (!logFile.exists()) {
				System.err.println("Warning: Log file not found: " + logFileName);
				continue;
			}

			try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
				String line;
				long executionTime = 0;
				long totalEvents = 0;
				int msgSent = 0;
				int msgReceived = 0;
				int finalLamport = 0;
				List<Long> counterValues = new ArrayList<>();

				while ((line = reader.readLine()) != null) {
					if (line.contains("Execution time =")) {
						String[] parts = line.split("=");
						String timeStr = parts[1].trim().replace("ms", "").trim();
						executionTime = Long.parseLong(timeStr);
					} else if (line.contains("Total events processed:")) {
						String[] parts = line.split(":");
						totalEvents = Long.parseLong(parts[1].trim());
					} else if (line.contains("Messages sent:")) {
						String[] parts = line.split(":");
						msgSent = Integer.parseInt(parts[1].trim());
					} else if (line.contains("Messages received:")) {
						String[] parts = line.split(":");
						msgReceived = Integer.parseInt(parts[1].trim());
					} else if (line.contains("Final Lamport time:")) {
						String[] parts = line.split(":");
						finalLamport = Integer.parseInt(parts[1].trim());
					} else if (line.contains("Counter") || line.contains("PaddedCounter")) {
						String[] parts = line.split(":");
						if (parts.length >= 2) {
							counterValues.add(Long.parseLong(parts[1].trim()));
						}
					}
				}

				long currentTime = System.currentTimeMillis(); 
				long calculatedStartTime = currentTime - executionTime;

				metrics.add(new Metrics(
						String.valueOf(nodeId), optimization, calculatedStartTime, 
						currentTime, totalEvents, msgSent,
						msgReceived, finalLamport, counterValues
				));

				System.out.println("Parsed node " + nodeId + ": execTime = " + executionTime + "ms, events = " + totalEvents);
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

import java.io.*;
import java.util.*;
import java.nio.file.*;

public class LogParser {
	static class NodeMetrics {
		String nodeId;
		String optimization;
		long execTime;
		long totalEvents;
		int msgSent;
		int msgReceived;
		int finalLamport;
		List<Long> counterValues = new ArrayList<>();

		double getThroughput() {
			return (double) totalEvents / (execTime / 1000.0);
		}

		@Override
		public String toString() {
			return String.format("%s, %s, %d, %d, %.2f, %d, %d, %d, %s",
					nodeId, optimization, execTime, totalEvents, getThroughput(),
					msgSent, msgReceived, finalLamport, counterValues);
		}
	}

	public static void main(String[] args) {
		List<NodeMetrics> allMetrics = new ArrayList<>();

		// Define the log files to parse
		String[][] logFiles = {
			// baseline logs
			{"1", "baseline", "node1.log"},
			{"2", "baseline", "node2.log"},
			{"3", "baseline", "node3.log"},
			{"4", "baseline", "node4.log"},
			{"5", "baseline", "node5.log"},

			// executor logs
			{"1", "executor", "node1-executor.log"},
			{"2", "executor", "node2-executor.log"},
			{"3", "executor", "node3-executor.log"},
			{"4", "executor", "node4-executor.log"},
			{"5", "executor", "node5-executor.log"},

			// padded logs
			{"1", "padded", "node1-padded.log"},
			{"2", "padded", "node2-padded.log"},
			{"3", "padded", "node3-padded.log"},
			{"4", "padded", "node4-padded.log"},
			{"5", "padded", "node5-padded.log"},
			
			// virtual logs
			{"1", "virtual", "node1-virtual.log"},
			{"2", "virtual", "node2-virtual.log"},
			{"3", "virtual", "node3-virtual.log"},
			{"4", "virtual", "node4-virtual.log"},
			{"5", "virtual", "node5-virtual.log"}
		};

		for (String[] logInfo : logFiles) {
			String nodeId = logInfo[0];
			String optimization = logInfo[1];
			String filename = logInfo[2];

			NodeMetrics metrics = parseLogFiles(nodeId, optimization, filename);
			if (metrics != null) {
				allMetrics.add(metrics);
				System.out.println("Parsed " + filename + ": " + metrics.execTime + "ms, " + metrics.totalEvents + " events, " + String.format("%.2f", metrics.getThroughput()) + " events/sec");
			}
		}

		// Write to CSV
		writeToCSV(allMetrics, "performance_results.csv");

		// Print summar table
		printSummary(allMetrics);
	}

	private static NodeMetrics parseLogFiles(String nodeId, String optimization, String filename) {
		NodeMetrics metrics = new NodeMetrics();
		metrics.nodeId = nodeId;
		metrics.optimization = optimization;

		Path path = Paths.get(filename);
		if (!Files.exists(path)) {
			System.err.println("Warning: File not found: " + filename);
			return null;
		}

		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains("Execution time =")) {
					// Format: "Execution time = 11399 ms"
					String[] parts = line.split("=");
					String timeStr = parts[1].trim().replace("ms", "").trim();
					metrics.execTime = Long.parseLong(timeStr);
				}
				else if (line.contains("Total events processed:")) {
					String[] parts = line.split(":");
					metrics.totalEvents = Long.parseLong(parts[1].trim());
				}
				else if (line.contains("Messages sent:")) {
					String[] parts = line.split(":");
					metrics.msgSent = Integer.parseInt(parts[1].trim());
				}
				else if (line.contains("Messages received:")) {
					String[] parts = line.split(":");
					metrics.msgReceived = Integer.parseInt(parts[1].trim());
				}
				else if (line.contains("Final Lamport time:")) {
					String[] parts = line.split(":");
					metrics.finalLamport = Integer.parseInt(parts[1].trim());
				}
				else if (line.contains("Counter")) {
					String[] parts = line.split(":");
					if (parts.length >= 2) {
						metrics.counterValues.add(Long.parseLong(parts[1].trim()));
					}
				}
			}

			// Validate we got all required data
			if (metrics.execTime == 0 || metrics.totalEvents == 0) {
				System.err.println("Warning: Incomplete data in " + filename);
				return null;
			}

			return metrics;
		} catch (IOException e) {
			System.err.println("Error reading " + filename + ": " + e.getMessage());
			return null;
		}
	}

	private static void writeToCSV(List<NodeMetrics> metrics, String filename) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
			// Write Header
			writer.println("NodeId,Optimization,ExecTime(ms),TotalEvents,Throughput(events/sec),MsgSent,MsgReceived,FinalLamport,CounterValues");

			// Write data
			for (NodeMetrics m : metrics) {
				writer.println(m.toString());
			}

			System.out.println("\nResults written to " + filename);

		} catch (IOException e) {
			System.err.println("Error writing CSV: " + e.getMessage());
		}
	}

	private static void printSummary(List<NodeMetrics> allMetrics) {
		System.out.println("\n" + "=".repeat(80));
		System.out.println("PERFORMANCE SUMMARY");
		System.out.println("+".repeat(80));

		// Group by optimization
		Map<String, List<NodeMetrics>> byOpt = new HashMap<>();
		for (NodeMetrics m : allMetrics) {
			byOpt.computeIfAbsent(m.optimization, k -> new ArrayList<>()).add(m);
		}

		// Print header
		System.out.printf("%-12s %-12s %-12s %-12s %-12s %-12s %-12s%n",
				"Optimization", "Avg Time(ms)", "Avg Events", "Avg Thruput", "Min Thruput", "Max Thruput", "Improvement");
		System.out.println("-".repeat(80));

		double baselineAvg = 0;

		for (String opt : Arrays.asList("baseline", "executor", "padded", "virtual")) {
			List<NodeMetrics> list = byOpt.get(opt);
			if (list == null || list.isEmpty()) continue;

			double avgTime = list.stream().mapToLong(m -> m.execTime).average().orElse(0);
			double avgEvents = list.stream().mapToLong(m -> m.totalEvents).average().orElse(0);
			double avgThruput = list.stream().mapToDouble(m -> m.getThroughput()).average().orElse(0);
			double minThruput = list.stream().mapToDouble(m -> m.getThroughput()).min().orElse(0);
			double maxThruput = list.stream().mapToDouble(m -> m.getThroughput()).max().orElse(0);

			if (opt.equals("baseline")) {
				baselineAvg = avgThruput;
			}

			double improvement = ((avgThruput - baselineAvg) / baselineAvg) * 100;

			System.out.printf("%-12s %-12.0f %-12.0f %-12.2f %-12.2f %-12.2f %+10.1f%%%n",
					opt, avgTime, avgEvents, avgThruput, minThruput, maxThruput, improvement);
		}

		System.out.println("=".repeat(80));

		// Print node-level details
		System.out.println("\n" + "=".repeat(80));
		System.out.println("DETAILED NODE RESULTS");
		System.out.println("=".repeat(80));
		System.out.printf("%-4s %-12s %-12s %-12s %-12s %-12s%n",
				"Node", "Optimization", "ExecTime(ms)", "Events", "Thruput", "FinalClock");
		System.out.println("-".repeat(80));

		for (NodeMetrics m : allMetrics) {
			System.out.printf("%-4s %-12s %-12d %-12d %-12.2f %-12d%n",
					m.nodeId, m.optimization, m.execTime, m.totalEvents,
					m.getThroughput(), m.finalLamport);
		}
	}

}

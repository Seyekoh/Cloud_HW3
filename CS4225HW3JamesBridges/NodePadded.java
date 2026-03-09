import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class NodePadded extends Node {
	// Override counters list to use PaddedCounter instead of Counter
	private final List<PaddedCounter> paddedCounters;

	public NodePadded(int nodeId) {
		super(nodeId);
		this.paddedCounters = new ArrayList<>();

		// Initialize padded counters for each thread
		for (int i = 0; i < numThreads; i++) {
			paddedCounters.add(new PaddedCounter());
		}

		System.out.println("Node " + nodeId + " using PaddedCounter to prevent false sharing");
	}

	@Override
	protected void startWorkerThreads() {
		System.out.println("Node " + nodeId + " starting " + numThreads + " worder threads with PaddedCounter");

		for (int i = 0; i < numThreads; i++) {
			final int threadId = i;
			final PaddedCounter counter = paddedCounters.get(i);

			Thread thread = new Thread(() -> {
				try {
					int numEvents = 100;
					for (int event = 0; event < numEvents; event++) {
						// Process local event with padded counter
						processLocalEventPadded(threadId, counter);

						// Randomly decide to send message to remote node
						if (random.nextBoolean()) {
							sendMessageToRandomNode(threadId);
						}

						// Small delay to simulate processing time
						try {
							Thread.sleep(random.nextInt(100));
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				} finally {
					workersDoneLatch.countDown();
				}
			});

			thread.setName("Node" + nodeId + "-padded-worker-" + i);
			workerThreads.add(thread);
			thread.start();
		}
	}

	private void processLocalEventPadded(int threadId, PaddedCounter counter) {
		// Increment padded counter
		counter.increment();

		// Update Laport clock for local event
		clock.tick();

		System.out.println("Thread within node" + nodeId + " executing local event (padded)");

		totalEvents.incrementAndGet();
	}

	@Override
	protected void handleClientMessages(Socket socket) {
		try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(socket.getInputStream()))) {
			String message;
			while (running && (message = reader.readLine()) != null) {
				try {
					// Parse message: "timestamp:senderId"senderThreadId"
					String[] parts = message.split(":");
					int receivedTime = Integer.parseInt(parts[0]);
					int senderId = Integer.parseInt(parts[1]);

					// Update Lamport clock with received time
					clock.update(receivedTime);

					// Find which thread will process this
					int threadId = (int)(totalEvents.get() % numThreads);
					PaddedCounter counter = paddedCounters.get(threadId);
					counter.increment();

					messagesReceived.incrementAndGet();
					totalEvents.incrementAndGet();

					System.out.println("Thread-" + threadId + " executing received event (padded) " + "(t = " + receivedTime + ") from Node" + senderId);

					// Simulate processing received event
					try {
						Thread.sleep(random.nextInt(5, 15));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				} catch (Exception e) {
					System.err.println("Node " + nodeId + " error parsing message: " + message);
				}
			}
		} catch (IOException e) {
			if (running) {
				System.err.println("Node " + nodeId + " error reading from socket: " + e.getMessage());
			}
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				// Ignore close errors
			}
		}
	}

	@Override
	public void start() {
		try {
			// Start server socket to accept connections
			serverSocket = new ServerSocket(port);
			System.out.println("Node " + nodeId + " listening on port " + port);

			// Start accepting connections in background
			startAcceptingConnections();

			// Connect to other nodes
			connectToOtherNodes();

			// Give connections time to establish
			Thread.sleep(2000);

			// Create and start worker threads with padded counters
			startWorkerThreads();

			// Wait for all workers to complete
			workersDoneLatch.await();

			// Give some time for final messages to be processed
			Thread.sleep(2000);

			// Stop accepting new connections
			running = false;
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
			}

			// Print final statistics
			long executionTime = System.currentTimeMillis() - startTime;
			            System.out.println("\n=== Node " + nodeId + " Statistics (PaddedCounter) ===");
            System.out.println("Total events processed: " + totalEvents.get());
            System.out.println("Messages sent: " + messagesSent.get());
            System.out.println("Messages received: " + messagesReceived.get());
            System.out.println("Execution time = " + executionTime + " ms");
            System.out.println("Final Lamport time: " + clock.getTime());
            
            // Print final padded counter values
            for (int i = 0; i < numThreads; i++) {
                System.out.println("PaddedCounter " + i + " value: " + paddedCounters.get(i).getValue());
            }
		} catch (Exception e) {
			System.err.println("Node " + nodeId + " error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: java NodePadded <nodeId>");
			System.err.println("nodeId must be 1, 2, 3, 4, or 5");
			System.exit(1);
		}

		int nodeId = Integer.parseInt(args[0]);
		if (nodeId < 1 || nodeId > 5) {
			System.err.println("nodeId must be between 1 and 5");
			System.exit(1);
		}

		NodePadded node = new NodePadded(nodeId);
		node.start();
	}
}

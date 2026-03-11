import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public class Node {
	protected final int nodeId;
	protected final int port;
	protected final int numThreads = 4;
	protected final LamportClock clock;
	protected final List<Counter> counters;
	protected final List<Socket> connections = new ArrayList<>();
	protected final List<Thread> workerThreads = new ArrayList<>();
	protected final Random random = new Random(4225); // SEED = 4225 for reproducibility
	protected final AtomicLong totalEvents = new AtomicLong(0);
	protected final AtomicInteger messagesSent = new AtomicInteger(0);
	protected final AtomicInteger messagesReceived = new AtomicInteger(0);
	protected final long startTime;
	protected final CountDownLatch workersDoneLatch;

	protected volatile boolean running = true;

	protected ServerSocket serverSocket;

	// Port mapping based on nodeId
	protected static final Map<Integer, Integer> PORT_MAP = new HashMap<>();
	static {
		PORT_MAP.put(1, 4225);
		PORT_MAP.put(2, 4226);
		PORT_MAP.put(3, 4227);
		PORT_MAP.put(4, 4228);
		PORT_MAP.put(5, 4229);
	}

	public Node(int nodeId) {
		this.nodeId = nodeId;
		this.port = PORT_MAP.get(nodeId);
		this.clock = new LamportClock(nodeId);
		this.counters = new ArrayList<>();
		this.startTime = System.currentTimeMillis();
		this.workersDoneLatch = new CountDownLatch(numThreads);

		// Initialize counters for each thread
		for (int i = 0; i < numThreads; i++) {
			counters.add(new Counter());
		}

		System.out.println("Node " + nodeId + " initialized on port " + port);
	}

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

			// Create and start worker threads
			startWorkerThreads();

			// Wait for all workers to complete
			workersDoneLatch.await();
			
			// Give some time for final messages to be processed
			Thread.sleep(2000);

			// Stop accepting new connections
			running = false;
			serverSocket.close();

			// Print final statistics
			long executionTime = System.currentTimeMillis() - startTime;
			System.out.println("\n=== Node " + nodeId + " Statistics ===");
			System.out.println("Total events processed: " + totalEvents.get());
			System.out.println("Messages sent: " + messagesSent.get());
			System.out.println("Messages received: " + messagesReceived.get());
			System.out.println("Execution time = " + executionTime + " ms");
			System.out.println("Final Lamport time: " + clock.getTime());

			for (int i = 0; i < numThreads; i++) {
				System.out.println("Counter " + i + " value: " + counters.get(i).getValue());
			}
		} catch (Exception e) {
			System.err.println("Node " + nodeId + " error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	protected void startWorkerThreads() {
		System.out.println("Node " + nodeId + " starting " + numThreads + " worker threads with 100 events each");

		for (int i = 0; i < numThreads; i++) {
			final int threadId = i;
			final Counter counter = counters.get(i);

			Thread thread = new Thread(() -> {
				try {
					// Process exactly 100 events per thread
					int numEvents = 100;

					for (int event = 0; event < numEvents; event++) {
						// Process local event
						processLocalEvent(threadId, counter);

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
			thread.setName("Node" + nodeId + "-worker-" + i);
			workerThreads.add(thread);
			thread.start();
		}
	}

	protected void processLocalEvent(int threadId, Counter counter) {
		// Increment local counter
		counter.increment();

		// Update Lamport clock for local event
		clock.tick();

		System.out.println("Thread within node" + nodeId + " executing local event");

		totalEvents.incrementAndGet();
	}

	protected void sendMessageToRandomNode(int threadId) {
		// Choose random node (1-5 excluding self)
		int targetNodeId;
		do {
			targetNodeId = 1 + random.nextInt(5);
		} while (targetNodeId == nodeId);

		int targetPort = PORT_MAP.get(targetNodeId);

		// Get current timestamp for message
		int timestamp = clock.getTime();

		// Make a copy for the lambda
		final int currentThreadId = threadId;
		final int currentTargetNodeId = targetNodeId;
		final int currentTimestamp = timestamp;

		// Send message in a separate thread to not block
		new Thread(() -> {
			try (Socket socket = new Socket("localhost", targetPort);
					PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
						// Message format: "timestamp:nodeId:threadId"
						out.println(currentTimestamp + ":" + nodeId + ":" + currentThreadId);
						
						messagesSent.incrementAndGet();

						System.out.println("Thread-" + currentThreadId + " within node" + nodeId + " sent event (t=" + currentTimestamp + ") to Node" + currentTargetNodeId);

					} catch (IOException e) {
						// Silently fail - nodes might not be ready or already done	
					}
		}).start();
	}

	protected void startAcceptingConnections() {
		// This runs in its own thread to accept incoming connections
		new Thread(() -> {
			while (running) {
				try {
					Socket clientSocket = serverSocket.accept();
					synchronized(connections) {
						connections.add(clientSocket);
					}

					// Handle client messages in new thread
					Thread handlerThread = new Thread(() -> handleClientMessages(clientSocket));
					handlerThread.start();
				} catch (Exception e) {
					if (running) {
						System.err.println("Node " + nodeId + " accept error: " + e.getMessage());
					}
				}
			}
		}).start();
	}

	protected void connectToOtherNodes() {
		for (int otherId = 1; otherId <= 5; otherId++) {
			if (otherId == nodeId) continue;	// Don't connect to yourself

			int otherPort = PORT_MAP.get(otherId);
			int maxRetries = 5;
			int retryDelay = 1000; // 1 second

			for (int retry = 1; retry <= maxRetries; retry++) {
				try {
					Socket socket = new Socket("localhost", otherPort);
					synchronized(connections) {
						connections.add(socket);
					}
					System.out.println("Node " + nodeId + " connected to Node " + otherId);

					// Start a thread to handle messages to/from this connection
					Thread handlerThread = new Thread(() -> handleClientMessages(socket));
					handlerThread.start();

					break; // Success - exit retry loop
				} catch (ConnectException e) {
					if (retry == maxRetries) {
						System.err.println("Node " + nodeId + " failed to connect to Node " + otherId);
					}
					try {
						Thread.sleep(retryDelay);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				} catch (IOException e) {
					System.err.println("Node " + nodeId + " error connecting to Node " + otherId + ": " + e.getMessage());
					break;
				}
			}
		}
	}

	protected void handleClientMessages(Socket socket) {
		try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(socket.getInputStream()))) {

			String message;
			while (running && (message = reader.readLine()) != null) {
				try {
					// Parse message: "timestamp:senderId:senderThreadId"
					String[] parts = message.split(":");
					int receivedTime = Integer.parseInt(parts[0]);
					int senderId = Integer.parseInt(parts[1]);
					int senderThreadId = Integer.parseInt(parts[2]);

					// Update Lamport clock with received time
					clock.update(receivedTime);

					// Find which thread will process this
					int threadId = (int)(totalEvents.get() % numThreads);
					Counter counter = counters.get(threadId);
					counter.increment();

					messagesReceived.incrementAndGet();
					totalEvents.incrementAndGet();
					System.out.println("Thread-" + threadId + " executing received event " + "(t = " + receivedTime + ") from Node" + senderId);

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

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: java Node <nodeId>");
			System.err.println("nodeId must be 1, 2, 3, 4, or 5");
			System.exit(1);
		}

		int nodeId = Integer.parseInt(args[0]);
		if (nodeId < 1 || nodeId > 5) {
			System.err.println("nodeId must be between 1 and 5");
			System.exit(1);
		}

		Node node = new Node(nodeId);
		node.start();
	}
}

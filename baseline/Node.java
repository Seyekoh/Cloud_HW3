import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Node {
	private final int nodeId;
	private final int port;
	private final int numThreads = 2;
	private final LamportClock clock;
	private final List<Counter> counters;
	private final List<Socket> connections = new ArrayList<>();

	private ServerSocket serverSocket;

	// Port mapping based on nodeId
	private static final Map<Integer, Integer> PORT_MAP = new HashMap<>();
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

			// Start thread to accept connections from other nodes
			Thread acceptThread = new Thread(this::acceptConnections);
			acceptThread.start();

			// Connect to other nodes
			connectToOtherNodes();

			// TODO: Start even processor threads

			// Keep main thread alive
			Thread.currentThread().join();

		} catch (Exception e) {
			System.err.println("Node " + nodeId + " error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void acceptConnections() {
		while (true) {
			try {
				Socket clientSocket = serverSocket.accept();
				synchronized(connections) {
					connections.add(clientSocket);
				}
				System.out.println("Node " + nodeId + " accepted connection from " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

				// Start a thread to handle messages from this connection
				Thread handlerThread = new Thread(() -> handleClientMessages(clientSocket));
				handlerThread.start();

			} catch (Exception e) {
				System.err.println("Node " + nodeId + " accept error: " + e.getMessage());
			}
		}
	}

	private void connectToOtherNodes() {
		for (int otherId = 1; otherId <=5; otherId++) {
			if (otherId == nodeId) continue;	// Don't connect to yourself

			int otherPort = PORT_MAP.get(otherId);
			int maxRetries = 3;
			int retryDelay = 1000; // 1 second

			for (int retry = 1; retry <= maxRetries; retry++) {
				try {
					System.put.println("Node " + nodeId + " attempting to connect to Node " + otherId + " on port " + otherPort + " (attempt " + retry + ")");

					Socket socket = new Socket("localhost", otherPort);
					synchronized(connections) {
						connections.add(socket);
					}
					System.out.println("Node " + nodeId + " connected to Node " + otherId);

					// Start a thread to handle messages to/from this connection
					Thread handlerThread = new Thread(() -> handleClientMessages(socket));
					handlerThread.start();

					break; // Success - exit retry loop
				} catch (ConnecException e) {
					System.err.println("Node " + nodeId + " connection to Node " + otherId + " failed (attempt " + retry + ")");

					if (retry < maxRetries) {
						try {
							Thread.sleep(retryDelay);
						} catch (InterruptedException io) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				} catch (IOException e) {
					System.err.println("Node " + nodeId + " error connecting to Node " + otherId + ": " + e.getMessage());
					break;
				}
			}
		}
	}

	private void handleCLientMessages(Socket socket) {
		try (BufferedReader reader = new BufferedReader(
					new InputStreameReader(socket.getInputStream()))) {
			String message;
			while ((message = reader.readLine()) != null) {
				System.out.println("Node " + nodeId + " received: " + message);

				// TODO: Parse and process messages

			}
		} catch (IOException e) {
			System.err.println("Node " + nodeId + " error reading from socket: " e.getMessage());
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
			System.err.println("nodeId must be 1, 3, 4, or 5");
			Systm.exit(1);
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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class NodeExecutor extends Node {
	private final ExecutorService workerExecutor;
	private final ExecutorService senderExecutor;

	public NodeExecutor(int nodeId) {
		super(nodeId);
		this.workerExecutor = Executors.newFixedThreadPool(numThreads);
		this.senderExecutor = Executors.newCachedThreadPool();
	}

	@Override
	protected void startWorkerThreads() {
        System.out.println("Node " + nodeId + " starting " + numThreads + " worker threads with ExecutorService");
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            final Counter counter = counters.get(i);
            
            workerExecutor.submit(() -> {
                try {
                    int numEvents = 100;
                    for (int event = 0; event < numEvents; event++) {
                        this.processLocalEvent(threadId, counter);
                        if (random.nextBoolean()) {
                            sendMessageToRandomNode(threadId);
                        }
                        Thread.sleep(random.nextInt(100));
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    workersDoneLatch.countDown();
                }
            });
        }
	}

	@Override
	protected void sendMessageToRandomNode(int threadId) {
	    // Choose random node (1-5 excluding self)
		int targetNodeId;
		do {
			targetNodeId = 1 + random.nextInt(5);
		} while (targetNodeId == nodeId);

		int targetPort = PORT_MAP.get(targetNodeId);
		int timestamp = clock.getTime();

		// Make final copies for lambda
		final int currentThreadId = threadId;
		final int currentTargetNodeId = targetNodeId;
		final int currentTimestamp = timestamp;

		// Use executor instead of creating new Thread
		senderExecutor.submit(() -> {
			try (Socket socket = new Socket("localhost", targetPort);
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

				// Message format: "timestamp:nodeId:threadId"
				out.println(currentTimestamp + ":" + nodeId + ":" + currentThreadId);

				messagesSent.incrementAndGet();

				System.out.println("Thread-" + currentThreadId + " within node" + nodeId + " sent event (t=" + currentTimestamp + ") to Node" + currentTargetNodeId);

			} catch (IOException e) {
				// Silently fail - nodes might not be ready or already done  
			}
		});
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

        	// Create and start worker threads using executor
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

        	// Shutdown executors
        	workerExecutor.shutdown();
        	senderExecutor.shutdown();

        	// Wait for pending tasks to complete
        	if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            	workerExecutor.shutdownNow();
        	}
        	if (!senderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            	senderExecutor.shutdownNow();
        	}

        	// Print final statistics
        	long executionTime = System.currentTimeMillis() - startTime;
        	System.out.println("\n=== Node " + nodeId + " Statistics (Executor) ===");
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
}

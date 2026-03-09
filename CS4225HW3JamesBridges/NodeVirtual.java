import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class NodeVirtual extends Node {
    
    public NodeVirtual(int nodeId) {
        super(nodeId);
        System.out.println("Node " + nodeId + " using Virtual Threads");
    }
    
    @Override
    protected void startWorkerThreads() {
        System.out.println("Node " + nodeId + " starting " + numThreads + 
                         " virtual worker threads with 100 events each");
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            final Counter counter = counters.get(i);
            
            // Create virtual thread instead of platform thread
            Thread.ofVirtual()
                .name("Node" + nodeId + "-virtual-worker-" + i)
                .start(() -> {
                    try {
                        int numEvents = 100;
                        for (int event = 0; event < numEvents; event++) {
                            // Process local event
                            processLocalEvent(threadId, counter);
                            
                            // Randomly decide to send message to remote node
                            if (random.nextBoolean()) {
                                sendMessageToRandomNodeVirtual(threadId);
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
        }
    }
    
    private void sendMessageToRandomNodeVirtual(int threadId) {
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
        
        // Use virtual thread for message sending
        Thread.ofVirtual().start(() -> {
            try (Socket socket = new Socket("localhost", targetPort);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                
                // Message format: "timestamp:nodeId:threadId"
                out.println(currentTimestamp + ":" + nodeId + ":" + currentThreadId);
                
                messagesSent.incrementAndGet();
                
                System.out.println("Thread-" + currentThreadId + " within node" + nodeId + 
                                 " sent event (t=" + currentTimestamp + ") to Node" + currentTargetNodeId + 
                                 " [Virtual]");
                
            } catch (IOException e) {
                // Silently fail - nodes might not be ready or already done  
            }
        });
    }
    
    @Override
    protected void startAcceptingConnections() {
        // Use virtual thread for accepting connections
        Thread.ofVirtual().start(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    synchronized(connections) {
                        connections.add(clientSocket);
                    }
                    
                    // Handle client messages in virtual thread
                    Thread.ofVirtual().start(() -> handleClientMessages(clientSocket));
                    
                } catch (Exception e) {
                    if (running) {
                        System.err.println("Node " + nodeId + " accept error: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    @Override
    protected void connectToOtherNodes() {
        // Use virtual threads for connection attempts
        for (int otherId = 1; otherId <= 5; otherId++) {
            if (otherId == nodeId) continue;
            
            final int targetId = otherId;
            final int targetPort = PORT_MAP.get(targetId);
            
            // Spawn virtual thread for each connection attempt
            Thread.ofVirtual().start(() -> {
                int maxRetries = 5;
                int retryDelay = 1000;
                
                for (int retry = 1; retry <= maxRetries; retry++) {
                    try {
                        Socket socket = new Socket("localhost", targetPort);
                        synchronized(connections) {
                            connections.add(socket);
                        }
                        System.out.println("Node " + nodeId + " connected to Node " + targetId + " [Virtual]");
                        
                        Thread.ofVirtual().start(() -> handleClientMessages(socket));
                        
                        break;
                        
                    } catch (ConnectException e) {
                        if (retry == maxRetries) {
                            System.err.println("Node " + nodeId + " failed to connect to Node " + targetId);
                        }
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } catch (IOException e) {
                        System.err.println("Node " + nodeId + " error connecting to Node " + 
                                         targetId + ": " + e.getMessage());
                        break;
                    }
                }
            });
        }
    }
    
    @Override
    public void start() {
        try {
            // Start server socket to accept connections
            serverSocket = new ServerSocket(port);
            System.out.println("Node " + nodeId + " listening on port " + port + " [Virtual]");
            
            // Start accepting connections in background (virtual)
            startAcceptingConnections();
            
            // Connect to other nodes (virtual)
            connectToOtherNodes();
            
            // Give connections time to establish
            Thread.sleep(2000);
            
            // Create and start virtual worker threads
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
            System.out.println("\n=== Node " + nodeId + " Statistics (Virtual Threads) ===");
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
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java NodeVirtual <nodeId>");
            System.err.println("nodeId must be 1, 2, 3, 4, or 5");
            System.exit(1);
        }
        
        int nodeId = Integer.parseInt(args[0]);
        if (nodeId < 1 || nodeId > 5) {
            System.err.println("nodeId must be between 1 and 5");
            System.exit(1);
        }
        
        NodeVirtual node = new NodeVirtual(nodeId);
        node.start();
    }
}

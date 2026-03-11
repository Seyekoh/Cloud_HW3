
# CS4225 Homework 3 - Distributed Event Monitoring System

## Project Overview
A distributed event monitoring system with 5 nodes communicating via sockets, using Lamport logical clocks for event ordering. Implements and compares multiple concurrency optimizations.


## Author
- James Bridges

## Requirements

- Java 21+ (for virtual threads support)
- Bash shell
- Linux/Unix environment (Azure VM)
  
## Project Structure

.
├── Counter.java # Basic counter (long field)
├── LamportClock.java # Lamport logical clock
├── LogParser.java # Manual log parsing utility
├── Metrics.java # Performance metrics class
├── Node.java # Baseline implementation
├── NodeExecutor.java # ExecutorService optimization
├── NodePadded.java # Padded Counter optimization
├── NodeVirtual.java # Virtual Threads optimization
├── OptimizationRunner.java # Automated experiment runner
├── PaddedCounter.java # Cache-aligned counter
├── performance_results.csv # Generated performance data
├── run-all.sh # Master script (optional)
├── start-baseline.sh # Run baseline configuration
├── start-executor.sh # Run ExecutorService configuration
├── start-padded.sh # Run Padded Counter configuration
├── start-virtual.sh # Run Virtual Threads configuration
├── stop.sh # Clean shutdown all nodes
├── structure.txt # Project structure documentation
├── node.log # Baseline log files
├── node-executor.log # ExecutorService log files
├── node-padded.log # Padded Counter log files
└── node-virtual.log # Virtual Threads log files

## Implemented Optimizations

| Optimization | Class | Description |
|-------------|-------|-------------|
| **Baseline** | `Node.java` | Raw platform threads, simple Counter |
| **ExecutorService** | `NodeExecutor.java` | Thread pooling for workers and senders |
| **Padded Counter** | `NodePadded.java` | Cache line alignment (64 bytes) to prevent false sharing |
| **Virtual Threads** | `NodeVirtual.java` | Java 21+ lightweight threads |
  
## How to Run Individual Configurations

Each optimization can be run independently using its own start script:

```bash
# Make all scripts executable
chmod +x *.sh

# Run baseline configuration
./start-baseline.sh

# In another terminal, monitor logs in real-time
tail -f node*.log

# When complete, stop all nodes
./stop.sh
```

## Running Other Configurations

```bash

# ExecutorService
./start-executor.sh
tail -f node*-executor.log
./stop.sh

# Padded Counter
./start-padded.sh
tail -f node*-padded.log
./stop.sh

# Virtual Threads
./start-virtual.sh
tail -f node*-virtual.log
./stop.sh
```

# Run automated experiments

javac OptimizationRunner.java # If not already compiled
java OptimizationRunner

The runner tests each optimization with multiple thread counts (2, 4, 8, 16) and event counts (100, 500, 1000) to provide comprehensive performance data.

## Configuration

Nodes: 5 (IDs 1-5)

Ports: 4225-4229 (incrementing by node ID)

Threads per node: 4

Events per thread: 100

Random seed: 4225 (ensures reproducibility)

Message probability: 50% (random.nextBoolean())

## Log Files

Configuration	Log File Pattern
Baseline	    node{id}.log
Executor	    node{id}-executor.log
Padded	        node{id}-padded.log
Virtual	        node{id}-virtual.log

Each log file contains:

    Node initialization and connection status

    Local event processing

    Sent/received message events

    Final statistics (execution time, event counts, counter values)

Sample Results:

Based on experimental runs (from performance_results.csv):
Optimization	Avg Time (ms)	Avg Events	Throughput (ev/s)	Improvement
Baseline	    10,680	        606	        56.8	            -
Executor	    10,746	        606	        56.4	            -0.7%
Padded	        10,817	        607	        56.1	            -1.2%
Virtual	        9,578	        607	        63.4	            +11.6%

Node-Level Detail (Throughput in events/sec)
Node	Baseline	Executor	Padded	Virtual
1	    52.1	    54.1	    54.6	63.8
2	    50.5	    48.9	    49.4	60.9
3	    57.0	    57.5	    54.4	62.3
4	    58.2	    56.2	    56.8	61.4
5	    67.7	    66.8	    67.6	68.4

# Manual Log Parsing

If you prefer to parse existing log files manually (or after running individual scripts):

# After running any configurations, parse all logs
java LogParser

Baseline: node{id}.log

Padded Counter: node{id}-padded.log

Virtual Threads: node{id}-virtual.log

## Sample Results

Baseline Average: 608 events, 10834 ms, 56.1 events/sec
Padded Average: 610 events, 10849 ms, 56.2 events/sec
Virtual Average: 608 events, 9614 ms, 63.3 events/sec

## Key Findings

1. Virtual threads provide the greatest improvement (~12%) by efficiently handling I/O blocking operations

2. ExecutorService shows slight degradation due to overhead of thread pooling with fixed thread counts

3. Padded counters actually hurt performance when false sharing isn't the primary bottleneck

4. Node 5 consistently outperforms others due to receiving more messages (stochastic distribution)

## Troubleshooting

# Port already in use
./stop.sh
lsof -i :4225-4229

# Force kill if necessary
lsof -ti :4225-4229 | xargs kill -9

# Compilation errors
# Clean and recompile
rm -f *.class
javac *.java

# Check Java version (must be 21+)
java -version

# Processes Not Terminating
# Force kill all Java Node processes
pkill -f "java Node"
pkill -9 -f "java Node" 2>/dev/null
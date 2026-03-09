
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
├── Metrics.java # Performance metrics class
├── Node.java # Baseline implementation
├── NodeExecutor.java # ExecutorService optimization
├── NodePadded.java # Padded Counter optimization
├── NodeVirtual.java # Virtual Threads optimization
├── OptimizationRunner.java # Automated experiment runner
├── PaddedCounter.java # Cache-aligned counter
├── start-baseline.sh # Run baseline configuration
├── start-padded.sh # Run Padded Counter configuration
├── start-virtual.sh # Run Virtual Threads configuration
├── stop.sh # Clean shutdown all nodes
└── README.md # This file## Implemented Optimizations

- **Baseline (Node.java)** - Raw threads, simple Counter
- **ExecutorService (NodeExecutor.java)** - Thread pooling
- **Padded Counter (NodePadded.java)** - Cache line alignment (64 bytes)
- **Virtual Threads (NodeVirtual.java)** - Java 21+ lightweight threads
  
## How to Run

```bash
# Make scripts executable
chmod +x *.sh

# Run baseline
./start-baseline.sh
# In another terminal, monitor logs
tail -f node*.log

# Run padded counter
./start-padded.sh
tail -f node*-padded.log

# Run virtual threads
./start-virtual.sh
tail -f node*-virtual.log

# Stop all nodes
./stop.sh
```

# Run automated experiments
java OptimizationRunner


## Configuration

5 nodes (IDs 1-5) on ports 4225-4229

4 threads per node, 100 events per thread

Random seed: 4225, 50% message probability

## Log Files

Baseline: node{id}.log

Padded Counter: node{id}-padded.log

Virtual Threads: node{id}-virtual.log

## Sample Results

Baseline Average: 608 events, 10834 ms, 56.1 events/sec
Padded Average: 610 events, 10849 ms, 56.2 events/sec
Virtual Average: 608 events, 9614 ms, 63.3 events/sec

## Key Findings

Virtual threads show ~12% performance improvement over baseline.

## Troubleshooting

# Port already in use
./stop.sh
lsof -i :4225-4229

# Compilation errors
rm *.class && javac *.java

# Check Java version (must be 21+)
java -version

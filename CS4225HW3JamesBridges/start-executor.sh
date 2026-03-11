#!/usr/bin/env bash

# Compile all Java Files
echo "Compiling Java files..."
javac *.java

# Clean up old log files
rm -f node*-executor.log

# Start all 5 nodes with ExecutorService
for id in {1..5}; do
	echo "Starting Node $id with ExecutorService..."
	java NodeExecutor $id > "node${id}-executor.log" 2>&1 &

	# Small delay to ensure nodes start in order
	sleep 0.5
done

echo "All executor nodes started. Waiting for execution..."
echo "To monitor logs in real-time, run:"
echo "tail -f node*-executor.log"
echo ""
echo "Run ./stop.sh to stop all nodes"

# Wait for all background processes
wait

#!/bin/bash

# Compile all Java Files first
echo "Compiling Java Files..."
javac *.java

# Clean up old log files
rm -f node*.log

# Start all 5 nodes in the background
for id in {1..5}; do
	echo "Starting Node $id..."
	java Node $id > "node$id.log" 2>&1 &

	# Small delay to ensure nodes start in order
	sleep 0.5
done

echo "All nodes startd. Waiting for execution..."
echo "Press Ctrl+C to stop all nodes"
echo ""
echo "To monitor logs in real-time, run:"
echo "tail -f node*.log"

# Wait for all background processes
wait

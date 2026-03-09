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
echo ""
echo "To monitor in real-time, navigate to current dir in new shell then enter:
	tail -f node*.log"
echo "Run ./stop.sh to stop all nodes"

# Wait for all background processes
wait

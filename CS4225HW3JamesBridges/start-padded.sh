#!/usr/bin/env bash

# Compile all Java files firs
echo "Compiling Java files..."
javac *.java

# Clean up old log files
rm -f node*-padded.log

# Start all 5 nodes with PaddedCounter
for id in {1..5}; do
	echo "Starting Node $is with PaddedCounter..."
	java NodePadded $id > "node${id}-padded.log" 2>&1 &

	# Small delay to ensure nodes start in order
	sleep 0.5
done

echo "All padded nodes started. Waiting for execution..."
echo "To monitor logs in real-time, run:"
echo "tail -f node*-padded.log"

# Wait for all background processes
wait

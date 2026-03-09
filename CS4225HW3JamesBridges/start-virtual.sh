#!/usr/bin/env bash

# Compile all Java files with release 21 for virtual threads support
echo "Compiling Java files with Java 21+ support..."
javac *.java

# Clean up old log files
rm -f node*-virtual.log

# Start all 5 nodes with Virtual Threads
for id in {1..5}; do
    echo "Starting Node $id with Virtual Threads..."
    java NodeVirtual $id > "node${id}-virtual.log" 2>&1 &
    
    # Small delay to ensure nodes start in order
    sleep 0.5
done

echo "All virtual thread nodes started. Waiting for execution..."
echo "To monitor logs in real-time, run:"
echo "tail -f node*-virtual.log"

# Wait for all background processes
wait

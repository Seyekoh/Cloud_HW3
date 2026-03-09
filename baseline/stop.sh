#!/bin/bash

echo "Stopping all Java Node processes..."
pkill -f "java Node"
sleep 1
# Force kill any remaining processes
pkill -9 -f "java Node" 2>/dev/null
echo "Checking if ports are freed..."
for port in 4225 4226 4227 4228 4229; do
	if lsof -i :$port > /dev/null 2>&1; then
		echo "Port $port still in use, killing specific process..."
		lsof -ti :$port | xargs kill -9 2>/dev/null
	fi
done
echo "Done!"

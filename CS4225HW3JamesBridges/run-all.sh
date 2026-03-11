#!/usr/bin/env bash

set -e # Exit on error

echo "==========================================="
echo "CS4225 Homework 3 - Running All Experiments"
echo "==========================================="

# Make sure all scripts are executable
chmod +x start-*.sh stop.sh

# Compile everything once
echo -e "|n[1/6] Compiling Java Files..."
javac *.java

# Run baseling
./start-baseline.sh
./stop.sh

# Run executor
./start-executor.sh
./stop.sh

# Run padded
./start-padded.sh
./stop.sh

# Run virtual
./start-virtual.sh
./stop.sh

# Parse all logs and generate CSV
echo -e "\nGenerating performance report..."
java LogParser

echo -e "\nAll done! Results saved to performance_results.csv"
echo "=============================================="

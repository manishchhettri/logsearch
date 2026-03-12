#!/bin/bash

# Log Search Shutdown Script
# Stops the running Log Search application

echo "Stopping Log Search Application..."

# Find the process ID of the running application
PID=$(ps aux | grep "log-search-1.0.0.jar" | grep -v grep | awk '{print $2}')

if [ -z "$PID" ]; then
    echo "Log Search is not running."
    exit 0
fi

# Kill the process
kill $PID 2>/dev/null

# Wait for process to stop (max 10 seconds)
for i in {1..10}; do
    if ! ps -p $PID > /dev/null 2>&1; then
        echo "Log Search stopped successfully."
        exit 0
    fi
    sleep 1
done

# If still running, force kill
if ps -p $PID > /dev/null 2>&1; then
    echo "Process still running, forcing shutdown..."
    kill -9 $PID 2>/dev/null
    sleep 1

    if ! ps -p $PID > /dev/null 2>&1; then
        echo "Log Search forcefully stopped."
        exit 0
    else
        echo "Error: Failed to stop Log Search (PID: $PID)"
        exit 1
    fi
fi

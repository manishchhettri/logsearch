#!/bin/bash

# Log Search Startup Script
# This script makes it easy to run the application with external configuration

JAR_FILE="target/log-search-1.0.0.jar"
CONFIG_FILE="config/application.yml"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please run: mvn clean package"
    exit 1
fi

# Check if external config exists
if [ -f "$CONFIG_FILE" ]; then
    echo "Using external configuration: $CONFIG_FILE"
    CONFIG_ARG="--spring.config.location=file:$CONFIG_FILE"
else
    echo "No external config found, using defaults from JAR"
    CONFIG_ARG=""
fi

# Pass through any command line arguments
echo "Starting Log Search Application..."
echo ""

java -jar "$JAR_FILE" $CONFIG_ARG "$@"

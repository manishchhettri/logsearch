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

# Parse JVM heap settings from config file (if exists)
HEAP_MIN="2g"
HEAP_MAX="4g"
JVM_EXTRA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

if [ -f "$CONFIG_FILE" ]; then
    # Extract heap-min from YAML - handle ${VAR:default} syntax
    HEAP_MIN_LINE=$(grep -E '^\s*heap-min:' "$CONFIG_FILE" | head -1)
    if [ ! -z "$HEAP_MIN_LINE" ]; then
        # Extract default value from ${VAR:default} syntax
        if echo "$HEAP_MIN_LINE" | grep -q '\${'; then
            HEAP_MIN=$(echo "$HEAP_MIN_LINE" | sed -E 's/.*\$\{[^:]+:([^}]+)\}.*/\1/')
        else
            # Direct value (no ${} syntax)
            HEAP_MIN=$(echo "$HEAP_MIN_LINE" | sed -E 's/.*heap-min:\s*([^#]+).*/\1/' | tr -d ' "'"'"'')
        fi
    fi

    # Extract heap-max from YAML
    HEAP_MAX_LINE=$(grep -E '^\s*heap-max:' "$CONFIG_FILE" | head -1)
    if [ ! -z "$HEAP_MAX_LINE" ]; then
        if echo "$HEAP_MAX_LINE" | grep -q '\${'; then
            HEAP_MAX=$(echo "$HEAP_MAX_LINE" | sed -E 's/.*\$\{[^:]+:([^}]+)\}.*/\1/')
        else
            HEAP_MAX=$(echo "$HEAP_MAX_LINE" | sed -E 's/.*heap-max:\s*([^#]+).*/\1/' | tr -d ' "'"'"'')
        fi
    fi

    # Extract extra JVM options from YAML
    JVM_EXTRA_LINE=$(grep -E '^\s*extra-opts:' "$CONFIG_FILE" | head -1)
    if [ ! -z "$JVM_EXTRA_LINE" ]; then
        if echo "$JVM_EXTRA_LINE" | grep -q '\${'; then
            JVM_EXTRA_OPTS=$(echo "$JVM_EXTRA_LINE" | sed -E 's/.*\$\{[^:]+:([^}]+)\}.*/\1/')
        else
            JVM_EXTRA_OPTS=$(echo "$JVM_EXTRA_LINE" | sed -E 's/.*extra-opts:\s*([^#]+).*/\1/' | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')
        fi
    fi
fi

# Allow environment variables to override config file
if [ ! -z "$JVM_HEAP_MIN" ]; then
    HEAP_MIN="$JVM_HEAP_MIN"
fi
if [ ! -z "$JVM_HEAP_MAX" ]; then
    HEAP_MAX="$JVM_HEAP_MAX"
fi

# Build JVM arguments
JVM_ARGS="-Xms$HEAP_MIN -Xmx$HEAP_MAX $JVM_EXTRA_OPTS"

# Pass through any command line arguments
echo "Starting Log Search Application..."
echo "JVM Settings: Min Heap=$HEAP_MIN, Max Heap=$HEAP_MAX"
echo "Extra JVM Options: $JVM_EXTRA_OPTS"
echo ""

java $JVM_ARGS -jar "$JAR_FILE" $CONFIG_ARG "$@"

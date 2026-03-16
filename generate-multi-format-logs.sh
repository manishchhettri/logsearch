#!/bin/bash

# Multi-Format Log Generator
# Generates logs in various formats to test adaptive pattern detection
# Usage: ./generate-multi-format-logs.sh [num_entries_per_format]

NUM_ENTRIES=${1:-50}
OUTPUT_DIR="./logs/test-formats"

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "Generating multi-format test logs..."
echo "Output directory: $OUTPUT_DIR"
echo "Entries per format: $NUM_ENTRIES"
echo ""

# Sample data
USERS=("admin" "john.doe" "jane.smith" "bob.wilson" "alice.brown" "system")
LEVELS_UPPER=("ERROR" "WARN" "INFO" "DEBUG" "TRACE")
LEVELS_MIXED=("Error" "Warn" "Info" "Debug" "Trace")
LOGGERS=("com.app.Service" "org.framework.Core" "database.Connection" "security.Auth" "Trace")
MESSAGES=(
    "Processing request for user session ABC123"
    "Database connection established successfully"
    "Authentication failed for invalid credentials"
    "Cache hit ratio: 85.3%"
    "Processing workflow step 3 of 10"
    "File uploaded: document-12345.pdf (2.3 MB)"
    "Payment processed: transaction-ID-67890"
    "Email notification sent to user@example.com"
    "Background job completed in 1.23 seconds"
    "Configuration reloaded from properties file"
)

# Get random element from array
get_random() {
    local arr=("$@")
    echo "${arr[$RANDOM % ${#arr[@]}]}"
}

# Generate timestamp in various formats
get_timestamp_weblogic() {
    date -v-${RANDOM:0:2}H "+%d %b %Y %H:%M:%S,$((RANDOM % 1000))"
}

get_timestamp_log4j() {
    date -v-${RANDOM:0:2}H "+%Y-%m-%d %H:%M:%S,$((RANDOM % 1000))"
}

get_timestamp_iso() {
    date -v-${RANDOM:0:2}H "+%Y-%m-%dT%H:%M:%S.%3N+13:00"
}

# ============================================================================
# FORMAT 1: Standard WebLogic (with user:xxx and empty brackets)
# ============================================================================
echo "Generating Format 1: Standard WebLogic..."
OUTPUT_FILE="$OUTPUT_DIR/format1-weblogic-standard.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_weblogic)
    thread="[[ACTIVE] ExecuteThread: '$((RANDOM % 25))' for queue: 'weblogic.kernel.Default (self-tuning)']"
    level=$(get_random "${LEVELS_UPPER[@]}")
    logger=$(get_random "${LOGGERS[@]}")
    user=$(get_random "${USERS[@]}")
    message=$(get_random "${MESSAGES[@]}")

    echo "[$timestamp] $thread [$level ] [$logger] [] [user:$user] - $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 2: WebLogic Variant (your format - no user: prefix, no empty [])
# ============================================================================
echo "Generating Format 2: WebLogic Variant (custom)..."
OUTPUT_FILE="$OUTPUT_DIR/format2-weblogic-variant.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_weblogic)
    thread="[[STANDBY] ExecuteThread: '$((RANDOM % 25))' for queue: 'weblogic.kernel.Default (self-tuning)']"
    level=$(get_random "${LEVELS_UPPER[@]}")
    logger=$(get_random "${LOGGERS[@]}")
    message=$(get_random "${MESSAGES[@]}")

    # This format: [timestamp] [thread] [level] [logger] message (no user field)
    echo "[$timestamp] $thread [$level] [$logger] $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 3: Log4j/Tomcat Format
# ============================================================================
echo "Generating Format 3: Log4j/Tomcat..."
OUTPUT_FILE="$OUTPUT_DIR/format3-log4j.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_log4j)
    thread="Thread-$((RANDOM % 10))"
    level=$(get_random "${LEVELS_UPPER[@]}")
    logger=$(get_random "${LOGGERS[@]}")
    message=$(get_random "${MESSAGES[@]}")

    echo "$timestamp [$thread] $level $logger - $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 4: Mixed Case Log Levels
# ============================================================================
echo "Generating Format 4: Mixed Case Levels..."
OUTPUT_FILE="$OUTPUT_DIR/format4-mixed-case-levels.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_weblogic)
    thread="[[ACTIVE] Worker-$((RANDOM % 10))]"
    level=$(get_random "${LEVELS_MIXED[@]}")  # Mixed case: Error, Warn, Info
    logger=$(get_random "${LOGGERS[@]}")
    user=$(get_random "${USERS[@]}")
    message=$(get_random "${MESSAGES[@]}")

    echo "[$timestamp] $thread [$level] [$logger] [user:$user] - $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 5: Simple Format (timestamp, user, message only)
# ============================================================================
echo "Generating Format 5: Simple Format..."
OUTPUT_FILE="$OUTPUT_DIR/format5-simple.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_iso)
    user=$(get_random "${USERS[@]}")
    message=$(get_random "${MESSAGES[@]}")

    echo "[$timestamp] [$user] $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 6: Custom Format with Different Field Order
# ============================================================================
echo "Generating Format 6: Custom Field Order..."
OUTPUT_FILE="$OUTPUT_DIR/format6-custom-order.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_weblogic)
    level=$(get_random "${LEVELS_UPPER[@]}")
    thread="[AsyncTask-$((RANDOM % 20))]"
    logger=$(get_random "${LOGGERS[@]}")
    user=$(get_random "${USERS[@]}")
    message=$(get_random "${MESSAGES[@]}")

    # Different order: [level] [timestamp] [user] [thread] [logger] message
    echo "[$level] [$timestamp] [$user] $thread [$logger] - $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 7: Minimal Format (timestamp and level only)
# ============================================================================
echo "Generating Format 7: Minimal Format..."
OUTPUT_FILE="$OUTPUT_DIR/format7-minimal.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_log4j)
    level=$(get_random "${LEVELS_UPPER[@]}")
    message=$(get_random "${MESSAGES[@]}")

    echo "[$timestamp] [$level] $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 8: WebLogic with userid instead of user
# ============================================================================
echo "Generating Format 8: WebLogic with userid..."
OUTPUT_FILE="$OUTPUT_DIR/format8-userid-variant.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_weblogic)
    thread="[[ACTIVE] ExecuteThread: '$((RANDOM % 25))' for queue: 'weblogic.kernel.Default (self-tuning)']"
    level=$(get_random "${LEVELS_UPPER[@]}")
    logger=$(get_random "${LOGGERS[@]}")
    userid="user$((RANDOM % 10000))"
    message=$(get_random "${MESSAGES[@]}")

    echo "[$timestamp] $thread [$level] [$logger] [userid:$userid] - $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 9: Complex Nested Brackets
# ============================================================================
echo "Generating Format 9: Complex Nested Brackets..."
OUTPUT_FILE="$OUTPUT_DIR/format9-nested-brackets.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(get_timestamp_weblogic)
    thread="[[POOL-A] [Worker-$((RANDOM % 10))]]"
    level=$(get_random "${LEVELS_UPPER[@]}")
    logger="[pkg.subpkg.$(get_random "${LOGGERS[@]}")]"
    user=$(get_random "${USERS[@]}")
    message=$(get_random "${MESSAGES[@]}")

    echo "[$timestamp] $thread [$level] $logger [user:$user] $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# FORMAT 10: ISO-8601 with Level
# ============================================================================
echo "Generating Format 10: ISO-8601 with Level..."
OUTPUT_FILE="$OUTPUT_DIR/format10-iso8601.log"
> "$OUTPUT_FILE"

for i in $(seq 1 $NUM_ENTRIES); do
    timestamp=$(date -v-${RANDOM:0:2}H "+%Y-%m-%dT%H:%M:%S.%3N+13:00" | sed 's/%3N/'$((RANDOM % 1000))'/')
    level=$(get_random "${LEVELS_UPPER[@]}")
    message=$(get_random "${MESSAGES[@]}")

    echo "$timestamp $level $message" >> "$OUTPUT_FILE"
done

echo "  Created: $OUTPUT_FILE ($NUM_ENTRIES entries)"

# ============================================================================
# Summary
# ============================================================================
echo ""
echo "================================================================"
echo "Multi-format log generation complete!"
echo "================================================================"
echo ""
echo "Generated formats:"
echo "  1. Standard WebLogic       - [timestamp] [thread] [level] [logger] [] [user:xxx] - message"
echo "  2. WebLogic Variant        - [timestamp] [thread] [level] [logger] message"
echo "  3. Log4j/Tomcat           - timestamp [thread] level logger - message"
echo "  4. Mixed Case Levels       - [timestamp] [thread] [Error/Warn] [logger] [user:xxx] - message"
echo "  5. Simple Format           - [timestamp] [user] message"
echo "  6. Custom Field Order      - [level] [timestamp] [user] [thread] [logger] - message"
echo "  7. Minimal Format          - [timestamp] [level] message"
echo "  8. userid Variant          - [timestamp] [thread] [level] [logger] [userid:xxx] - message"
echo "  9. Nested Brackets         - [timestamp] [[nested]] [level] [logger] [user] message"
echo " 10. ISO-8601                - ISO-timestamp level message"
echo ""
echo "Total files: 10"
echo "Entries per file: $NUM_ENTRIES"
echo ""
echo "Test adaptive pattern detection by indexing these files!"

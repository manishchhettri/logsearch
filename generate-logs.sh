#!/bin/bash

# WebLogic-Style Log Generator Script
# Generates logs in WebLogic format with thread, level, logger, user fields
# Usage: ./generate-logs.sh [num_days] [logs_per_day]

NUM_DAYS=${1:-7}
LOGS_PER_DAY=${2:-300}
OUTPUT_DIR="./logs"

# Create logs directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Sample users
USERS=("curamsystemuser" "xyz" "1234" "5678" "admin" "john.doe" "jane.smith" "bob.wilson" "alice.brown" "charlie.davis")

# Sample thread names (WebLogic ExecuteThreads)
THREADS=(
    "[[ACTIVE] ExecuteThread: '0' for queue: 'weblogic.kernel.Default (self-tuning)']"
    "[[ACTIVE] ExecuteThread: '1' for queue: 'weblogic.kernel.Default (self-tuning)']"
    "[[ACTIVE] ExecuteThread: '15' for queue: 'weblogic.kernel.Default (self-tuning)']"
    "[[ACTIVE] ExecuteThread: '22' for queue: 'weblogic.kernel.Default (self-tuning)']"
    "[[STANDBY] ExecuteThread: '0' for queue: 'weblogic.kernel.Default (self-tuning)']"
    "[[STANDBY] ExecuteThread: '1' for queue: 'weblogic.kernel.Default (self-tuning)']"
    "[[STANDBY] ExecuteThread: '16' for queue: 'weblogic.kernel.Default (self-tuning)']"
)

# Log levels
LEVELS=("INFO " "WARN " "ERROR" "DEBUG" "TRACE")

# Sample loggers (class/component names)
LOGGERS=(
    "curam.abv.filters.uditLoggingFilter"
    "curam.abv.investigationscollections.sl.impl.ManageDebtWorkflow"
    "curam.core.impl.WorkflowManager"
    "curam.util.persistence.impl.DatabaseConnection"
    "curam.security.impl.AuthenticationService"
    "Trace"
    "oracle.jdbc.driver.OracleDriver"
    "org.springframework.web.servlet.DispatcherServlet"
    "com.ibm.ws.webcontainer.servlet.ServletWrapper"
)

# Sample messages with realistic WebLogic/Curam content
MESSAGES=(
    # Normal operations
    "/Curam/servlet-unauth/DBtoJMS 127.0.0.1 - password1234==userid5678"
    "/Curam/WorkspaceServlet 192.168.1.100 - sessionID=ABC123XYZ"
    "Processing workflow for caseID=12345, caseOwner=john.doe"
    "Database query executed successfully, returned 523 rows"
    "User authentication successful for userid: xyz"
    "Starting batch job: PROCESS_DAILY_CLAIMS"
    "Batch job completed: processed 5000 records in 45 seconds"
    "Cache refresh completed for entity: PERSON"
    "Session created for user: alice.brown, sessionID: SES-789456123"
    "Transaction committed successfully, txnID: TXN-2026031200001"

    # Warnings
    "RuleObjectPropagation:ERR_RULE_OBJECT_PROPAGATORS_EVIDENCE_TYPE_INVALID: Error in configuration for rule object propagator type 'Active succession set': no evidence name found in evidence map for type code 'DET0026117'."
    "Session timeout warning: user xyz idle for 25 minutes"
    "Database connection pool nearing capacity: 85% utilized"
    "Slow query detected: execution time 5.2 seconds for query ID QRY-123"
    "Deprecated API usage detected in workflow WF-456"

    # Errors
    "ERROR: NullPointerException in curam.core.impl.WorkflowManager.executeWorkflow()"
    "ERROR: Database connection failed - java.sql.SQLException: Connection refused"
    "ERROR: OutOfMemoryError: Java heap space - GC overhead limit exceeded"
    "ERROR: Customer ID cannot be negative: customerId=-12345"
    "ERROR: Failed to load configuration file: /opt/curam/config/application.properties"
    "ERROR: Transaction rolled back due to constraint violation: FK_PERSON_ADDRESS"
    "ERROR: Authentication failed for user: invalid_user - incorrect password"
    "ERROR: Deadlock detected - MySQLTransactionRollbackException"
)

# Multi-line messages (these will have continuation lines)
MULTILINE_MESSAGES=(
    "start issueIRL key = curam.abv.investigationscollections.sl.struct.CreateDebtTasksAndEventsKey@542fdba6"
    "Exception caught during workflow execution"
    "Stack trace for debugging workflow issue"
    "Failed to process evidence document"
)

# Multi-line continuations
CONTINUATIONS=(
    " caseID=-2345"
    " caseOwner=john.doe"
    " workflowID=WF-789456"
    " serialVersionUID=137040931"
    " evidenceType=DET0026117"
    " documentID=DOC-123456"
    "   at curam.core.impl.WorkflowManager.execute(WorkflowManager.java:234)"
    "   at curam.core.impl.WorkflowEngine.process(WorkflowEngine.java:567)"
    "   at curam.util.transaction.impl.TransactionManager.commit(TransactionManager.java:123)"
    "   Caused by: java.lang.NullPointerException: Cannot invoke method on null object"
    "   at curam.abv.investigationscollections.sl.impl.EvidenceProcessor.validate(EvidenceProcessor.java:89)"
)

echo "Generating $NUM_DAYS days of WebLogic-style logs..."
echo "Each day will have approximately $LOGS_PER_DAY log entries"
echo

# Get the current date
BASE_DATE=$(date +%Y%m%d)

for ((day=0; day<NUM_DAYS; day++)); do
    # Calculate the date for this log file (going backwards from today)
    LOG_DATE=$(date -v-${day}d +"%Y%m%d" 2>/dev/null || date -d "-${day} days" +"%Y%m%d")
    LOG_DATE_DISPLAY=$(date -v-${day}d +"%d %b %Y" 2>/dev/null || date -d "-${day} days" +"%d %b %Y")

    FILENAME="$OUTPUT_DIR/server-$LOG_DATE.log"

    echo "Generating $FILENAME with $LOGS_PER_DAY entries..."

    # Clear the file if it exists
    > "$FILENAME"

    # Generate log entries for this day
    for ((i=0; i<LOGS_PER_DAY; i++)); do
        # Random hour, minute, second
        HOUR=$((RANDOM % 24))
        MINUTE=$((RANDOM % 60))
        SECOND=$((RANDOM % 60))
        MILLIS=$((RANDOM % 1000))

        # Format timestamp: 09 Mar 2026 18:48:36,378
        TIMESTAMP=$(printf "%s %02d:%02d:%02d,%03d" "$LOG_DATE_DISPLAY" $HOUR $MINUTE $SECOND $MILLIS)

        # Random selections
        USER="${USERS[$RANDOM % ${#USERS[@]}]}"
        THREAD="${THREADS[$RANDOM % ${#THREADS[@]}]}"
        LOGGER="${LOGGERS[$RANDOM % ${#LOGGERS[@]}]}"

        # 70% INFO, 20% WARN, 10% ERROR
        RAND=$((RANDOM % 100))
        if [ $RAND -lt 70 ]; then
            LEVEL="INFO "
        elif [ $RAND -lt 90 ]; then
            LEVEL="WARN "
        else
            LEVEL="ERROR"
        fi

        # Select message
        MESSAGE="${MESSAGES[$RANDOM % ${#MESSAGES[@]}]}"

        # Format: [timestamp] [thread] [level] [logger] [] [user:xxx] - message
        echo "[$TIMESTAMP] $THREAD [$LEVEL] [$LOGGER] [] [user:$USER] - $MESSAGE" >> "$FILENAME"

        # 10% chance of multi-line log entry
        if [ $((RANDOM % 10)) -eq 0 ]; then
            MULTILINE_MSG="${MULTILINE_MESSAGES[$RANDOM % ${#MULTILINE_MESSAGES[@]}]}"

            # New timestamp for multi-line entry
            SECOND=$((SECOND + 1))
            if [ $SECOND -ge 60 ]; then
                SECOND=0
                MINUTE=$((MINUTE + 1))
            fi
            TIMESTAMP=$(printf "%s %02d:%02d:%02d,%03d" "$LOG_DATE_DISPLAY" $HOUR $MINUTE $SECOND $MILLIS)

            # Main log line
            echo "[$TIMESTAMP] $THREAD [$LEVEL] [$LOGGER] [] [user:$USER] - $MULTILINE_MSG" >> "$FILENAME"

            # Add 2-4 continuation lines
            NUM_CONTINUATIONS=$((2 + RANDOM % 3))
            for ((j=0; j<NUM_CONTINUATIONS; j++)); do
                CONT="${CONTINUATIONS[$RANDOM % ${#CONTINUATIONS[@]}]}"
                echo "$CONT" >> "$FILENAME"
            done
        fi
    done

    echo "  ✓ Generated $FILENAME ($(wc -l < "$FILENAME") total lines including continuations)"
done

echo
echo "✓ Log generation complete!"
echo "  Location: $OUTPUT_DIR/"
echo "  Files: $(ls -1 $OUTPUT_DIR/server-*.log | wc -l) log files"
echo "  Total size: $(du -sh $OUTPUT_DIR | cut -f1)"
echo
echo "To index these logs:"
echo "  ./start.sh start --logs-dir=$OUTPUT_DIR"

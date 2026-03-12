#!/bin/bash

# Log Generator Script
# Usage: ./generate-logs.sh [num_days] [logs_per_day]

NUM_DAYS=${1:-7}
LOGS_PER_DAY=${2:-1000}
OUTPUT_DIR="./logs"

# Create logs directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Sample users
USERS=("john.doe" "jane.smith" "bob.wilson" "alice.brown" "charlie.davis" "emma.johnson" "michael.wang" "sarah.chen" "system" "admin")

# Sample log messages (including realistic Java errors and exceptions)
MESSAGES=(
    # Normal operations
    "Application started successfully"
    "Database connection established"
    "User login successful from IP 192.168.1.100"
    "Processing batch job for customer data"
    "Generated monthly sales report"
    "Downloaded file: invoice_12345.pdf"
    "Batch job completed successfully - processed 5000 records"
    "Searching for transactions in date range"
    "Found 1523 matching transactions"
    "Starting scheduled cleanup task"
    "Cleaned up 342 temporary files"
    "Scheduled cleanup task completed"
    "Uploading 25 product images"
    "Upload completed successfully"
    "Updated customer profile for customer ID: 12345"
    "Sending notification email to customer@example.com"
    "Starting hourly metrics collection"
    "Collected metrics: CPU 45%, Memory 62%, Disk 78%"
    "Metrics collection completed"
    "User logout - session duration: 5h 30m"
    "Starting nightly backup process"
    "Backing up database to /backups/db_20260312.sql"
    "Database backup completed - size: 2.3 GB"
    "Backing up files to cloud storage"
    "File backup completed - uploaded 458 files"
    "Backup process completed successfully"
    "Starting maintenance tasks"
    "Optimizing database indexes"
    "Database optimization completed"
    "Cleaning up old log files older than 90 days"
    "Deleted 15 old log files - freed 1.2 GB disk space"
    "Maintenance tasks completed"
    "All active users: 42"
    "System running normally - uptime: 23h 15m"
    "End of day summary: 128 users logged in, 6248 transactions processed"
    "Starting data export job for client XYZ Corp"
    "Export job processing - 75% complete"
    "Data export completed - file size: 850 MB"
    "Starting health check"
    "Health check: Database - OK"
    "Health check: File Storage - OK"
    "Health check: External API - OK"
    "Health check: Email Service - OK"
    "Health check completed - all systems operational"
    "Processing payment transaction for order #98765"
    "Payment processed successfully - amount: \$129.99"
    "Sending order confirmation email"
    "Cache cleared - 1523 entries removed"
    "API request rate: 450 req/sec"
    "Response time p95: 125ms"

    # Warnings
    "WARNING: Connection pool exhausted - all connections in use"
    "WARNING: High memory usage detected - 85% utilized"
    "WARNING: Slow query detected - execution time: 5.2 seconds"
    "WARNING: Disk space running low - 90% utilized on /data"
    "WARNING: Thread pool queue size exceeding threshold: 500 tasks"
    "WARNING: Cache hit ratio below threshold: 45%"
    "WARNING: API rate limit approaching - 950/1000 requests"

    # Database Errors
    "ERROR: Database connection timeout after 30 seconds - java.sql.SQLTimeoutException"
    "ERROR: Deadlock detected - com.mysql.jdbc.exceptions.jdbc4.MySQLTransactionRollbackException: Deadlock found when trying to get lock"
    "ERROR: Connection refused to database server at jdbc:postgresql://localhost:5432/appdb"
    "ERROR: Too many connections - java.sql.SQLException: Data source rejected establishment of connection, message from server: Too many connections"
    "ERROR: Table not found - java.sql.SQLException: Table 'appdb.orders' doesn't exist"
    "ERROR: Constraint violation - java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '12345' for key 'PRIMARY'"

    # NullPointerException
    "ERROR: NullPointerException in OrderService.processOrder() at line 245 - java.lang.NullPointerException"
    "ERROR: Cannot invoke String.length() because customerName is null - java.lang.NullPointerException at com.company.service.CustomerService.validateName(CustomerService.java:89)"

    # OutOfMemoryError
    "ERROR: Java heap space exhausted - java.lang.OutOfMemoryError: Java heap space at java.util.ArrayList.grow(ArrayList.java:237)"
    "ERROR: GC overhead limit exceeded - java.lang.OutOfMemoryError: GC overhead limit exceeded"
    "ERROR: Unable to create new native thread - java.lang.OutOfMemoryError: unable to create new native thread"

    # ClassNotFoundException / NoClassDefFoundError
    "ERROR: Class not found - java.lang.ClassNotFoundException: com.company.util.DateHelper"
    "ERROR: NoClassDefFoundError - java.lang.NoClassDefFoundError: org/apache/commons/lang3/StringUtils"

    # IOException / FileNotFoundException
    "ERROR: Failed to read configuration file - java.io.FileNotFoundException: /etc/app/config.properties (No such file or directory)"
    "ERROR: IOException while writing to file - java.io.IOException: Disk quota exceeded"
    "ERROR: Failed to connect to remote server - java.net.ConnectException: Connection refused (Connection refused)"
    "ERROR: Socket timeout - java.net.SocketTimeoutException: Read timed out after 60000ms"

    # JSON/Parsing Errors
    "ERROR: Failed to parse JSON response - com.fasterxml.jackson.core.JsonParseException: Unexpected character at line 1 column 45"
    "ERROR: JSON mapping error - com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field 'userId'"
    "ERROR: Invalid date format - java.time.format.DateTimeParseException: Text '2026-13-01' could not be parsed: Invalid value for MonthOfYear"

    # IllegalArgumentException / IllegalStateException
    "ERROR: Invalid parameter - java.lang.IllegalArgumentException: Customer ID cannot be negative"
    "ERROR: Invalid state - java.lang.IllegalStateException: Transaction already committed"
    "ERROR: NumberFormatException - java.lang.NumberFormatException: For input string: 'abc123'"

    # Transaction / Concurrency Errors
    "ERROR: Transaction rolled back - org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back"
    "ERROR: Optimistic locking failure - org.hibernate.StaleObjectStateException: Row was updated or deleted by another transaction"
    "ERROR: ConcurrentModificationException in cache update - java.util.ConcurrentModificationException at java.util.HashMap\$HashIterator.nextNode"

    # HTTP/REST API Errors
    "ERROR: HTTP 500 Internal Server Error from payment gateway at https://api.payment.com/v1/charge"
    "ERROR: HTTP 404 Not Found - Resource /api/customers/99999 does not exist"
    "ERROR: HTTP 503 Service Unavailable - External API temporarily unavailable"
    "ERROR: HTTP 401 Unauthorized - Invalid API key provided"
    "ERROR: REST call failed - org.springframework.web.client.ResourceAccessException: I/O error on POST request"

    # Spring Framework Errors
    "ERROR: Bean creation failed - org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'dataSource'"
    "ERROR: No qualifying bean found - org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.company.service.PaymentService'"
    "ERROR: Autowiring failed - org.springframework.beans.factory.UnsatisfiedDependencyException: Unsatisfied dependency expressed through field 'customerRepository'"

    # Security Errors
    "ERROR: Access denied - org.springframework.security.access.AccessDeniedException: Access is denied"
    "ERROR: Authentication failed - org.springframework.security.authentication.BadCredentialsException: Bad credentials"
    "ERROR: JWT token expired - io.jsonwebtoken.ExpiredJwtException: JWT expired at 2026-03-12T10:30:00Z"

    # Validation Errors
    "ERROR: Validation failed - javax.validation.ConstraintViolationException: email must be a well-formed email address"
    "ERROR: Invalid input - org.springframework.web.bind.MethodArgumentNotValidException: Field 'phoneNumber' must not be blank"

    # Cache / Redis Errors
    "ERROR: Redis connection failed - redis.clients.jedis.exceptions.JedisConnectionException: Could not get a resource from the pool"
    "ERROR: Cache operation failed - org.springframework.data.redis.RedisSystemException: Error in execution"

    # Message Queue Errors
    "ERROR: Failed to send message to queue - javax.jms.JMSException: Queue not found: order.processing"
    "ERROR: Message processing failed - org.springframework.amqp.rabbit.support.ListenerExecutionFailedException: Listener threw exception"

    # Retry messages
    "Retrying database connection attempt 1 of 3"
    "Retrying API call with exponential backoff - attempt 2"
    "Successfully recovered after retry"
    "Max retry attempts reached - marking operation as failed"
)

echo "Generating $NUM_DAYS days of logs with ~$LOGS_PER_DAY entries per day..."

for day in $(seq 0 $((NUM_DAYS - 1))); do
    # Calculate date (going backwards from today)
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        DATE=$(date -v-${day}d +%Y%m%d)
        DATE_ISO=$(date -v-${day}d +%Y-%m-%d)
    else
        # Linux
        DATE=$(date -d "$day days ago" +%Y%m%d)
        DATE_ISO=$(date -d "$day days ago" +%Y-%m-%d)
    fi

    OUTPUT_FILE="$OUTPUT_DIR/server-$DATE.log"

    echo "Generating $OUTPUT_FILE..."

    # Clear file if exists
    > "$OUTPUT_FILE"

    for i in $(seq 1 $LOGS_PER_DAY); do
        # Random hour (0-23), minute (0-59), second (0-59), millisecond (0-999)
        HOUR=$(printf "%02d" $((RANDOM % 24)))
        MINUTE=$(printf "%02d" $((RANDOM % 60)))
        SECOND=$(printf "%02d" $((RANDOM % 60)))
        MILLIS=$(printf "%03d" $((RANDOM % 1000)))

        # Random user
        USER=${USERS[$((RANDOM % ${#USERS[@]}))]}

        # Random message (no placeholders needed for realistic errors)
        MSG=${MESSAGES[$((RANDOM % ${#MESSAGES[@]}))]}

        # Timestamp in NZDT format (UTC+13)
        TIMESTAMP="${DATE_ISO}T${HOUR}:${MINUTE}:${SECOND}.${MILLIS}+13:00"

        # Write log entry
        echo "[$TIMESTAMP] [$USER] $MSG" >> "$OUTPUT_FILE"
    done

    echo "  Generated $(wc -l < "$OUTPUT_FILE") log entries"
done

echo ""
echo "Log generation complete!"
echo "Total files: $(ls -1 $OUTPUT_DIR/server-*.log | wc -l)"
echo "Total log entries: $(cat $OUTPUT_DIR/server-*.log | wc -l)"
echo ""
echo "To index these logs, run:"
echo "  java -jar target/log-search-1.0.0.jar start --logs-dir=$OUTPUT_DIR"

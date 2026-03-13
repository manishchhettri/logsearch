#!/bin/bash

# Mixed Log Format Generator
# Generates logs with various formats to test fallback parsing
# Usage: ./generate-mixed-logs.sh [filename]

FILENAME=${1:-mixed-format.log}
OUTPUT_DIR="./logs"

# Create logs directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

OUTPUT_FILE="$OUTPUT_DIR/$FILENAME"

echo "Generating mixed-format log file: $OUTPUT_FILE"

# Clear the file if it exists
> "$OUTPUT_FILE"

# Pattern 1: WebLogic format (structured)
cat >> "$OUTPUT_FILE" << 'EOF'
[13 Mar 2026 10:15:30,123] [[ACTIVE] ExecuteThread: '0' for queue: 'weblogic.kernel.Default (self-tuning)'] [INFO ] [com.example.app.Service] [] [user:admin] - Application started successfully
[13 Mar 2026 10:15:31,456] [[ACTIVE] ExecuteThread: '1' for queue: 'weblogic.kernel.Default (self-tuning)'] [ERROR] [com.example.app.Database] [] [user:system] - Connection pool exhausted
EOF

# Pattern 2: ISO 8601 timestamp (semi-structured)
cat >> "$OUTPUT_FILE" << 'EOF'
2026-03-13T10:16:00 INFO User login successful: john.doe
2026-03-13T10:16:15 WARN Database query took 5.2 seconds
2026-03-13T10:16:30 ERROR Failed to process payment for transaction ID: TXN-12345
EOF

# Pattern 3: Apache/NCSA log format
cat >> "$OUTPUT_FILE" << 'EOF'
13/Mar/2026:10:17:00 +1300 [INFO] Processing request from 192.168.1.100
13/Mar/2026:10:17:15 +1300 [ERROR] File not found: /var/www/html/missing.html
EOF

# Pattern 4: Syslog format
cat >> "$OUTPUT_FILE" << 'EOF'
Mar 13 10:18:00 server1 kernel: Out of memory: Kill process 12345
Mar 13 10:18:05 server1 sshd[5678]: Failed password for invalid user from 192.168.1.50
EOF

# Pattern 5: Simple date-time
cat >> "$OUTPUT_FILE" << 'EOF'
2026/03/13 10:19:00 - Starting backup process
2026/03/13 10:19:30 - Backup completed: 5.2 GB transferred
EOF

# Pattern 6: Plain text (no timestamp - will use file metadata)
cat >> "$OUTPUT_FILE" << 'EOF'
This is a plain text log entry without any timestamp
Another plain line with important error information: OutOfMemoryError
System is running low on disk space - only 5% remaining
EOF

# Pattern 7: Custom application format
cat >> "$OUTPUT_FILE" << 'EOF'
[2026-03-13 10:20:00] [TRACE] com.example.MyClass - Entering method processData()
[2026-03-13 10:20:01] [DEBUG] com.example.MyClass - Processing 1000 records
[2026-03-13 10:20:05] [INFO] com.example.MyClass - Successfully processed all records
EOF

# Pattern 8: JSON-style logs (timestamp embedded)
cat >> "$OUTPUT_FILE" << 'EOF'
{"timestamp":"2026-03-13T10:21:00","level":"INFO","message":"API request received","endpoint":"/api/users"}
{"timestamp":"2026-03-13T10:21:01","level":"ERROR","message":"Database timeout","duration_ms":5000}
EOF

# Pattern 9: Mixed with stack traces (multi-line)
cat >> "$OUTPUT_FILE" << 'EOF'
[13 Mar 2026 10:22:00,000] [[STANDBY] ExecuteThread: '5' for queue: 'weblogic.kernel.Default (self-tuning)'] [ERROR] [com.example.ErrorHandler] [] [user:system] - Exception in thread "main" java.lang.NullPointerException
   at com.example.MyClass.processData(MyClass.java:45)
   at com.example.Main.run(Main.java:123)
   Caused by: java.io.IOException: Connection reset
   at java.net.SocketInputStream.read(SocketInputStream.java:210)
EOF

# Pattern 10: Timestamp at end of line
cat >> "$OUTPUT_FILE" << 'EOF'
Critical error in payment processing module - 2026-03-13 10:23:00
System restarted after crash - 2026-03-13 10:23:30
EOF

# Pattern 11: No timestamp at all (pure plain text)
cat >> "$OUTPUT_FILE" << 'EOF'
ERROR: Configuration file missing
WARNING: Using default settings
INFO: System initialized successfully
EOF

# Pattern 12: Windows-style timestamp
cat >> "$OUTPUT_FILE" << 'EOF'
3/13/2026 10:24:00 AM - Service started
3/13/2026 10:24:15 AM - Processing queue items: 150 pending
EOF

echo "✓ Generated $OUTPUT_FILE with multiple log formats"
echo ""
echo "Log patterns included:"
echo "  1. WebLogic format (structured)"
echo "  2. ISO 8601 timestamp"
echo "  3. Apache/NCSA format"
echo "  4. Syslog format"
echo "  5. Simple date-time"
echo "  6. Plain text (no timestamp)"
echo "  7. Custom application format"
echo "  8. JSON-style logs"
echo "  9. Multi-line stack traces"
echo " 10. Timestamp at end"
echo " 11. Pure plain text"
echo " 12. Windows-style timestamp"
echo ""
echo "Total lines: $(wc -l < "$OUTPUT_FILE")"

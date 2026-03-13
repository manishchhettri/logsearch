# Log Search Application

A lightweight, embedded Lucene-based log search application designed for developers to search through historical logs locally.

## Features

- **Embedded Lucene**: No external services required - runs as a single JAR file
- **Code-aware search**: Custom analyzer optimized for Java stack traces and class names
- **Day-based indexing**: Smart partitioning by day for efficient date-range queries
- **Fast search**: Full-text search across GBs of logs in seconds
- **Modern UI**: Clean web interface with syntax highlighting and formatted stack traces
- **Auto-indexing**: Automatically watches for new log files
- **Flexible log parsing**: Support for simple 3-group and enhanced 6-group log formats (WebLogic, etc.)
- **Multi-line support**: Properly handles stack traces and multi-line log messages
- **Fallback parsing**: Intelligently indexes logs that don't match the primary pattern
- **Configurable**: Support for different log formats and timezones
- **Retention management**: Auto-cleanup of old indexes

## Requirements

- Java 8 or higher
- Maven 3.6+ (for building)

## Quick Start

### Option 1: Use Pre-built JAR (Recommended)

The repository includes a pre-built JAR file, so you can start immediately without building:

**1. Clone the repository**
```bash
git clone <your-repo-url>
cd log-search
```

The JAR file is already available at: `target/log-search-1.0.0.jar`

**2. Prepare your logs

Place your log files in a directory (e.g., `./logs`). The default pattern expects files named like:
- `server-20260312.log`
- `server-20260313.log`

**3. Run the application**

```bash
# Simple start - indexes logs and starts web server
java -jar target/log-search-1.0.0.jar start --logs-dir=./logs

# Or just:
java -jar target/log-search-1.0.0.jar --logs-dir=./logs
```

The application will:
1. Index all log files in the specified directory
2. Start the web server on http://localhost:8080
3. Automatically open your browser

---

### Option 2: Build from Source

If you want to modify the code or build it yourself:

**1. Build the application**
```bash
mvn clean package
```

This creates an executable JAR: `target/log-search-1.0.0.jar`

**2. Prepare your logs and run**

Follow steps 2-3 from Option 1 above.

---

## Usage

### Commands

**Start the application (default)**
```bash
# Using startup script (recommended)
./start.sh start --logs-dir=/path/to/logs

# Or directly with Java
java -jar log-search-1.0.0.jar start --logs-dir=/path/to/logs
```

**Stop the application**
```bash
# Using shutdown script (recommended)
./stop.sh

# The script will gracefully stop the running application
```

**Index logs only (then exit)**
```bash
java -jar log-search-1.0.0.jar index --logs-dir=/path/to/logs
```

### Command-line Options

| Option | Description | Default |
|--------|-------------|---------|
| `--logs-dir` | Directory containing log files | `./logs` |
| `--index-dir` | Directory to store Lucene indexes | `./.log-search/indexes` |

### Using the Web UI

1. Open http://localhost:8080
2. Set the date/time range or click "Last 24 Hours"
3. Enter search terms (optional - leave empty to see all logs)
4. Click "Search"

## Search Guide

### How Search Works

The application uses a custom **CodeAnalyzer** optimized for searching logs with Java stack traces, class names, and code. It intelligently tokenizes:

- **Java class names**: `com.example.DataValidator` → searchable by "DataValidator", "example", or "com"
- **Method names**: `validateLevel78()` → searchable by "validateLevel78" or "validate"
- **Stack traces**: Each line is properly separated and searchable
- **Package names**: `org.springframework.web` → searchable by "springframework", "web", or "org"

### Basic Search Examples

**Simple word search**
```
error
```
Finds any log entry containing "error" (case-insensitive)

**Multiple words (implicit AND)**
```
database connection
```
Finds entries containing both "database" AND "connection"

**Phrase search**
```
"Out of memory"
```
Finds the exact phrase (words together in that order)

**View all logs**
```
(leave search box empty)
```
Shows all logs in the selected date range

### Searching Java Code and Stack Traces

**Search by class name**
```
DataValidator
```
Finds: `com.example.validation.DataValidator`, `DataValidator.java`, etc.

**Search by package component**
```
springframework
```
Finds: `org.springframework.web.servlet.DispatcherServlet`, etc.

**Search by method name**
```
validateLevel78
```
Finds: `DataValidator.validateLevel78(DataValidator.java:1232)`

**Search by exception type**
```
NullPointerException
```
Finds all NPE occurrences in stack traces

**Search for specific file**
```
DispatcherServlet.java
```
Finds stack trace lines from that file

**Combination searches**
```
DataValidator AND OutOfMemoryError
```
Finds entries where both appear (in stack traces or messages)

### Advanced Search Syntax

**Boolean operators (must be UPPERCASE)**
```
ERROR AND database
ERROR OR timeout
ERROR NOT connection
(memory OR heap) AND java
```

**Wildcards**
```
connect*         → connects, connecting, connection, etc.
*Exception       → NullPointerException, IOException, etc.
Data*Validator   → DataValidator, DataRecordValidator, etc.
```

**Field-specific searches**
```
level:ERROR                → Only ERROR level logs
level:WARN OR level:ERROR  → WARN or ERROR logs
user:admin                 → Logs from user 'admin'
logger:DataValidator       → Logs from specific logger
thread:ExecuteThread       → Logs from specific thread
```

**Combining techniques**
```
level:ERROR AND (database OR connection)
user:admin AND OutOfMemory*
"stack overflow" AND DataValidator
```

### Search Tips & Tricks

**1. Finding Errors by Type**
```
NullPointerException        → Find all NPEs
OutOfMemoryError           → Find memory issues
StackOverflowError         → Find stack overflow errors
SQLException               → Find database errors
```

**2. Finding Specific Components**
```
TransactionManager         → All logs mentioning this class
curam.core                → All logs from curam.core package
weblogic.servlet          → All WebLogic servlet logs
```

**3. Finding User Activity**
```
user:john.doe             → All actions by john.doe
user:admin AND ERROR      → All errors from admin user
user:* AND "login failed" → Failed logins for any user
```

**4. Performance Investigation**
```
"slow query"              → Database performance issues
timeout                   → Timeout errors
"response time"           → Performance metrics
heap AND memory           → Memory-related logs
```

**5. Debugging Workflows**
```
caseID                    → Find case-related logs
sessionID                 → Track session activity
"workflow execution"      → Workflow logs
HandleRequest AND ERROR   → Failed requests
```

**6. Finding Configuration Issues**
```
"configuration" AND (error OR failed)
"property" AND "not found"
"missing" AND "required"
```

**7. Integration Points**
```
"rest api" OR "web service"
jdbc AND connection
jms AND queue
ldap OR "active directory"
```

### Common Search Patterns

| What you want to find | Search query |
|----------------------|--------------|
| All errors in last hour | `level:ERROR` (set time range) |
| Specific user's errors | `user:john.doe AND level:ERROR` |
| Database connection issues | `database AND (connection OR jdbc)` |
| Memory problems | `OutOfMemoryError OR "heap space"` |
| Specific exception | `NullPointerException` |
| Method that failed | `DataValidator.validate` |
| Slow performance | `slow OR timeout OR "took * ms"` |
| Failed authentications | `auth* AND (failed OR denied)` |
| Specific transaction | `transactionID:12345` (if in logs) |
| Stack traces mentioning class | `DataValidator` |

### Understanding Search Results

**Log Entry Display**
- **Time**: Timestamp of the log entry (in your local timezone)
- **Level Badge**: Color-coded severity (ERROR=red, WARN=yellow, INFO=blue, DEBUG=gray)
- **User**: User associated with the log entry
- **Logger**: Java class/component that generated the log
- **Thread**: Thread name (useful for concurrent debugging)
- **File:LineNumber**: Source file and line number in your log file

**Stack Traces**
- Automatically formatted with proper line breaks
- Each stack frame on its own line
- Searchable by any class, method, or package name in the trace

### Performance Tips

**1. Use date ranges wisely**
- Narrow time ranges = faster searches
- Search last hour instead of last 30 days when possible
- Day-based indexing makes single-day searches very fast (~50-200ms)

**2. Be specific**
- `NullPointerException AND DataValidator` is faster than just `error`
- Specific terms reduce result set size

**3. Use field searches when possible**
- `level:ERROR` is faster than searching message content
- `user:admin` is faster than free-text "admin"

**4. Avoid leading wildcards**
- `*Exception` is slower than `Exception*`
- `DataValidator*` is faster than `*Validator`

### Troubleshooting Searches

**No results found?**
- ✓ Check date range includes your log timestamps
- ✓ Try broader search terms (e.g., just "error" instead of "error connecting")
- ✓ Verify logs are indexed (check "Re-Index Logs" button)
- ✓ Remove quotes if searching for individual words
- ✓ Check spelling and case (searches are case-insensitive)

**Too many results?**
- ✓ Narrow the date range
- ✓ Add more specific terms: `error` → `error AND database`
- ✓ Use field searches: `level:ERROR` instead of `error`
- ✓ Add user filter: `user:john.doe`

**Searching not finding Java class names?**
- ✓ The CodeAnalyzer splits on dots automatically
- ✓ Search just the class name: `DataValidator` (not `com.example.DataValidator`)
- ✓ Or search any part: "validation", "example", "DataValidator"

### API Endpoints

The REST API allows programmatic access for automation and integration.

**Search logs**
```bash
# Basic search
curl "http://localhost:8080/api/search?query=error&startTime=2026-03-12T00:00:00Z&endTime=2026-03-12T23:59:59Z"

# Search by class name
curl "http://localhost:8080/api/search?query=DataValidator&startTime=2026-03-13T00:00:00Z&endTime=2026-03-13T23:59:59Z"

# Field-specific search
curl "http://localhost:8080/api/search?query=level:ERROR&startTime=2026-03-12T00:00:00Z&endTime=2026-03-12T23:59:59Z"

# Complex query with pagination
curl "http://localhost:8080/api/search?query=error+AND+database&startTime=2026-03-12T00:00:00Z&endTime=2026-03-12T23:59:59Z&page=0&pageSize=50"

# URL-encoded query with special characters
curl "http://localhost:8080/api/search?query=OutOfMemoryError&startTime=2026-03-13T00:00:00%2B13:00&endTime=2026-03-13T23:59:59%2B13:00"
```

**Response format:**
```json
{
  "entries": [
    {
      "timestamp": "2026-03-13T15:30:00.123+13:00",
      "level": "ERROR",
      "thread": "[[ACTIVE] ExecuteThread: '5']",
      "logger": "com.example.DeepRecursionHandler",
      "user": "admin",
      "message": "StackOverflowError: Recursive call depth exceeded\n   at com.example.validation.DataValidator.validateLevel78(...)",
      "sourceFile": "long-stacktraces.log",
      "lineNumber": 1
    }
  ],
  "totalHits": 2,
  "page": 0,
  "pageSize": 50,
  "searchTimeMs": 243
}
```

**Trigger re-indexing**
```bash
curl -X POST http://localhost:8080/api/index
```

**Response:**
```json
{
  "success": true,
  "message": "Indexing completed",
  "indexedFiles": 13
}
```

**Check status**
```bash
curl http://localhost:8080/api/status
```

**Response:**
```json
{
  "status": "running",
  "indexedFiles": 13
}
```

**Query Parameters:**

| Parameter | Required | Description | Example |
|-----------|----------|-------------|---------|
| `query` | No | Search query (empty = all logs) | `error`, `DataValidator`, `level:ERROR` |
| `startTime` | Yes | Start of time range (ISO 8601) | `2026-03-12T00:00:00Z` |
| `endTime` | Yes | End of time range (ISO 8601) | `2026-03-12T23:59:59Z` |
| `page` | No | Page number (0-indexed) | `0` (default) |
| `pageSize` | No | Results per page | `100` (default), max `1000` |

**Automation Examples:**

```bash
# Daily error report
curl "http://localhost:8080/api/search?query=level:ERROR&startTime=$(date -u -d '1 day ago' +%Y-%m-%dT00:00:00Z)&endTime=$(date -u +%Y-%m-%dT23:59:59Z)" | jq '.totalHits'

# Find OutOfMemoryErrors in last hour
curl "http://localhost:8080/api/search?query=OutOfMemoryError&startTime=$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)&endTime=$(date -u +%Y-%m-%dT%H:%M:%SZ)" | jq -r '.entries[].message'

# Monitor specific user's activity
curl "http://localhost:8080/api/search?query=user:admin&startTime=$(date -u +%Y-%m-%dT00:00:00Z)&endTime=$(date -u +%Y-%m-%dT23:59:59Z)" | jq '.totalHits'
```

## Configuration

### External Configuration (Recommended)

Edit `config/application.yml` to customize settings without rebuilding:

```yaml
server:
  port: 8080

log-search:
  # Directory containing log files to index
  logs-dir: ${LOGS_DIR:./logs}

  # Directory where Lucene indexes will be stored
  index-dir: ${INDEX_DIR:./.log-search/indexes}

  # Log file pattern (regex) - matches any .log file
  file-pattern: ".*\\.log"

  # Date extraction from filename (optional)
  # If filename doesn't match, falls back to file modified time
  filename-date-pattern: "server-(\\d{4})(\\d{2})(\\d{2})\\.log"

  # Log line pattern - Enhanced 6-group WebLogic format
  # Groups: (timestamp)(thread)(level)(logger)(user)(message)
  log-line-pattern: "^\\[([^\\]]+)\\]\\s+(.+?)\\s+\\[([^\\]]+)\\]\\s+\\[([^\\]]+)\\]\\s+\\[\\]\\s+\\[user:([^\\]]+)\\]\\s+-\\s+(.*)$"

  # Date format in log lines
  log-datetime-format: "dd MMM yyyy HH:mm:ss,SSS"

  # Timezone for parsing logs
  timezone: "Pacific/Auckland"

  # Days to retain indexes (0 = unlimited)
  retention-days: 30

  # Auto-watch for new log files
  auto-watch: true

  # Watch interval in seconds (how often to check for new files)
  watch-interval: 60

  # Enable fallback parsing for non-matching log lines
  # When enabled, lines that don't match the primary pattern will still be indexed
  enable-fallback: true

logging:
  level:
    com.lsearch.logsearch: INFO
```

### Configuration Options Explained

| Setting | Description | Example |
|---------|-------------|---------|
| `logs-dir` | Directory containing log files | `./logs` or `/var/log/app` |
| `index-dir` | Where Lucene indexes are stored | `./.log-search/indexes` |
| `file-pattern` | Regex to match log filenames | `.*\\.log` or `server-\\d{8}\\.log` |
| `filename-date-pattern` | Extract date from filename (optional) | `server-(\\d{4})(\\d{2})(\\d{2})\\.log` |
| `log-line-pattern` | Regex to parse log lines | See examples above |
| `log-datetime-format` | Java DateTimeFormatter pattern | `dd MMM yyyy HH:mm:ss,SSS` |
| `timezone` | Timezone for parsing timestamps | `Pacific/Auckland`, `UTC`, `America/New_York` |
| `retention-days` | How many days to keep indexes | `30` (or `0` for unlimited) |
| `auto-watch` | Automatically index new log files | `true` or `false` |
| `watch-interval` | Seconds between checking for new files | `60` |
| `enable-fallback` | Index non-matching lines with fallback | `true` or `false` |

### Quick Configuration Examples

**For simple logs** (`[timestamp] [user] message`):
```yaml
log-line-pattern: "^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$"
log-datetime-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
```

**For WebLogic logs** (6-group pattern):
```yaml
log-line-pattern: "^\\[([^\\]]+)\\]\\s+(.+?)\\s+\\[([^\\]]+)\\]\\s+\\[([^\\]]+)\\]\\s+\\[\\]\\s+\\[user:([^\\]]+)\\]\\s+-\\s+(.*)$"
log-datetime-format: "dd MMM yyyy HH:mm:ss,SSS"
```

**For different timezones**:
```yaml
timezone: "America/New_York"   # US Eastern
timezone: "Europe/London"       # UK
timezone: "Asia/Tokyo"          # Japan
timezone: "UTC"                 # Universal Time
```

**For high-volume environments** (reduce retention):
```yaml
retention-days: 7              # Keep only last 7 days
watch-interval: 300            # Check every 5 minutes
```

## Log Format Support

The application supports two log format patterns:

### Simple 3-Group Pattern (Basic)

```
[timestamp] [user] message
```

Example:
```
[2026-03-12T14:30:45.123+13:00] [john.doe] Application started successfully
[2026-03-12T14:30:46.456+13:00] [jane.smith] Database connection established
```

Configuration:
```yaml
log-line-pattern: "^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$"
log-datetime-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
```

Capture groups: (timestamp)(user)(message)

### Enhanced 6-Group Pattern (WebLogic, Enterprise)

```
[timestamp] [thread] [level] [logger] [] [user:username] - message
```

Example:
```
[13 Mar 2026 15:30:00,123] [[ACTIVE] ExecuteThread: '5' for queue: 'weblogic.kernel.Default'] [ERROR] [com.example.DeepRecursionHandler] [] [user:admin] - StackOverflowError: Recursive call depth exceeded
```

Configuration:
```yaml
log-line-pattern: "^\\[([^\\]]+)\\]\\s+(.+?)\\s+\\[([^\\]]+)\\]\\s+\\[([^\\]]+)\\]\\s+\\[\\]\\s+\\[user:([^\\]]+)\\]\\s+-\\s+(.*)$"
log-datetime-format: "dd MMM yyyy HH:mm:ss,SSS"
```

Capture groups: (timestamp)(thread)(level)(logger)(user)(message)

**Benefits of 6-group pattern:**
- Search by log level: `level:ERROR`
- Search by logger/class: `logger:DataValidator`
- Search by thread: `thread:ExecuteThread`
- Color-coded severity badges in UI
- Better structured debugging

### Multi-line Support

Both patterns support multi-line log entries (stack traces, exception details):

```
[13 Mar 2026 15:30:00,123] [[ACTIVE] ExecuteThread: '5'] [ERROR] [com.example.Handler] [] [user:admin] - Error processing request
   at com.example.validation.DataValidator.validateLevel78(DataValidator.java:1232)
   at com.example.validation.DataValidator.validateLevel77(DataValidator.java:1231)
   at com.example.validation.DataValidator.validateLevel76(DataValidator.java:1230)
   ... (continuation lines are automatically attached to the previous entry)
```

### Fallback Parsing

If `enable-fallback: true` (default), the parser uses a 3-tier approach:

1. **Tier 1**: Try primary configured pattern
2. **Tier 2**: Auto-detect timestamp patterns (ISO 8601, Apache, Syslog, etc.)
3. **Tier 3**: Use file metadata (filename date or file modified time)

This ensures even non-standard log lines get indexed and are searchable.

### Customizing for Different Formats

Edit `config/application.yml` to match your log format:

**Apache/Nginx Access Logs**:
```yaml
log-line-pattern: "^([^\\s]+\\s+[^\\s]+)\\s+(\\S+)\\s+(.*)$"
log-datetime-format: "dd/MMM/yyyy:HH:mm:ss Z"
```

**Standard Java Application Logs**:
```yaml
log-line-pattern: "^\\[([^\\]]+)\\]\\s+\\[([^\\]]+)\\]\\s+(.*)$"
log-datetime-format: "yyyy-MM-dd HH:mm:ss,SSS"
```

**Custom Delimited Format** - `timestamp|user|message`:
```yaml
log-line-pattern: "^([^|]+)\\|([^|]+)\\|(.*)$"
log-datetime-format: "yyyy-MM-dd HH:mm:ss"
```

**Pattern Requirements:**
- 3-group pattern: (timestamp)(user)(message)
- 6-group pattern: (timestamp)(thread)(level)(logger)(user)(message)
- First capture group must always be a parseable timestamp

## How It Works

### Day-based Indexing

The application creates separate Lucene indexes for each day:

```
.log-search/indexes/
├── 2026-03-10/
├── 2026-03-11/
├── 2026-03-12/
└── 2026-03-13/
```

When you search for a date range like "2026-03-12 14:00 to 2026-03-13 16:00":
- Opens only the `2026-03-12` and `2026-03-13` indexes
- Applies timestamp filtering within those indexes
- Much faster than searching a single monolithic index

### Index Size

- Raw logs: ~2GB/day
- Lucene index: ~400-800MB/day (20-40% of raw size)
- 30 days retention: ~12-24GB index storage

## Performance

Typical search times on a modern laptop:

- **Single day**: 50-200ms
- **7 days**: 200-500ms
- **30 days**: 500-1500ms

For 2GB of logs per day, with full-text search.

## Troubleshooting

**No results found**
- Check the date range includes the log files
- Verify log files match the `file-pattern` in config
- Check logs are being indexed: look for "Indexing completed" in console

**Slow searches**
- Narrow the date range
- Use more specific search terms
- Check index size (may need to reduce retention)

**Out of memory**
- Increase JVM heap: `java -Xmx2g -jar log-search-1.0.0.jar`
- Reduce retention days

**Logs not parsing**
- Check the log format matches expected pattern
- Verify timezone settings
- Check console for parsing errors

## Development

### Project Structure

```
src/main/java/com/lsearch/logsearch/
├── config/          # Configuration properties
├── controller/      # REST API controllers
├── model/           # Domain models
├── service/         # Business logic
│   ├── LogParser.java           # Parses log lines
│   ├── LuceneIndexService.java  # Manages Lucene indexes
│   ├── LogSearchService.java    # Search functionality
│   └── LogFileIndexer.java      # File watching & indexing
└── cli/             # Command-line interface
```

### Building

**Note**: Building is optional! A pre-built JAR is included in the repository at `target/log-search-1.0.0.jar`.

If you want to rebuild or modify the code:

```bash
# Build
mvn clean package

# Run tests
mvn test

# Run locally
mvn spring-boot:run -Dspring-boot.run.arguments="start --logs-dir=./logs"
```

## License

This is an internal tool. Modify as needed for your use case.

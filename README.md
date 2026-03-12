# Log Search Application

A lightweight, embedded Lucene-based log search application designed for developers to search through historical logs locally.

## Features

- **Embedded Lucene**: No external services required - runs as a single JAR file
- **Day-based indexing**: Smart partitioning by day for efficient date-range queries
- **Fast search**: Full-text search across GBs of logs in seconds
- **Simple UI**: Clean web interface for searching and browsing logs
- **Auto-indexing**: Automatically watches for new log files
- **Configurable**: Support for different log formats and timezones
- **Retention management**: Auto-cleanup of old indexes

## Requirements

- Java 8 or higher
- Maven 3.6+ (for building)

## Quick Start

### 1. Build the application

```bash
mvn clean package
```

This creates an executable JAR: `target/log-search-1.0.0.jar`

### 2. Prepare your logs

Place your log files in a directory (e.g., `./logs`). The default pattern expects files named like:
- `server-20260312.log`
- `server-20260313.log`

### 3. Run the application

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

### Search Tips

- **Simple text search**: Just type words to find in log messages
- **Phrase search**: Use quotes: `"error connecting"`
- **Boolean operators**: Use AND, OR, NOT: `error AND database`
- **Date range**: Always required - limits which day-based indexes to search
- **Wildcard**: Use `*` for partial matches: `user*`

### API Endpoints

**Search logs**
```bash
GET /api/search?query=error&startTime=2026-03-12T00:00:00Z&endTime=2026-03-12T23:59:59Z&page=0&pageSize=100
```

**Trigger re-indexing**
```bash
POST /api/index
```

**Check status**
```bash
GET /api/status
```

## Configuration

Edit `src/main/resources/application.yml` to customize:

```yaml
log-search:
  # Log file pattern (regex)
  file-pattern: "server-\\d{8}\\.log"

  # Date extraction from filename
  filename-date-pattern: "server-(\\d{4})(\\d{2})(\\d{2})\\.log"

  # Log line pattern (regex with 3 capture groups: timestamp, user, message)
  log-line-pattern: "^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$"

  # Date format in log lines
  log-datetime-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"

  # Timezone for parsing
  timezone: "Pacific/Auckland"

  # Days to retain indexes (0 = unlimited)
  retention-days: 30

  # Auto-watch for new files
  auto-watch: true

  # Watch interval (seconds)
  watch-interval: 60
```

## Log Format

The default log format is:

```
[timestamp] [user] message
```

Example:
```
[2026-03-12T14:30:45.123+13:00] [john.doe] Application started successfully
[2026-03-12T14:30:46.456+13:00] [jane.smith] Database connection established
```

### Customizing for Different Formats

The log format is fully configurable! Edit `config/application.yml` to match your log format:

```yaml
log-search:
  # Log line pattern with 3 capture groups: (timestamp)(user)(message)
  log-line-pattern: "^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$"

  # Date format for parsing timestamps
  log-datetime-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
```

**Examples of different log formats:**

**Default format** - `[timestamp] [user] message`:
```yaml
log-line-pattern: "^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$"
log-datetime-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
```

**Space-separated format** - `timestamp user message`:
```yaml
log-line-pattern: "^(\\S+)\\s+(\\S+)\\s+(.*)$"
log-datetime-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
```

**Syslog format** - `Mar 12 14:30:45 user message`:
```yaml
log-line-pattern: "^(\\S+\\s+\\d+\\s+\\S+)\\s+(\\S+)\\s+(.*)$"
log-datetime-format: "MMM dd HH:mm:ss"
```

**Important**: The regex must have exactly 3 capture groups in this order:
1. Timestamp (will be parsed using `log-datetime-format`)
2. User
3. Message

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
src/main/java/com/company/logsearch/
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

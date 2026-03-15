# Quick Start Guide

Get up and running with LogSearch in under 5 minutes.

## Prerequisites

- Java 8 or higher installed
- No Maven required for running (only for building from source)

## Option 1: Download and Run (Recommended)

### 1. Download the JAR

Download `log-search-1.0.0.jar` from the releases page or build from source.

### 2. Create Required Directories

```bash
# Create directories for logs and indexes
mkdir -p logs
mkdir -p .log-search/indexes

# Metadata directory is auto-created when chunking is enabled
```

**Directory Structure:**
```
your-project/
├── log-search-1.0.0.jar
├── logs/                        # Put your log files here
├── .log-search/
│   └── indexes/                 # Lucene indexes (auto-created)
│       ├── 2026-03-10/          # Day-based indexes
│       ├── 2026-03-11/
│       └── metadata/            # Chunk metadata (if chunking enabled)
└── config/
    └── application.yml          # Optional configuration
```

### 3. Start the application

```bash
# Basic startup (uses ./logs directory by default)
java -jar log-search-1.0.0.jar

# Or specify your logs directory
java -jar log-search-1.0.0.jar --logs-dir=/path/to/your/logs
```

You should see:
```
=================================================================
Log Search is running!
Web UI: http://localhost:8080
API: http://localhost:8080/api/search
=================================================================
```

The application will:
- Automatically detect your log format (WebLogic, Tomcat, etc.)
- Index all .log files in the directory
- Create chunks and metadata (if chunking enabled)
- Open the web UI in your browser

### 4. Open the Web UI

Navigate to: **http://localhost:8080**

The interface has three main tabs:
- **Search Logs** - Search and analyze your logs
- **Download Logs** - Bulk download from URLs or paths
- **Dashboards** - Create custom dashboards

## Option 2: Build from Source

### 1. Build the application

```bash
git clone <repository-url>
cd log-search
mvn clean package
```

This creates: `target/log-search-1.0.0.jar`

### 2. Run as described in Option 1

## Using the Search Interface

1. Click "Last 24 Hours" to set the date range
2. Try these searches:

**Basic searches:**
   - Leave search empty and click "Search" to see all logs
   - `error` - find all errors
   - `level:ERROR` - find ERROR level logs only
   - `user:admin` - find logs from admin user

**Java code searches:**
   - `DataValidator` - find logs mentioning this class
   - `OutOfMemoryError` - find memory errors
   - `NullPointerException` - find NPE stack traces
   - `springframework` - find Spring framework logs

**Advanced searches:**
   - `error AND database` - both terms must be present
   - `"database connection"` - exact phrase search
   - `level:ERROR AND user:admin` - errors from specific user
   - `OutOfMemory* OR StackOverflow*` - memory or stack issues

### 4. Use your own logs

```bash
# Copy your logs to the logs directory
cp /path/to/your/server-*.log ./logs/

# Or point to your logs directory
java -jar target/log-search-1.0.0.jar start --logs-dir=/path/to/your/logs
```

## Smart Format Detection (No Configuration Required!)

LogSearch **automatically detects** log formats from various server types:

✓ **WebLogic** - `[timestamp] [thread] [level] [logger] [] [user:xxx] - message`
✓ **WebSphere** - `[timestamp] [thread] level logger [user] message`
✓ **Tomcat/Log4j** - `timestamp [thread] level logger - message`
✓ **ISO-8601** - `2026-03-12T14:30:45.123+13:00 level message`
✓ **Simple** - `[timestamp] [user] message`

**Just drop your logs into the directory and search** — no configuration needed!

The system will automatically:
- Detect the format on first line read
- Cache the detected format per file for performance
- Handle mixed formats in the same directory
- Extract log levels (ERROR, WARN, INFO) automatically

## Viewing Pattern Fingerprints

After searching, look for the **"🎯 TOP ERROR PATTERNS"** section in the facets sidebar (right side):

This shows:
- Most common log patterns with variables normalized
- Occurrence count for each pattern
- Percentage distribution
- Log level (ERROR/WARN/INFO)

Example:
```
🎯 TOP ERROR PATTERNS

NullPointerException in <CLASS>.<METHOD>        ×245
────────────────────────────────────────────────────
34.2%                                        ERROR

Failed to connect to database at <IP>           ×156
────────────────────────────────────────────────────
21.8%                                        ERROR
```

**Click any pattern** to view all logs matching that pattern!

## Search Tips & Tricks

**Finding errors:**
```
level:ERROR                    → All error-level logs
NullPointerException          → All NPEs in stack traces
OutOfMemoryError              → Memory issues
```

**Finding specific Java code (camelCase aware!):**
```
DataValidator                 → Logs mentioning this class (from any package)
Validator                     → Matches DataValidator, InputValidator, etc.
NullPointer                   → Matches NullPointerException
springframework                → All Spring framework logs
TransactionManager            → Transaction-related logs
Transaction                   → Matches TransactionManager, TransactionService, etc.
```

Note: The search is **camelCase-aware**, so searching "Pointer" will find "NullPointerException"!

**User activity:**
```
user:john.doe                 → All logs from this user
user:admin AND level:ERROR    → Admin's errors
```

**Boolean combinations:**
```
database AND connection       → Both terms present
error OR warning              → Either term present
error NOT timeout             → Errors excluding timeouts
```

**Wildcards:**
```
connect*                      → connection, connecting, connects, etc.
*Exception                    → Any exception type
```

**Exact phrases:**
```
"out of memory"               → Exact phrase match
"stack overflow error"        → Exact phrase in that order
```

## Using Dashboards

### Creating a Dashboard

1. Click the **"Dashboards"** tab
2. Click **"Create New Dashboard"**
3. Enter a name (e.g., "Production Errors")
4. Enter search query (e.g., `level:ERROR`)
5. Select time range (e.g., "Last 24 Hours")
6. Click **"Create Dashboard"**

### Dashboard Features

**Widgets automatically created:**
- **Error Count**: Total errors in time range
- **Log Level Distribution**: Pie chart showing ERROR/WARN/INFO breakdown
- **Timeline by Level**: Stacked bar chart showing errors over time

**Dashboard Actions:**
- **Refresh**: Click refresh icon to update data
- **View Search Results**: Opens search tab with the dashboard's query
- **Edit**: Modify dashboard name, query, or time range
- **Delete**: Remove dashboard

**Auto-Refresh:**
Dashboards with relative time ranges (Last 1h, Last 24h, etc.) automatically update when refreshed.

## Downloading Logs from Remote Sources

### Using the Download Tab

1. Click the **"Download Logs"** tab
2. Enter up to 5 URLs or file paths:
   ```
   https://server.com/logs/app-2026-03-12.log
   /mnt/backup/logs/server.log
   https://logs.example.com/archives/
   ```
3. Click **"Download"**

**What happens:**
- Files are downloaded to your logs directory
- Directories are scanned for all .log files
- All downloaded logs are automatically indexed
- You can search them immediately

**Supported sources:**
- HTTP/HTTPS URLs (individual files)
- HTTP/HTTPS URLs (directories with log files)
- Local file paths
- Local directories (scans recursively for .log files)

## Next Steps

- See [README.md](README.md) for comprehensive search guide
- Explore pattern fingerprints in the facets sidebar (🎯 TOP ERROR PATTERNS)
- Create dashboards for common investigations
- Add more log files to the logs directory (they'll be auto-indexed)
- Log format detection is automatic — no configuration needed!

## Metadata-First Search Configuration (Optional)

LogSearch now supports an **optional metadata-first search architecture** that scales to 100GB+ log volumes.

### When to Enable Metadata-First Search

**Enable if:**
- Log volumes exceed 50GB
- You need sub-second search at massive scale
- You want 90-98% pruning efficiency

**Keep disabled if:**
- Log volumes are under 50GB
- Faster indexing is more important
- Simpler architecture is preferred

### Enable Metadata-First Search

Create or edit `config/application.yml`:

```yaml
log-search:
  # Enable metadata-first search architecture
  chunking:
    enabled: true                # Set to false for standard search
    strategy: "ADAPTIVE"         # ADAPTIVE (recommended) or HOURLY
    adaptive:
      target-size-mb: 200        # Target: 150-250 MB chunks
      min-duration-minutes: 15   # Minimum chunk duration
      max-duration-hours: 6      # Maximum chunk duration

  # Metadata extraction settings
  metadata:
    top-terms-count: 50                    # Top terms per chunk
    enable-package-extraction: true        # Extract Java packages
    enable-exception-extraction: true      # Extract exception types
    bloom-filter:
      enabled: true                        # Enable Bloom filter pruning
      false-positive-rate: 0.01            # 1% false positive rate
      estimated-terms-per-chunk: 10000     # Expected unique terms
```

### Performance Characteristics

**With Metadata-First Search (chunking enabled):**
- **Scales to**: 100GB+ logs
- **Search time**: Sub-second at massive scale
- **Pruning**: 90-98% of chunks eliminated before search
- **Example**: 100 chunks → 2-10 searched
- **Trade-off**: Slightly slower indexing (metadata extraction)

**With Standard Search (chunking disabled):**
- **Scales to**: ~50GB logs
- **Search time**: Increases linearly with data size
- **Indexing**: Faster (no chunking overhead)
- **Best for**: Smaller deployments, simpler architecture

### Testing Your Configuration

```bash
# Start with metadata-first search
java -jar log-search-1.0.0.jar

# Check logs for confirmation:
# "Chunking ENABLED - metadata-first search architecture active"
# "Created 15 chunks for server.log"
# "Using METADATA-FIRST search (chunking enabled)"
# "Metadata index returned 3 candidate chunks in 0.8ms"
```

## Configuration

### Reduce Backend Logging

By default, the application logs INFO-level messages. To reduce console noise:

**Option 1: Use WARN level (recommended)**
```bash
# Only show warnings and errors
java -jar log-search-1.0.0.jar --logging.level.com.lsearch=WARN
```

**Option 2: Use ERROR level (minimal logging)**
```bash
# Only show errors
java -jar log-search-1.0.0.jar --logging.level.com.lsearch=ERROR
```

**Option 3: Edit config/application.yml permanently**
```yaml
logging:
  level:
    com.lsearch.logsearch: WARN  # Change from INFO to WARN
```

### Control Auto-Reindexing Interval

By default, **auto-watch is disabled** - the application only indexes logs at startup. This is recommended for most users to reduce CPU usage.

**Enable auto-watch if needed:**
```bash
# Enable automatic scanning every 60 seconds (default interval)
java -jar log-search-1.0.0.jar --log-search.auto-watch=true

# Or enable with custom interval (e.g., every hour)
java -jar log-search-1.0.0.jar --log-search.auto-watch=true --log-search.watch-interval=3600
```

**Manual indexing (default behavior):**
- Logs are indexed once at startup
- Click **"Re-Index Logs"** button in the UI when you add new log files
- Lower CPU usage when idle

**Option: Increase scan interval (if auto-watch enabled)**
```bash
# Check every 1 hour (3600 seconds)
java -jar log-search-1.0.0.jar --log-search.watch-interval=3600

# Check every 30 minutes (1800 seconds)
java -jar log-search-1.0.0.jar --log-search.watch-interval=1800
```

**Option 3: Edit config/application.yml permanently**
```yaml
log-search:
  auto-watch: false              # Disable auto-scanning
  # OR
  watch-interval: 3600           # Scan every hour (in seconds)
```

**Recommended Settings:**
```bash
# Quiet operation (minimal logging)
java -jar log-search-1.0.0.jar --logging.level.com.lsearch=WARN

# Or even quieter (errors only)
java -jar log-search-1.0.0.jar --logging.level.com.lsearch=ERROR
```

**Default behavior** (no flags needed):
- Auto-watch is **disabled** by default
- Manual indexing via UI "Re-Index Logs" button
- Lower CPU usage when idle
- Add `--logging.level.com.lsearch=WARN` for minimal console output

## Common Commands

```bash
# Standard startup (auto-watch disabled by default)
java -jar log-search-1.0.0.jar

# Quiet mode (minimal logging)
java -jar log-search-1.0.0.jar --logging.level.com.lsearch=WARN

# Custom logs directory
java -jar log-search-1.0.0.jar --logs-dir=/path/to/logs

# Enable auto-watch with hourly scanning
java -jar log-search-1.0.0.jar --log-search.auto-watch=true --log-search.watch-interval=3600

# Index only (doesn't start server)
java -jar log-search-1.0.0.jar index --logs-dir=/path/to/logs

# Custom index location
java -jar log-search-1.0.0.jar --logs-dir=./logs --index-dir=./my-indexes
```

## Troubleshooting

**Port 8080 already in use?**
```bash
# Edit src/main/resources/application.yml and change:
server:
  port: 8081

# Then rebuild
mvn clean package
```

**Can't find Java 8?**
```bash
# Check your Java version
java -version

# Should show version 1.8 (Java 8) or higher
```

**No results when searching?**
- Make sure date range includes your log file dates
- Try simpler search terms (e.g., `error` instead of `"connection error"`)
- Check logs are indexed: Click "Re-Index Logs" button in UI
- File pattern `.*\.log` matches ANY .log file (very flexible)
- Look at console output for indexing errors

**Can't find Java class names?**
- ✓ Search works! The CodeAnalyzer splits `com.example.DataValidator` automatically
- ✓ Search just: `DataValidator` (not the full package path)
- ✓ Or search: `example`, `validation`, or any component

## Need Help?

Check the full [README.md](README.md) for detailed documentation.

# Quick Start Guide

Get up and running with Log Search in 3 minutes.

## Prerequisites

- Java 8 or higher installed
- Maven installed

## Steps

### 1. Build the application

```bash
cd /Users/manish/Java\ Projects/log_search
mvn clean package
```

This takes about 30-60 seconds and creates: `target/log-search-1.0.0.jar`

### 2. Try it with sample data

The project includes sample logs in the `logs/` directory. Start the application:

```bash
java -jar target/log-search-1.0.0.jar start --logs-dir=./logs
```

You should see:
```
=================================================================
Log Search is running!
Web UI: http://localhost:8080
API: http://localhost:8080/api/search
=================================================================
```

Your browser will automatically open to http://localhost:8080

### 3. Search the sample logs

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

## Expected Log Format

Your logs should look like:
```
[2026-03-12T14:30:45.123+13:00] [username] Log message here
```

Pattern: `[ISO DateTime] [User] Message`

## Search Tips & Tricks

**Finding errors:**
```
level:ERROR                    → All error-level logs
NullPointerException          → All NPEs in stack traces
OutOfMemoryError              → Memory issues
```

**Finding specific Java code:**
```
DataValidator                 → Logs mentioning this class (from any package)
springframework                → All Spring framework logs
TransactionManager            → Transaction-related logs
```

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

## Next Steps

- See [README.md](README.md) for comprehensive search guide with 40+ examples
- Customize log format in `config/application.yml` (no rebuild needed!)
- Add more log files to the logs directory (they'll be auto-indexed)

## Common Commands

```bash
# Just start (indexes and runs server)
java -jar target/log-search-1.0.0.jar

# Explicit start with custom logs directory
java -jar target/log-search-1.0.0.jar start --logs-dir=/path/to/logs

# Index only (doesn't start server)
java -jar target/log-search-1.0.0.jar index --logs-dir=/path/to/logs

# Start with custom index location
java -jar target/log-search-1.0.0.jar start --logs-dir=./logs --index-dir=./my-indexes
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

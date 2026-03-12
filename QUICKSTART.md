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
   - Leave search empty and click "Search" to see all logs
   - Search for: `error`
   - Search for: `john.doe`
   - Search for: `backup AND completed`
   - Search for: `"database connection"`

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

## Next Steps

- See [README.md](README.md) for full documentation
- Customize log format in `src/main/resources/application.yml`
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
- Check log files match the pattern: `server-YYYYMMDD.log`
- Look at console output for indexing errors

## Need Help?

Check the full [README.md](README.md) for detailed documentation.

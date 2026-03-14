# External Configuration

This directory contains external configuration files that override the defaults bundled in the JAR.

## How It Works

Spring Boot automatically looks for `application.yml` in these locations (highest priority first):

1. **Command line**: `--spring.config.location=file:/path/to/config.yml`
2. **Current directory**: `./application.yml`
3. **Config subdirectory**: `./config/application.yml` ← **This file!**
4. **Inside JAR**: Defaults (lowest priority)

## Quick Start

The included `application.yml` is pre-configured with sensible defaults and comments explaining each setting.

**To customize for your environment:**

1. Edit `config/application.yml`
2. Run the application using `./start.sh` (or `start.bat` on Windows)
3. Changes take effect immediately - no rebuild needed!

## Smart Auto-Detection (Recommended)

**LogSearch now automatically detects log formats!**

The system includes built-in support for:
- WebLogic
- WebSphere
- Tomcat/Log4j
- ISO-8601 timestamps
- Custom application formats

**In most cases, you don't need to configure log patterns at all.**

Simply drop your logs into the directory and the system will:
- Auto-detect the format on first read
- Cache the detected format per file for performance
- Handle mixed formats in the same directory
- Extract log levels (ERROR, WARN, INFO) automatically

## Example Configurations (Advanced/Optional)

You can still manually configure patterns if needed, but auto-detection works for most cases.

### For Different File Name Patterns

**Apache/Nginx Access Logs:**
```yaml
log-search:
  file-pattern: "access\\.log\\.\\d{4}-\\d{2}-\\d{2}"
  filename-date-pattern: "access\\.log\\.(\\d{4})-(\\d{2})-(\\d{2})"
  # Log format will be auto-detected
```

**Standard Java Logs:**
```yaml
log-search:
  file-pattern: "application-\\d{4}-\\d{2}-\\d{2}\\.log"
  filename-date-pattern: "application-(\\d{4})-(\\d{2})-(\\d{2})\\.log"
  # Log format will be auto-detected
```

**Note:** `log-line-pattern` and `log-datetime-format` are now optional thanks to smart auto-detection!

### For Different Timezones

```yaml
log-search:
  timezone: "America/New_York"    # US Eastern
  # timezone: "Europe/London"     # UK
  # timezone: "Asia/Tokyo"        # Japan
  # timezone: "UTC"               # Universal Time
```

### For Large Log Volumes

```yaml
log-search:
  retention-days: 7               # Keep only last 7 days
  watch-interval: 3600            # Check for new files every hour (instead of 60s)
  # OR
  auto-watch: false               # Disable auto-scanning entirely (manual indexing via UI)
```

### For Quiet Operation (Recommended)

Reduce console logging and disable background scanning for typical use:

```yaml
log-search:
  auto-watch: false               # Disable automatic file scanning

logging:
  level:
    com.lsearch.logsearch: WARN   # Only show warnings and errors
```

**Command line equivalent:**
```bash
java -jar log-search-1.0.0.jar \
  --logging.level.com.lsearch=WARN \
  --log-search.auto-watch=false
```

This configuration:
- Minimizes console output (only warnings/errors shown)
- No background CPU usage (index manually when you add logs)
- Use "Re-Index Logs" button in UI when needed

## Distribution

When distributing the application to other developers:

1. **Include**:
   - `log-search-1.0.0.jar`
   - `config/application.yml` (sample configuration)
   - `start.sh` / `start.bat` (startup scripts)

2. **Users can customize** `config/application.yml` without needing:
   - Java development tools
   - Source code
   - Rebuild/recompile

3. **Run with**: `./start.sh start --logs-dir=/path/to/logs`

## Environment Variables

You can also override settings using environment variables:

```bash
# Override logs directory
export LOGS_DIR=/var/log/myapp
./start.sh start

# Override index directory
export INDEX_DIR=/data/search-indexes
./start.sh start
```

## Command Line Override

Override any setting from the command line:

```bash
# Use completely different config file
./start.sh start --spring.config.location=file:/etc/logsearch/custom.yml

# Override specific properties
./start.sh start --log-search.retention-days=90 --log-search.watch-interval=30
```

## Validation

After changing configuration, verify settings are loaded correctly:

```bash
# Check logs on startup for:
# "Logs directory: /your/configured/path"
# "Index directory: /your/configured/path"

# Or check via API:
curl http://localhost:8080/api/status
```

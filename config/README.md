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

## Example Configurations

### For Different Log Formats

**Apache/Nginx Access Logs:**
```yaml
log-search:
  file-pattern: "access\\.log\\.\\d{4}-\\d{2}-\\d{2}"
  filename-date-pattern: "access\\.log\\.(\\d{4})-(\\d{2})-(\\d{2})"
  log-datetime-format: "dd/MMM/yyyy:HH:mm:ss Z"
```

**Standard Java Logs:**
```yaml
log-search:
  file-pattern: "application-\\d{4}-\\d{2}-\\d{2}\\.log"
  filename-date-pattern: "application-(\\d{4})-(\\d{2})-(\\d{2})\\.log"
  log-datetime-format: "yyyy-MM-dd HH:mm:ss"
```

**Syslog Format:**
```yaml
log-search:
  file-pattern: "syslog-\\d{8}"
  filename-date-pattern: "syslog-(\\d{4})(\\d{2})(\\d{2})"
  log-datetime-format: "MMM dd HH:mm:ss"
```

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
  watch-interval: 300             # Check for new files every 5 minutes
```

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

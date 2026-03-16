# Context View Configuration

The context view slider settings are now externalized and can be configured without rebuilding the JAR.

## Configuration Location

Edit `config/application.yml`:

```yaml
log-search:
  context:
    default-lines: 500    # Default number of lines shown when opening context view
    min-lines: 10         # Minimum slider value (left side)
    max-lines: 1000       # Maximum slider value (right side)
    step: 10              # Slider increment step
```

## How It Works

1. **Backend Configuration**: Settings are stored in `application.yml` and loaded by Spring Boot
2. **Config Endpoint**: `/api/config/context` exposes the settings as JSON
3. **Frontend Integration**: On page load, the UI fetches configuration and updates the slider

## Usage Examples

### Example 1: Increase Maximum Lines for Large Log Files

```yaml
log-search:
  context:
    default-lines: 1000
    min-lines: 50
    max-lines: 5000      # ← Allow viewing up to 5000 lines of context
    step: 50
```

**Effect**: Context view slider can now go up to 5000 lines instead of 1000.

### Example 2: Environment Variable Override

You can also override via environment variables without editing the file:

```bash
export LOG_SEARCH_CONTEXT_MAX_LINES=5000
export LOG_SEARCH_CONTEXT_DEFAULT_LINES=1000
java -jar log-search-1.0.0.jar
```

Or using Spring Boot property format:

```bash
java -jar log-search-1.0.0.jar \
  --log-search.context.max-lines=5000 \
  --log-search.context.default-lines=1000
```

### Example 3: Production Settings (Large Logs)

For production environments with very large log files:

```yaml
log-search:
  context:
    default-lines: 2000    # Start with more context
    min-lines: 100         # Don't allow too few lines
    max-lines: 10000       # Allow viewing 10K lines for deep debugging
    step: 100              # Larger steps for faster adjustment
```

### Example 4: Development Settings (Small Logs)

For development with smaller log files:

```yaml
log-search:
  context:
    default-lines: 200
    min-lines: 10
    max-lines: 500
    step: 10
```

## API Endpoint

**GET** `/api/config/context`

**Response:**
```json
{
  "defaultLines": 500,
  "minLines": 10,
  "maxLines": 1000,
  "step": 10
}
```

## Benefits

✅ **No Rebuild Required**: Change settings by editing `application.yml`
✅ **Per-Environment Config**: Different settings for dev/test/prod
✅ **Runtime Override**: Use environment variables for quick changes
✅ **Centralized**: All configuration in one place
✅ **Type-Safe**: Spring Boot validates values on startup

## Validation

Spring Boot will fail to start if invalid values are provided:
- Negative numbers are rejected
- Non-integer values are rejected
- Missing values fall back to defaults (500, 10, 1000, 10)

## Default Values

If the configuration section is missing or the endpoint fails, the UI falls back to:
- Default: 500 lines
- Min: 10 lines
- Max: 1000 lines
- Step: 10 lines

These are the same values hardcoded in previous versions for backward compatibility.

# Distribution Guide

How to package and distribute the Log Search application to developers.

## What to Distribute

Create a distribution package containing:

```
log-search/
├── log-search-1.0.0.jar          # The application
├── start.sh                       # Linux/Mac startup script
├── start.bat                      # Windows startup script
├── config/
│   ├── application.yml            # Configurable settings
│   └── README.md                  # Configuration guide
├── README.md                      # Main documentation
└── QUICKSTART.md                  # Getting started guide
```

## Creating a Distribution Package

### Option 1: Simple ZIP

```bash
# From project root
mkdir -p dist/log-search
cp target/log-search-1.0.0.jar dist/log-search/
cp start.sh start.bat dist/log-search/
cp -r config dist/log-search/
cp README.md QUICKSTART.md dist/log-search/

# Create archive
cd dist
zip -r log-search-1.0.0.zip log-search/
# Or: tar -czf log-search-1.0.0.tar.gz log-search/
```

### Option 2: Maven Assembly (Automated)

Add to `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <artifactId>maven-assembly-plugin</artifactId>
      <configuration>
        <descriptors>
          <descriptor>src/assembly/dist.xml</descriptor>
        </descriptors>
      </configuration>
      <executions>
        <execution>
          <phase>package</phase>
          <goals><goal>single</goal></goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Then run: `mvn clean package` → creates `target/log-search-1.0.0-dist.zip`

## Developer Instructions

Include these instructions with your distribution:

### For Recipients

**1. Extract the package**
```bash
unzip log-search-1.0.0.zip
cd log-search
```

**2. (Optional) Customize configuration**
```bash
# Edit config/application.yml to match your log format
nano config/application.yml
```

**3. Run the application**

**Linux/Mac:**
```bash
./start.sh start --logs-dir=/path/to/your/logs
```

**Windows:**
```cmd
start.bat start --logs-dir=C:\path\to\logs
```

**4. Access the UI**
- Open: http://localhost:8080
- Search your logs!

## Configuration Examples for Common Scenarios

### Scenario 1: Different Log File Naming

**User has files named: `app-2026-03-12.log`**

Edit `config/application.yml`:
```yaml
log-search:
  file-pattern: "app-\\d{4}-\\d{2}-\\d{2}\\.log"
  filename-date-pattern: "app-(\\d{4})-(\\d{2})-(\\d{2})\\.log"
```

### Scenario 2: Different Log Format

**User has logs like: `2026-03-12 14:30:45 [INFO] Message`**

Edit `config/application.yml`:
```yaml
log-search:
  log-datetime-format: "yyyy-MM-dd HH:mm:ss"
```

### Scenario 3: Different Timezone

**User is in New York (EST)**

Edit `config/application.yml`:
```yaml
log-search:
  timezone: "America/New_York"
```

### Scenario 4: Different Log Location

**User wants to use `/var/log/myapp`**

```bash
# Option 1: Command line
./start.sh start --logs-dir=/var/log/myapp

# Option 2: Environment variable
export LOGS_DIR=/var/log/myapp
./start.sh start

# Option 3: Edit config/application.yml
log-search:
  logs-dir: "/var/log/myapp"
```

## Advanced: Completely Custom Config

Users can use their own config file:

```bash
./start.sh start --spring.config.location=file:/etc/myapp/custom-config.yml
```

## Troubleshooting for Recipients

**Application won't start?**
- Check Java version: `java -version` (need Java 8+)
- Check JAR exists: `ls -l log-search-1.0.0.jar`

**No logs showing up?**
- Check file pattern matches your files
- Check date pattern extracts dates correctly
- Look at startup logs for "Indexing log file: ..."

**Search returns nothing?**
- Verify date range covers your logs
- Check timezone setting matches log timestamps
- Try searching without any query (shows all logs)

**Need help?**
- Check `config/README.md` for configuration examples
- Check `README.md` for full documentation
- Contact your platform team

## Version Management

When releasing updates:

1. **Update version in `pom.xml`**:
   ```xml
   <version>1.1.0</version>
   ```

2. **Rebuild**: `mvn clean package`

3. **Create new distribution**: Include changelog

4. **Distribute**: Send to users with upgrade instructions

## Migration Guide (for updates)

**Upgrading from 1.0.0 to 1.1.0:**

1. Stop old version
2. Replace JAR file only
3. Keep existing `config/application.yml` (settings compatible)
4. Start new version
5. Existing indexes are reused automatically

## Security Considerations

- **JARfiles are executable code** - distribute through secure channels
- Users can customize config - **review settings** if concerned
- Application runs on **localhost only** by default (not exposed to network)
- To expose to network, users can edit:
  ```yaml
  server:
    address: 0.0.0.0  # WARNING: Exposes to network
  ```

## Support

Provide users with:
1. This documentation
2. Example config files for common scenarios
3. Contact for platform team
4. Link to issue tracker (if applicable)

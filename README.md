# LogSearch

**Fast, lightweight search for archived application logs.**

LogSearch is a developer-focused log investigation tool designed for teams that cannot retain large volumes of logs in expensive observability platforms such as Splunk.

Many organizations must periodically delete logs from Splunk or other centralized systems due to license or storage constraints. When incidents occur later, developers often need to manually download large log files and search them line-by-line using tools like `grep` or text editors.

LogSearch solves this problem by allowing developers to **index and search historical logs locally with high performance using Apache Lucene**.

It provides fast full-text search, stack trace awareness, date filtering, analytics dashboards, and contextual log viewing — all packaged in a **single lightweight application with no external dependencies**.

---

# Problem This Solves

In many engineering environments:

• Splunk or similar tools retain logs for only a short period due to license costs
• Older logs are archived to files or object storage
• Developers must manually download and search logs
• Investigations become slow and inefficient

Typical developer workflow today:

```
Download logs
Open huge files
Run grep commands
Scroll through stack traces
Repeat across multiple files
```

This process can take **hours for a single investigation**.

LogSearch provides a faster alternative.

```
Archived logs
      │
      ▼
Index using Lucene
      │
      ▼
Search instantly
      │
      ▼
View stack traces and context
```

Instead of searching raw files, developers query a **high-performance index**.

---

# Key Benefits

### Fast Investigation

Lucene-based indexing enables near-instant search across large log files.

### Historical Log Access

Investigate incidents even after logs have been removed from Splunk.

### Developer Friendly

Designed specifically for engineers investigating application logs.

### Zero Infrastructure

Runs as a **single Java application** with no external services required.

### Lightweight Alternative

Keeps Splunk for operational monitoring while enabling low-cost historical search.

---

# Core Features

## Full-Text Log Search

Search across indexed logs using powerful text queries.

Examples:

```
NullPointerException
payment failed
timeout AND retry
"database connection"
```

---

## Date and Time Filtering

Restrict searches to a specific time window.

Example:

```
2026-03-12 10:00 → 2026-03-12 12:00
```

This significantly reduces search time when working with large datasets.

---

## Java Stack Trace Awareness

LogSearch understands multi-line stack traces.

Instead of breaking them across lines, stack traces are stored as **single searchable events**.

Example:

```
java.lang.NullPointerException
    at com.company.service.PaymentService.process()
    at com.company.controller.PaymentController.execute()
```

---

## Context View

See log lines before and after the event.

Example:

```
Previous logs
Target log event
Following logs
```

This makes debugging faster by showing surrounding activity.

---

## Incremental Indexing

Only new logs need to be indexed.

This avoids reprocessing entire log archives.

---

## Full Reindex

If needed, the entire index can be rebuilt.

Useful when log formats change or new parsing rules are added.

---

## Log Analytics

LogSearch can generate quick summaries such as:

• error counts
• warning counts
• log frequency over time

This helps identify spikes and unusual patterns.

---

## Pattern Fingerprinting

Automatically identifies and groups similar log messages by extracting normalized patterns.

Example:

```
Original logs:
  ERROR: Failed to connect to database at 192.168.1.1
  ERROR: Failed to connect to database at 192.168.1.2
  ERROR: Failed to connect to database at 192.168.1.3

Pattern detected:
  ERROR: Failed to connect to database at <IP>  (×3 occurrences)
```

Benefits:
• Quickly identify the most common error patterns
• See percentage distribution of error types
• Click a pattern to view all matching logs
• Spot recurring issues across different contexts

The UI displays "🎯 TOP ERROR PATTERNS" showing:
• Pattern text with variables normalized
• Occurrence count
• Percentage of total logs
• Log level (ERROR, WARN, INFO)

---

## Smart Format Detection

Automatically detects log formats from various server types with **zero configuration**.

Supported formats:
• WebLogic
• WebSphere
• Tomcat / Log4j
• ISO-8601 timestamps
• Custom application formats

Benefits:
• No manual configuration required
• Handles mixed log formats in same directory
• Automatically adapts when log format changes
• Per-file format caching for performance

Simply drop logs into the directory and search — format detection happens automatically.

---

## Dashboard View

Provides a quick overview of log activity including:

• log volume
• error distribution
• time-based patterns

---

## Saved Searches

Common queries can be saved and reused.

Example:

```
payment failures
authentication errors
database timeouts
```

---

## Bulk Log Download

Search results can be downloaded for offline analysis or sharing with team members.

---

# Architecture Overview

```
              Log Files
                  │
                  ▼
          Log Parsing Engine
                  │
                  ▼
           Apache Lucene
               Index
                  │
                  ▼
             Search API
                  │
                  ▼
         Developer Interface
```

LogSearch converts raw logs into structured searchable events stored in Lucene.

---

# Technology Stack

| Component     | Technology        |
| ------------- | ----------------- |
| Language      | Java 8+           |
| Search Engine | Apache Lucene 8.11.2 |
| Packaging     | Standalone JAR    |
| Storage       | Local file system |
| Indexing      | Incremental       |
| Concurrency   | Parallel search   |

---

# Performance

LogSearch is designed for **enterprise-scale workloads**:

### Throughput
- **1-2 GB/day**: Optimized for small teams
- **5-10 GB/day**: Production-ready with default settings
- **10-20 GB/day**: Supported with tuned heap configuration

### Search Speed
- **Single day**: 100-300ms
- **7 days**: 300-500ms (parallel search)
- **30 days**: 1-3 seconds

### Parallel Search Architecture
Searches across multiple day-based indexes run **concurrently** using thread pools, providing 3-5x performance improvement over sequential search.

### JVM Auto-Configuration
Heap size is automatically configured from `application.yml`:
```yaml
jvm:
  heap-min: 2g    # Default for small workloads
  heap-max: 4g    # Increase for enterprise scale
```

For 10 GB/day workloads, recommended settings:
```yaml
jvm:
  heap-min: 8g
  heap-max: 12g
```

---

# Typical Workflow

### Step 1

Download archived logs.

Example:

```
server-20260312.log
server-20260313.log
server-20260314.log
```

---

### Step 2

Index the logs using the startup script (with auto-configured JVM settings).

```bash
./start.sh index
```

Or run directly:
```bash
java -jar logsearch.jar index
```

---

### Step 3

Start the web server.

```bash
./start.sh
```

This automatically:
- Reads JVM heap settings from `config/application.yml`
- Applies optimal garbage collection settings
- Starts the application with parallel search enabled

---

### Step 4

Search via the web UI at `http://localhost:8080` or API:

```bash
curl "http://localhost:8080/api/search?query=NullPointerException&startTime=..."
```

---

### Step 5

Investigate results with context and stack traces through the web UI.

---

# Configuration

LogSearch uses externalized configuration in `config/application.yml`.

### JVM Configuration

Control heap size and GC settings:

```yaml
jvm:
  heap-min: 2g     # Minimum heap
  heap-max: 4g     # Maximum heap
  extra-opts: -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

**Recommended settings by workload:**

| Daily Log Volume | heap-min | heap-max |
|-----------------|----------|----------|
| 1-2 GB          | 2g       | 4g       |
| 5 GB            | 4g       | 6g       |
| 10 GB           | 8g       | 12g      |
| 20+ GB          | 16g      | 24g      |

### Environment Variable Override

```bash
export JVM_HEAP_MAX=8g
./start.sh
```

### Log Configuration

```yaml
log-search:
  logs-dir: ./logs
  index-dir: ./.log-search/indexes
  retention-days: 30
  auto-watch: true
```

---

# Example Use Cases

### Production Incident Investigation

Search historical logs after they have been removed from Splunk.

---

### Debugging Failed Deployments

Quickly locate errors across multiple log files.

---

### Root Cause Analysis

Find the first occurrence of a failure.

---

### Performance Troubleshooting

Search for slow queries or timeout patterns.

---

# Why Not Just Use Splunk?

Splunk is powerful but expensive for long-term retention.

Many organizations keep only **7–30 days of logs** in Splunk.

Older logs are stored as files.

LogSearch allows teams to:

• keep Splunk costs under control
• retain access to historical logs
• enable developers to investigate issues independently

---

# Design Goals

LogSearch was designed with these principles:

• **Simple deployment**
• **High performance search**
• **Developer-focused workflow**
• **Minimal infrastructure requirements**

---

# When to Use LogSearch

LogSearch works best when:

• logs are archived outside Splunk
• developers need to investigate historical incidents
• teams want a lightweight search tool

---

# When Not to Use LogSearch

LogSearch is not intended to replace:

• Splunk
• ELK stack
• distributed observability platforms

Instead, it complements them by providing **low-cost historical search**.

---

# Future Enhancements

Planned improvements include:

• distributed indexing
• cloud storage integration
• faster indexing pipelines
• structured log parsing
• alert pattern detection

---

# Contributing

Contributions are welcome.

Possible areas to contribute:

• log parsers
• indexing improvements
• UI enhancements
• performance tuning

---

# License

This project is licensed under the MIT License.

---

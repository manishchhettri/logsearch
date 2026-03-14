# LogSearch – Feature Overview

This document describes the major capabilities of **LogSearch**, a lightweight log investigation platform designed to enable fast search and analysis of archived log files.

LogSearch focuses on providing developers with efficient tools for investigating application behaviour after logs have been removed from centralized observability platforms such as Splunk.

---

# 1. High Performance Log Search

LogSearch uses **Apache Lucene 8.11.2** with **parallel search architecture** to provide fast full-text search across large volumes of log data.

### Search Performance

Real-world benchmarks:

| Time Range | Search Time | Notes |
|------------|-------------|-------|
| 1 day      | 100-300ms   | Single index search |
| 7 days     | 300-500ms   | Parallel across 7 indexes |
| 30 days    | 1-3 seconds | Concurrent search of 30 indexes |

### Parallel Search Architecture

Day-based indexes are searched **concurrently** using thread pools:
- Automatically scales with CPU cores
- 3-5x faster than sequential search
- Thread-safe design with proper resource management

Example: Searching 7 days of logs opens and queries 7 indexes **simultaneously** instead of one-by-one.

### Supported Search Capabilities

• keyword search
• phrase search
• boolean queries (AND, OR, NOT)
• partial matches and wildcards
• field-specific queries (level:ERROR, user:admin)
• regex patterns

Example queries:

```
NullPointerException
database timeout
payment AND failed
"connection refused"
level:ERROR AND message:OutOfMemoryError
```

---

# 2. Intelligent Log Indexing

LogSearch converts raw log files into optimized search indexes.

During indexing:

• log timestamps are extracted
• multi-line entries are merged
• stack traces are preserved
• log metadata is stored for fast filtering

This enables accurate search results while maintaining the context of each log event.

---

# 3. Multi-Line Log Event Handling

Many application logs contain multi-line entries such as Java stack traces.

LogSearch detects and groups these entries into a **single searchable event** rather than treating each line independently.

Example:

```
ERROR PaymentService failed
java.lang.NullPointerException
    at com.company.payment.Service.process(Service.java:45)
    at com.company.controller.PaymentController.execute()
```

This entire block becomes **one indexed log event**.

---

# 4. Time-Based Filtering

Logs can be filtered by date and time to narrow search results.

Supported capabilities:

• exact time range filtering
• day-based filtering
• quick time window selection

Example:

```
Search logs between:
2026-03-12 10:00
and
2026-03-12 12:00
```

Time filtering significantly improves search performance when analyzing large datasets.

---

# 5. Contextual Log Viewing

When a log event is selected, LogSearch provides surrounding log context.

This allows developers to see what occurred immediately before and after the event.

Example view:

```
Previous log entries
Target log entry
Following log entries
```

This is particularly useful when investigating failures or system behaviour leading up to an error.

---

# 6. Incremental Log Indexing

LogSearch supports incremental indexing of new log files.

Only new or updated logs are processed, avoiding unnecessary re-indexing of existing data.

Benefits:

• faster indexing
• reduced CPU usage
• efficient log ingestion

---

# 7. Full Index Rebuild

When necessary, LogSearch can rebuild the entire index from scratch.

This may be useful when:

• log formats change
• parsing rules are updated
• corrupted indexes need recovery

---

# 8. Log Analytics and Aggregation

LogSearch can produce simple analytics from indexed logs, including:

• error frequency
• warning frequency
• log distribution over time
• event counts by type

These summaries provide quick insight into system behaviour.

---

# 9. Dashboard View

The dashboard provides a high-level overview of system log activity.

Typical metrics include:

• total log events
• error distribution
• time-based event trends

This helps teams quickly identify unusual activity patterns.

---

# 10. Saved Searches

Developers can save commonly used queries for quick reuse.

Examples:

```
Database connection errors
Authentication failures
Payment processing issues
```

Saved searches simplify repeated investigations.

---

# 11. Bulk Result Export

Search results can be exported for offline analysis or collaboration.

Supported capabilities:

• export matched log events
• export filtered log sets
• share logs with other team members

---

# 12. Enterprise-Scale Log Support

LogSearch is designed to handle **enterprise-scale log volumes**.

### Supported Workloads

| Daily Volume | Index Size (30 days) | Search Performance |
|--------------|---------------------|-------------------|
| 1-2 GB/day   | 12-24 GB           | < 500ms (7 days)  |
| 5 GB/day     | 30-60 GB           | < 1s (7 days)     |
| 10 GB/day    | 60-120 GB          | 1-2s (7 days)     |
| 20 GB/day    | 120-240 GB         | 2-4s (7 days)     |

### Optimizations for Large Workloads

• **Day-based index partitioning** - only relevant dates are searched
• **Parallel search execution** - concurrent queries across indexes
• **Configurable heap sizing** - auto-configured from application.yml
• **LongPoint range queries** - efficient timestamp filtering
• **Index caching** - Lucene segment caching reduces disk I/O

### JVM Tuning

LogSearch automatically reads heap configuration from `config/application.yml`:

```yaml
jvm:
  heap-min: 8g    # For 10 GB/day workload
  heap-max: 12g
  extra-opts: -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

The `start.sh` and `start.bat` scripts parse these settings and apply them automatically.

---

# 13. Lightweight Deployment

LogSearch runs as a **single standalone Java application** with **auto-configured JVM settings**.

No additional services are required.

Deployment characteristics:

• no database required
• no external search cluster
• minimal configuration
• portable execution
• automatic heap sizing from config
• optimized GC settings

### Quick Start

```bash
# Start with default configuration (2-4 GB heap)
./start.sh

# Or with custom heap size
export JVM_HEAP_MAX=8g
./start.sh

# Or edit config/application.yml and run
./start.sh
```

### Hardware Requirements

| Log Volume | Recommended RAM | CPU Cores | Storage (30 days) |
|------------|----------------|-----------|-------------------|
| 1-2 GB/day | 8 GB           | 4         | 50 GB             |
| 5 GB/day   | 16 GB          | 4-8       | 150 GB            |
| 10 GB/day  | 24 GB          | 8+        | 300 GB            |

**SSD storage strongly recommended** for optimal search performance (6x faster than HDD).

---

# 14. Flexible Log Source Support

LogSearch can index logs from multiple sources including:

• application server logs
• microservice logs
• archived log directories
• downloaded production logs

---

# 15. Developer-Oriented Workflow

LogSearch is designed to support the typical developer investigation workflow:

```
Download archived logs
Index logs locally
Search quickly
View stack traces
Investigate root cause
```

This workflow dramatically reduces the time required to analyze historical incidents.

---

# 16. Cost-Effective Historical Log Investigation

LogSearch enables teams to maintain access to historical logs without the high cost of long-term centralized log retention.

This allows organizations to:

• reduce Splunk storage costs
• maintain searchable log archives
• empower developers to perform independent investigations

---

# Summary

LogSearch provides a lightweight yet powerful platform for searching archived logs.

Key strengths include:

• fast Lucene-based search
• stack trace awareness
• time-based filtering
• contextual event viewing
• minimal infrastructure requirements

It complements centralized observability platforms by enabling **efficient investigation of historical log data**.

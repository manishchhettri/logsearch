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
| Language      | Java              |
| Search Engine | Apache Lucene     |
| Packaging     | Standalone JAR    |
| Storage       | Local file system |
| Indexing      | Incremental       |

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

Index the logs.

```
java -jar logsearch.jar index
```

---

### Step 3

Run searches.

```
java -jar logsearch.jar search "NullPointerException"
```

---

### Step 4

Investigate results with context and stack traces.

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

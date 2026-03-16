# Windows Search Issue - Diagnostic Guide

## Problem
Search returns no results on Windows when using any search criteria, but empty search returns data correctly.

## Root Cause Hypothesis
The issue likely started after adding:
1. Adaptive Pattern Builder
2. Log level detection changes (detecting ERROR, WARN, INFO in square brackets)

## Diagnostic Logging Added

### 1. Search Query Diagnostics (`LogSearchService.java`)
**What it shows:**
- Operating system and file separator
- Original query vs preprocessed query
- Lucene query syntax after parsing
- Final query sent to each date index
- Number of hits returned per index

**What to look for:**
```
=== SEARCH DIAGNOSTICS START ===
OS: Windows 10, File.separator: \
Original query: 'level:ERROR'
Query unchanged after preprocessing
Lucene query parsed successfully: level:error
Final Lucene query for date 2026-03-13: +level:error +timestamp:[...]
Date index 2026-03-13 returned 0 hits
```

**Red flags:**
- "CRITICAL: Failed to parse query" - Query parsing exception
- Query changes unexpectedly after preprocessing
- All indexes return 0 hits despite data being present

---

### 2. Pattern Detection Diagnostics (`LogPatternDetector.java`)
**What it shows:**
- Which pattern (WebLogic, Log4j, Adaptive) matched the log format
- Extracted fields: level, user, thread, logger
- Pattern match failures with sample line

**What to look for:**
```
Auto-detected log format 'WebLogic' for file: logs/server.log
  Extracted: level=INFO, user=john.doe, thread=ExecuteThread-1
```

**Red flags:**
- "Cached pattern 'X' failed for file" - Pattern stopped matching
- "No hardcoded pattern matched" - May trigger adaptive builder
- Extracted level is NULL or empty

---

### 3. Adaptive Pattern Builder Diagnostics (`AdaptivePatternBuilder.java`)
**What it shows:**
- Number of bracketed fields found
- Log level detection (uppercase vs mixed case)
- Generated regex pattern
- Validation success rate

**What to look for:**
```
Attempting adaptive pattern detection for file: logs/server.log (3 sample lines)
Found 6 bracketed fields to classify
  Bracket 2: LOG LEVEL (uppercase) -> 'INFO ' detected as INFO
Built adaptive pattern - Regex: Adaptive-timestamp-thread-level-logger-user-message
  Success rate: 100.0%
```

**Red flags:**
- "No brackets found in line" - Wrong file format
- Level detection fails (no "LOG LEVEL" message)
- Validation failed: <70% success rate
- Pattern works on Mac but creates different regex on Windows

---

### 4. Document Indexing Diagnostics (`LuceneIndexService.java`)
**What it shows:**
- First 3 documents indexed with all field values
- Warning if level field is NULL or empty

**What to look for:**
```
INDEXED DOCUMENT #1:
  timestamp: 2026-03-13T14:30:00+13:00
  level: 'INFO'
  user: 'john.doe'
  thread: 'ExecuteThread-1'
  logger: 'com.example.Service'
  message: 'Application started successfully'
  sourceFile: logs/server-20260313.log
```

**Red flags:**
- level: 'null' or level: ''
- Multiple warnings: "Entry with NULL or EMPTY level field"
- Fields differ between Mac and Windows indexing

---

## Testing Steps on Windows

### Step 1: Clean Re-index
```batch
# Stop service
stop.bat

# Delete existing indexes
rd /s /q .log-search\indexes

# Start service
start.bat
```

### Step 2: Check Indexing Logs
Look for:
- Pattern detection messages
- "INDEXED DOCUMENT #1, #2, #3" with field values
- Any warnings about NULL level fields

**Save these logs!**

### Step 3: Test Empty Search (Baseline)
```
curl "http://localhost:8080/api/search?startTime=2026-03-01T00:00:00%2B13:00&endTime=2026-03-31T23:59:59%2B13:00&pageSize=10"
```

**Expected:** Returns results ✓

### Step 4: Test Level Search (Problem Case)
```
curl "http://localhost:8080/api/search?query=level:ERROR&startTime=2026-03-01T00:00:00%2B13:00&endTime=2026-03-31T23:59:59%2B13:00&pageSize=10"
```

**Look for in logs:**
- "Lucene query parsed successfully: level:error"
- "Final Lucene query..."
- "Date index X returned Y hits"

### Step 5: Test Simple Text Search
```
curl "http://localhost:8080/api/search?query=error&startTime=2026-03-01T00:00:00%2B13:00&endTime=2026-03-31T23:59:59%2B13:00&pageSize=10"
```

Compare results with level:ERROR search.

---

## Common Issues & Solutions

### Issue 1: "CRITICAL: Failed to parse query"
**Cause:** Lucene query parser failing (special characters, Windows paths with `\`)

**Solution:** Check if query contains backslashes or special characters. The preprocessQuery should handle this, but may need adjustment.

---

### Issue 2: level field is NULL during indexing
**Cause:** Adaptive pattern builder or pattern detector failing to extract level field on Windows

**Solution:**
1. Check if log format differs (CRLF vs LF line endings)
2. Compare "Bracket X: LOG LEVEL" messages between Mac and Windows
3. May need to fix regex patterns in AdaptivePatternBuilder

---

### Issue 3: Query parses but returns 0 hits
**Cause:** Field indexed with different case or format than searched

**Possible causes:**
- Level indexed as "INFO " (with trailing space) but searched as "INFO"
- Level indexed as "Info" but searched as "ERROR"
- StandardAnalyzer tokenization differs by locale

**Solution:** Compare "INDEXED DOCUMENT" output with search query. Check for extra spaces or case differences.

---

### Issue 4: Adaptive pattern creates different regex on Windows
**Cause:** Regex behavior differs by JVM locale or line ending handling

**Solution:** Force use of hardcoded patterns instead of adaptive:
```yaml
# In application.yml
log-search:
  log-line-pattern: "^\\[([^\\]]+)\\]\\s+(.+?)\\s+\\[([^\\]]+)\\]..."
```

---

## Key Diagnostic Output to Capture

When testing on Windows, capture these log sections:

1. **Startup:** Pattern library initialization
2. **Indexing:** First 3 "INDEXED DOCUMENT" messages
3. **Search:** "=== SEARCH DIAGNOSTICS START ===" section
4. **Query Parsing:** "Lucene query parsed successfully" or parse errors
5. **Results:** "Date index X returned Y hits"

**Send these logs for analysis!**

---

## Quick Diagnosis Decision Tree

```
Empty search works?
├─ YES → Data is indexed correctly
│   └─ Search with criteria fails?
│       ├─ YES → Query parsing or field matching issue
│       │   └─ Check: Lucene query syntax, field names, case sensitivity
│       └─ NO → Works on Windows! Issue resolved.
└─ NO → Indexing failed
    └─ Check: Pattern detection, NULL level fields, file format
```

---

## Expected Log Comparison (Mac vs Windows)

### Mac (Working):
```
Auto-detected log format 'WebLogic'
  Extracted: level=INFO, user=admin
Lucene query parsed successfully: level:info
Date index 2026-03-13 returned 42 hits
```

### Windows (Broken - Hypothesis 1):
```
Auto-detected log format 'Adaptive-timestamp-level-message'
  Extracted: level=null, user=null
Lucene query parsed successfully: level:info
Date index 2026-03-13 returned 0 hits
```
**Issue:** Pattern detector fails, level not extracted

### Windows (Broken - Hypothesis 2):
```
Auto-detected log format 'WebLogic'
  Extracted: level=INFO , user=admin
Lucene query parsed successfully: level:info
Date index 2026-03-13 returned 0 hits
```
**Issue:** Trailing space in indexed level field

### Windows (Broken - Hypothesis 3):
```
Auto-detected log format 'WebLogic'
  Extracted: level=INFO, user=admin
CRITICAL: Failed to parse query 'level:ERROR'
  Error: ParseException - Cannot parse level:ERROR
```
**Issue:** Query parsing fails (special character or locale issue)

---

## Next Steps After Diagnosis

Based on log output, we'll implement targeted fixes:
1. Pattern detection refinement
2. Query preprocessing for Windows paths
3. Field trimming/normalization
4. Locale-independent parsing

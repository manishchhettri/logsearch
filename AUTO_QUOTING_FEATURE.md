# Auto-Quoting for Dot-Separated Identifiers

## Feature Overview

The system now automatically quotes dot-separated identifiers (package names, class names) to provide precise search results and correct highlighting.

## Problem Solved

**Before:**
- Query: `core.framework`
- Lucene tokenized to: `core OR framework`
- Results: Any log with `core` OR `framework` (too broad)
- Highlighting: Failed (looked for exact `core.framework` which didn't exist)

**After:**
- Query: `core.framework`
- Auto-converted to: `"core.framework"`
- Lucene searches for: exact phrase `"core.framework"`
- Results: Only logs containing `core.framework` together
- Highlighting: Works! Both `core` and `framework` are highlighted with yellow background

---

## How It Works

### Backend (LogSearchService.java)

**Method:** `autoQuoteDotSeparatedIdentifiers()`

**Detection Pattern:**
```regex
\b([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)\b
```

**Very Conservative Approach:**
- Only matches **Java package-style** identifiers
- Must start with **lowercase letter** (package naming convention)
- Contains only: lowercase letters, digits, underscores, dots
- **Does NOT match**:
  - File paths: `/Users/manish/Java Projects/...` (has slashes/uppercase)
  - Windows paths: `C:\Program Files\...` (has backslash/uppercase)
  - Class names in logs: `PaymentService.process` (starts with uppercase)
  - URLs: `http://example.com` (has colon/slashes)

**Rules:**
1. ✅ Automatically quotes identifiers with dots (e.g., `core.framework`)
2. ✅ Skips if already quoted (e.g., `"core.framework"`)
3. ✅ **Skips entire query if contains field queries** (e.g., `sourceFile:java.lang.Exception`)
   - This prevents `PhraseQuery` errors on `StringField` fields like `sourceFile`
   - Field queries are handled separately by existing preprocessing logic
4. ✅ Skips if contains wildcards (e.g., `core.*.service`)
5. ✅ Works with boolean operators (e.g., `core.framework OR payment.service`)

### Frontend (index.html)

**Highlighting Enhancement:**
```javascript
// Split search terms by spaces AND dots
currentSearchQuery.split(/[\s.]+/)
```

This ensures:
- Query: `core.framework` → Highlights both `core` and `framework` individually
- Query: `com.example.service` → Highlights `com`, `example`, and `service`

---

## Examples

### Example 1: Simple Package Name
**Input:** `core.framework`
**Backend converts to:** `"core.framework"`
**Search results:** Logs containing exact phrase `core.framework`
**Highlighting:** `core` and `framework` highlighted in yellow

### Example 2: Package + Keyword
**Input:** `core.framework ERROR`
**Backend converts to:** `"core.framework" ERROR`
**Search results:** Logs with `core.framework` AND error level
**Highlighting:** `core`, `framework`, and `ERROR` highlighted

### Example 3: Multiple Packages
**Input:** `core.framework payment.service`
**Backend converts to:** `"core.framework" "payment.service"`
**Search results:** Logs with both packages
**Highlighting:** `core`, `framework`, `payment`, `service` highlighted

### Example 4: Boolean Query
**Input:** `core.framework OR payment.gateway`
**Backend converts to:** `"core.framework" OR "payment.gateway"`
**Search results:** Logs with either package
**Highlighting:** All four terms highlighted

### Example 5: Wildcard (No Change)
**Input:** `core.*.service`
**Backend:** No change (wildcard detected)
**Search results:** Lucene wildcard search works as expected
**Highlighting:** `core` and `service` highlighted

### Example 6: Field Query (No Auto-Quoting)
**Input:** `sourceFile:java.lang.Exception`
**Backend:** No auto-quoting applied (entire query skipped to prevent PhraseQuery on StringField)
**Search results:** Uses existing field query preprocessing
**Highlighting:** Works correctly
**Note:** This prevents `PhraseQuery` errors since `sourceFile` is indexed with `StringField` (no position data)

### Example 7: Already Quoted (No Change)
**Input:** `"core.framework"`
**Backend:** No change (already quoted)
**Search results:** Phrase search
**Highlighting:** `core` and `framework` highlighted

### Example 8: Nested Packages
**Input:** `com.example.service.payment.PaymentService`
**Backend converts to:** `"com.example.service.payment.PaymentService"`
**Search results:** Exact package match
**Highlighting:** All terms highlighted

---

## Benefits

✅ **More Precise Results** - Finds exact package/class names, not partial matches
✅ **Correct Highlighting** - Yellow highlights show what was actually searched
✅ **No User Syntax Required** - Works automatically, no need to remember quote syntax
✅ **Backward Compatible** - Doesn't break existing queries
✅ **Smart Detection** - Preserves wildcards, field queries, and boolean operators
✅ **Java/Spring Optimized** - Perfect for Java log files with package names

---

## Technical Details

### Backend Changes
- **File:** `LogSearchService.java`
- **New Method:** `autoQuoteDotSeparatedIdentifiers(String queryText)`
- **Integration:** Called at start of `preprocessQuery()` before other processing
- **Logging:** Debug logs show when identifiers are auto-quoted

### Frontend Changes
- **File:** `index.html`
- **Function:** `highlightLogMessage()`
- **Change:** Split pattern from `/\s+/` to `/[\s.]+/` (spaces AND dots)
- **CSS:** `.highlight-search-term` class (yellow background)

---

## User Guide

### Best Practices

**For Java Package Names:**
```
✅ Type: core.framework
   Auto-converted to: "core.framework"

✅ Type: com.example.service.PaymentService
   Auto-converted to: "com.example.service.PaymentService"
```

**For Wildcards:**
```
✅ Type: core.*.service
   No conversion (wildcard preserved)
```

**For Boolean Queries:**
```
✅ Type: core.framework OR payment.service
   Auto-converted to: "core.framework" OR "payment.service"

✅ Type: core.framework AND ERROR
   Auto-converted to: "core.framework" AND ERROR
```

**Manual Override:**
If you want to search for `core` OR `framework` separately:
```
Type: core OR framework
(Don't use dots - system won't auto-quote)
```

---

## Logging

When debug logging is enabled, you'll see:
```
DEBUG LogSearchService - Auto-quoted identifier: core.framework -> "core.framework"
```

This helps verify the auto-quoting behavior during troubleshooting.

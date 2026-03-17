# Search Syntax Guide

## Searching for Package Names / Dot-Separated Identifiers

### The Problem

When searching for Java package names like `core.framework` or `com.example.service`, Lucene tokenizes the dots as separators, converting your search into:

```
core.framework  →  core OR framework
```

This returns **too many results** - any log with either `core` OR `framework`.

### The Solution: Manual Quoting

**Wrap package names in quotes** to search for the exact phrase:

```
"core.framework"  →  Exact phrase search
```

---

## Examples

### ❌ Without Quotes (Too Broad)
**Search:** `core.framework`
**Lucene interprets as:** `core OR framework`
**Results:** Any log with `core` OR `framework` anywhere (thousands of results)

### ✅ With Quotes (Precise)
**Search:** `"core.framework"`
**Lucene interprets as:** Exact phrase `core.framework`
**Results:** Only logs containing `core.framework` together

---

## Common Use Cases

### 1. Java Package Names
```
"com.example.service"
"java.util.ArrayList"
"org.springframework.boot"
```

### 2. Class Names
```
"PaymentService.processPayment"
"UserController.login"
```

### 3. Multi-Word Package Searches
```
"core.framework" AND ERROR
"payment.service" OR "order.service"
```

### 4. Wildcards (No Quotes Needed)
```
core.*.service
com.example.*
```
**Note:** Wildcards work WITHOUT quotes. Don't quote them.

---

## Search Term Highlighting

When you search, matching terms are highlighted with a **yellow background** in the results.

**How it works:**
- Query: `"core.framework"`
- Highlighting: Both `core` and `framework` are highlighted individually
- This helps you spot the matches even though you searched for a phrase

---

## Boolean Operators

Combine package searches with boolean logic:

```
"core.framework" AND ERROR           # Package with error level
"payment.service" OR "order.service"  # Either package
NOT "legacy.module"                   # Exclude a package
```

---

## Field Queries

Search specific fields (no quotes needed for field names):

```
sourceFile:server-20260312.log
level:ERROR "core.framework"
user:john.doe AND "payment.service"
```

**Available fields:**
- `sourceFile` - Log file path
- `level` - Log level (ERROR, WARN, INFO, etc.)
- `user` - User identifier
- `logger` - Logger/component name
- `thread` - Thread name
- `correlationId` - Correlation ID
- `messageId` - Message ID
- `flowName` - Flow name
- `endpoint` - Endpoint

---

## Why Auto-Quoting Was Disabled

We initially tried auto-quoting package names automatically, but it caused issues:

1. **PhraseQuery Errors:** Some fields (`sourceFile`, etc.) are indexed as `StringField` without position data, so phrase queries fail
2. **File Path Confusion:** Paths like `/Users/manish/Java Projects/...` were being incorrectly quoted
3. **Unpredictable Behavior:** Users couldn't tell when auto-quoting would trigger

**Decision:** Manual quoting is more predictable and gives users full control.

---

## Quick Reference

| What You Want | How to Search | Example |
|---------------|---------------|---------|
| Exact package name | `"package.name"` | `"core.framework"` |
| Either term | `term1 OR term2` | `core OR framework` |
| Package + keyword | `"package" AND keyword` | `"core.framework" AND ERROR` |
| Wildcard | `package.*` | `core.*.service` |
| Field search | `field:value` | `level:ERROR` |
| Multiple packages | `"pkg1" OR "pkg2"` | `"core.framework" OR "payment.service"` |

---

## Tips

1. **Always quote package names** - It's a small habit that makes searches much more precise
2. **Check the results count** - If you get thousands of results, you probably need quotes
3. **Use highlighting** - Yellow highlights show which terms matched
4. **Combine with filters** - Use facets on the left to narrow down results further


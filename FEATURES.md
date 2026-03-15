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

# 17. User Flow Timeline Visualization

Visual timeline representation of user log flows across services and components, providing an intuitive view of user interactions through distributed systems.

### Overview

The User Flow Timeline feature creates a **lightweight, color-coded vertical timeline** showing the chronological flow of user operations across different services, with intelligent exception parsing and interactive navigation.

### Feature Activation

**Trigger**: Automatically appears when `user:` filter is active
- Example: When user searches `user:bob.wilson`
- Button appears: **"📊 View Flow"** in the results header
- Opens in slide-in panel (right side of screen, 500px width)

### Core Capabilities

1. **Lightweight Timeline Visualization**
   - Vertical timeline (top to bottom = chronological order)
   - Each log entry displayed as a color-coded box
   - Service interactions shown with arrows (→)
   - Clean gradient backgrounds by log level
   - Fast rendering (pure HTML/CSS, ~100ms for 100 logs)

2. **Interactive Features**
   - **Click box** → Highlight corresponding log entry in main results
   - **Click log entry** → Scroll to box in timeline
   - **Hover** → Show tooltip with full message
   - **Level toggles** → Show/hide INFO, WARN, ERROR boxes
   - **Correlation ID selector** → Switch between different user sessions
   - **Export** → Screenshot timeline as PNG

3. **Smart Exception Handling**
   - For stack traces, extracts **only top-level information**
   - Shows: Exception type, class, method, and line number
   - Hides: Full 50-line stack traces for readability
   - Example display: `NullPointerException at PaymentService.processPayment:145`

### Visual Design

**Color Scheme (Clean & Professional)**:
- **INFO** - Blue gradient with left border (#E3F2FD to #BBDEFB)
- **WARN** - Orange gradient with left border (#FFF3E0 to #FFE0B2)
- **ERROR** - Red gradient with shadow (#FFEBEE to #FFCDD2)
- **DEBUG** - Green gradient with left border (#E8F5E9 to #C8E6C9)

**Typography**:
- Time: Bold, 12px, gray
- Service names: Bold, 13px, dark
- Messages: Regular, 12px
- Exceptions: Monospace, 11px on colored background

### Data Extraction Logic

1. **Service Name Extraction**
   - Extracts from logger name: `com.example.payment.PaymentService` → `Payment`
   - Removes common suffixes: Service, Controller, Repository
   - Adds spaces before capitals for readability

2. **Exception Top-Level Extraction**
   - Parses exception type from first line
   - Extracts class name, method name, and line number from first stack frame
   - Formats as: `ExceptionType at ClassName.methodName:lineNumber`

3. **Message Simplification**
   - For ERROR/FATAL: Shows exception summary instead of full stack trace
   - For long messages: Truncates to 120 characters with "..."
   - For normal messages: Shows as-is

4. **Grouping Strategy**
   - Groups logs by correlation ID when available
   - Falls back to 5-minute time windows if no correlation ID
   - Allows switching between different user sessions via dropdown

### Timeline Layout

**Vertical Flow Example**:
```
┌──────────────────────────────────────────────────┐
│ 10:15:23  Client → Payment              ✅       │  (Blue gradient)
│ POST /payment - Request received                 │
└──────────────────────────────────────────────────┘
                       ↓
┌──────────────────────────────────────────────────┐
│ 10:15:24  Payment → Database            ✅       │  (Blue gradient)
│ SELECT balance FROM accounts                     │
└──────────────────────────────────────────────────┘
                       ↓
┌──────────────────────────────────────────────────┐
│ 10:15:25  Payment                       ❌       │  (Red gradient)
│ ERROR - NullPointerException                     │
│ at PaymentService.processPayment:145             │
│ [Click to see full log ›]                        │
└──────────────────────────────────────────────────┘
```

### Implementation Details

**Frontend Technology**:
- Pure HTML/CSS implementation (no Mermaid or heavy frameworks)
- Vue.js component for state management (optional - can use vanilla JS)
- html2canvas library for PNG export functionality
- CSS3 gradients, animations, and flexbox for layout

**Backend API (Optional)**:
- Can process entirely client-side from existing search results
- Optional `/api/user-flow` endpoint for server-side grouping
- Returns logs grouped by correlation ID

**Performance**:
- Renders 100 logs in < 100ms
- Lightweight: No 3rd party diagram libraries (~200KB saved vs Mermaid)
- Smooth animations and transitions
- Virtual scrolling for 100+ logs

### Use Cases

1. **Debugging User-Specific Issues**
   - Trace user journey through distributed systems
   - Identify where user requests fail in multi-service architectures
   - Debug authentication, authorization, and session issues

2. **Understanding Service Interactions**
   - Visualize data flow between microservices
   - See timing between events
   - Identify bottlenecks in request processing

3. **Analyzing Error Cascades**
   - See how errors propagate across services
   - Understand failure sequences
   - Identify root cause in distributed failures

4. **Documenting User Journeys**
   - Export timeline as PNG for incident reports
   - Share visual representation with team members
   - Create documentation for common user flows

### Technical Stack

- **HTML5**: Semantic markup for timeline structure
- **CSS3**: Gradients, animations, flexbox, transitions
- **JavaScript**: Component logic and event handling
- **html2canvas**: PNG export functionality
- **Vue.js** (optional): Component framework for state management

### Edge Cases Handled

1. **No Correlation ID**: Groups by 5-minute time windows
2. **Single Log**: Shows single box with no connectors
3. **100+ Logs**: Virtual scrolling or pagination
4. **Missing Service**: Shows "Unknown" placeholder
5. **Very Long Messages**: Truncates with "..." and expands on click
6. **Mixed Log Levels**: Level toggles allow filtering display

### Advantages Over Alternative Approaches

✅ **Performance**: 10x faster than Mermaid (pure HTML/CSS rendering)
✅ **Control**: Exact pixel-perfect styling
✅ **Size**: No 3rd party library (~200KB saved)
✅ **Integration**: Matches existing UI perfectly
✅ **Flexibility**: Easy to add custom features
✅ **Debugging**: Simple DOM inspection
✅ **Responsive**: Natural HTML reflow
✅ **Accessibility**: Proper semantic HTML

### Implementation Effort

**Total**: 10-11 hours

**Breakdown**:
- Phase 1: Basic Timeline (MVP) - 3-4 hours
- Phase 2: Exception Handling - 2 hours
- Phase 3: Interactive Features - 3 hours
- Phase 4: Polish & Export - 2 hours

**Status**: 📝 Planned - Lightweight UI Approach

### User Guide

#### How to Use

**Step 1: Search with User Filter**

The Flow Timeline requires a user-specific search query:

```
user:john.doe
user:customer123
user:admin AND payment
user:guest AND level:ERROR
```

**The "View Flow" button appears automatically when your query includes a user filter.**

**Step 2: Execute Search**

Click "Search" to get results. The system will:
- Search across all matching logs for that user
- Deduplicate results (if same logs indexed multiple times)
- Display results in standard search view

**Step 3: Open Flow Timeline**

Click the **"📊 View Flow"** button (appears in the top toolbar).

The Flow Timeline panel slides in from the right side showing:
- Chronological sequence of all log events
- Color-coded by log level (INFO, WARN, ERROR)
- Service/component names extracted from logger field
- Timestamps with millisecond precision
- Message previews (truncated to 120 characters)

**Step 4: Navigate the Timeline**

Features:
- **Scroll** through the timeline vertically
- **Click** any timeline box to jump to that entry in the main results
- **Hover** to see full message tooltip
- **Close** the panel with the × button or by clicking outside

#### Real-World Use Cases

**1. Trace Payment Failure**

Scenario: Customer reports payment failed

Steps:
```
1. Search: user:customer123 AND payment
2. View Flow
3. Observe timeline:
   - 12:03:40 AuthService - INFO: Authentication successful
   - 12:03:41 PaymentService - INFO: Processing payment $49.99
   - 12:03:45 PaymentService - ERROR: Database connection timeout
   - 12:03:45 OrderService - WARN: Payment callback not received
```

Result: Clearly see that payment failed at database connection, causing downstream timeout.

**2. Debug Multi-Service Transaction**

Scenario: Order creation involves 5 microservices

Steps:
```
1. Search: user:admin AND order:12345
2. View Flow
3. See service transitions:
   - OrderService → InventoryService → PaymentService → ShippingService → NotificationService
```

Result: Identify which service in the chain failed or had delays.

**3. Investigate Authentication Issues**

Scenario: User reports intermittent login failures

Steps:
```
1. Search: user:john.doe AND (login OR auth OR session)
2. View Flow
3. Observe pattern:
   - Successful login attempts: AuthService → SessionService → UserService
   - Failed attempts: AuthService → ERROR (missing SessionService logs)
```

Result: Session service not receiving auth events → likely network/routing issue.

**4. Performance Analysis**

Scenario: User reports slow checkout process

Steps:
```
1. Search: user:customer456 AND checkout
2. View Flow
3. Check timestamps:
   - 14:22:00.100 CheckoutService - Start
   - 14:22:05.850 CheckoutService - End  (5.75 seconds!)
   - 14:22:01.000 PaymentService - Start
   - 14:22:05.800 PaymentService - End  (4.8 seconds in payment)
```

Result: Payment service taking 4.8s of the 5.75s total → bottleneck identified.

#### Example Flow Timeline

**E-commerce Purchase Flow:**

```
Timeline for user:sarah.jones (Search: user:sarah.jones AND purchase)

┌────────────────────────────────────────────────────┐
│ WebController - INFO            14:30:00.123       │
│ User navigated to product page: /products/book-123 │
└────────────────────────────────────────────────────┘
                    ↓ 2 seconds
┌────────────────────────────────────────────────────┐
│ ShoppingCartService - INFO      14:30:02.456       │
│ Item added to cart: book-123 (quantity: 1)         │
└────────────────────────────────────────────────────┘
                    ↓ 5 seconds
┌────────────────────────────────────────────────────┐
│ CheckoutService - INFO          14:30:07.789       │
│ Checkout initiated for cart: cart-456              │
└────────────────────────────────────────────────────┘
                    ↓ 200ms
┌────────────────────────────────────────────────────┐
│ PaymentService - INFO           14:30:07.989       │
│ Processing payment: $29.99 (card: **** 4532)       │
└────────────────────────────────────────────────────┘
                    ↓ 3 seconds
┌────────────────────────────────────────────────────┐
│ PaymentService - ERROR          14:30:10.991       │
│ Payment gateway timeout after 3000ms retry         │
│ java.net.SocketTimeoutException: Read timed out    │
└────────────────────────────────────────────────────┘
                    ↓ 100ms
┌────────────────────────────────────────────────────┐
│ CheckoutService - WARN          14:30:11.091       │
│ Payment callback not received, retrying...         │
└────────────────────────────────────────────────────┘
```

**Insights from this timeline:**
- User spent 5 seconds on product page before adding to cart
- Checkout flow started smoothly
- Payment service timeout after 3 seconds
- Checkout service detected failure and initiated retry
- **Root cause:** External payment gateway latency/timeout

#### Best Practices

**Good Queries for Flow View:**
```
✓ user:admin                          # All activity for admin user
✓ user:customer123 AND payment        # Payment-related activity
✓ user:guest AND level:ERROR          # All errors for guest user
✓ user:john.doe AND "checkout"        # Checkout flow for John
```

**Less Useful Queries:**
```
✗ level:ERROR                         # No user filter (button won't appear)
✗ payment failed                      # No user filter
✗ user:admin AND user:guest           # Multiple users (confusing flow)
```

**When to Use Flow Timeline:**
- Debugging user-specific issues
- Tracing requests across microservices
- Understanding event sequencing and timing
- Investigating authentication/session problems
- Analyzing user journey and behavior

**When to Use Standard Search:**
- Searching across all users
- Analyzing system-wide patterns
- Looking for specific error messages without user context
- Performing aggregate analytics

#### Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Esc` | Close Flow Timeline panel |
| `Arrow Up/Down` | Scroll through timeline |
| `Click` | Jump to entry in main results |

#### Troubleshooting

**"View Flow" Button Not Appearing**

Cause: Query doesn't contain `user:` filter

Solution: Add user filter to your query:
```
# Before
payment failed

# After
user:customer123 AND payment failed
```

**Empty Timeline**

Cause: No results for the user/query combination

Solution:
- Verify user exists in logs
- Expand time range
- Check user filter syntax: `user:username` (no spaces)

**Timeline Shows Wrong Order**

Cause: Results sorted by relevance, not time

Solution: Timeline automatically sorts by timestamp (chronological), but main results may show different order. Timeline is always time-ordered.

**Slow Timeline Rendering**

Cause: Too many results (>1000 entries)

Solution:
- Narrow time range
- Add more specific filters: `user:admin AND payment AND level:ERROR`
- Use pagination (timeline shows all results, may be slow)

#### Comparison with Other Views

| Feature | Standard Search | Flow Timeline | Context View |
|---------|----------------|---------------|--------------|
| **Purpose** | Find matching logs | Trace user journey | See surrounding logs |
| **Display** | All matches | Chronological flow | ±10 lines context |
| **Best for** | General search | User debugging | Understanding single event |
| **Filter** | Any | Requires `user:` | Click any result |
| **Visualization** | List + facets | Timeline | Code-like view |

#### Current Limitations

1. **Single User Only:** Timeline works best with one user filter
   - Query: `user:admin` ✓
   - Query: `user:admin OR user:guest` ❌ (confusing timeline)

2. **Time Range Dependent:** Shows only results from current search
   - To see complete user flow, search wide time range (e.g., last 24h)

3. **Service Name Extraction:** Relies on logger field format
   - Works best with standard package naming: `com.company.service.ServiceName`
   - May show incomplete names for non-standard loggers

4. **Message Truncation:** Long messages truncated to 120 chars in timeline
   - Full message visible on click in main results
   - Exception details shown in smart format

#### Key Benefits

✅ Visual understanding of user journey
✅ Quick identification of failure points
✅ Service transition visibility
✅ Timing analysis capability
✅ Reduced time to resolution for user-reported issues

---

# Summary

LogSearch provides a lightweight yet powerful platform for searching archived logs.

Key strengths include:

• fast Lucene-based search
• stack trace awareness
• time-based filtering
• contextual event viewing
• user flow timeline visualization
• minimal infrastructure requirements

It complements centralized observability platforms by enabling **efficient investigation of historical log data**.

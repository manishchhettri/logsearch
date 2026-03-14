# Log Search - Features & Roadmap

## Implemented Features

### Core Search Capabilities
- ✅ Full-text search across all log fields with Lucene
- ✅ Date range filtering with day-based index partitioning
- ✅ Advanced pagination (configurable page size up to 1000)
- ✅ Code-aware search (Java class names, stack traces, method names)
- ✅ Formatted stack trace display with proper line breaks
- ✅ Manual re-indexing (incremental and full re-index options)
- ✅ Modern web UI with responsive design
- ✅ Boolean operators (AND, OR, NOT)
- ✅ Wildcards and phrase searches
- ✅ Field-specific searches (level:ERROR, user:admin, etc.)

### Analytics & Insights 
- ✅ **Error Hotspots** - Automatic grouping by exception type, component, user
- ✅ **Timeline Visualization** - Hourly/daily error distribution with spike detection
- ✅ **Top N Analysis** - Most affected components, users, files with percentages
- ✅ **Pattern Detection** - Auto-detect memory leaks, retry loops, deployment issues
- ✅ **Aggregations API** - Multi-facet aggregation (level, exception, logger, user, file)
- ✅ **Statistics Cards** - Total logs, errors, warnings, top exceptions

### Advanced Search 
- ✅ **Faceted Filters** - Dynamic left-sidebar filters (click to refine)
- ✅ **Context View** - See surrounding logs before/after a specific entry
- ✅ **Field Highlighting** - Automatic highlighting of errors, exceptions, IPs, URLs, timestamps, paths, IDs

### Productivity Features
- ✅ **Saved Searches** - Persist and reload common queries with relative time support
- ✅ **Custom Dashboards** - Create, save, and manage dashboards with analytics widgets
- ✅ **Dashboard Widgets** - Pie charts, stacked bar charts, statistics cards
- ✅ **Dashboard Drill-down** - Navigate from aggregated stats to detailed search results
- ✅ **Relative Time Ranges** - Auto-updating time windows (Last 1h, 6h, 24h, 2d, 7d)
- ✅ **Quick Time Buttons** - One-click time range selection
- ✅ **Bulk Download** - Download logs from multiple URLs/paths simultaneously (up to 5)
- ✅ **Export** - Export search results to various formats

### Log Format Support 
- ✅ **Multi-Format Parsing** - 5-tier fallback strategy
- ✅ **JSON Logs** - Auto-detection and field extraction
- ✅ **Apache/Nginx Logs** - Access log format support
- ✅ **Custom Patterns** - Configurable regex patterns (3-group and 6-group)
- ✅ **Multi-line Support** - Properly handles stack traces and multi-line messages
- ✅ **Smart Continuation** - Automatic detection of continuation lines
- ✅ **Timestamp Auto-detection** - Supports ISO 8601, Apache, Syslog, and custom formats

### Operations & Management
- ✅ **Auto-indexing** - Automatically watches for new log files
- ✅ **Retention Management** - Auto-cleanup of old indexes
- ✅ **Configurable Settings** - External YAML configuration without rebuild
- ✅ **REST API** - Full programmatic access for automation
- ✅ **Status Endpoint** - Monitor indexing progress and health
- ✅ **Single JAR Deployment** - No external dependencies

---

## Implementation Timeline

### Phase 1 - Analytics Foundation ✅ COMPLETED
- ✅ Aggregation API endpoint (`/api/aggregations`)
- ✅ Multi-facet aggregation (level, exception, logger, user, file)
- ✅ Hourly timeline generation
- ✅ Quick filters (faceted sidebar)
- ✅ Error hotspots UI
- ✅ Timeline visualization with Chart.js
- ✅ Pattern detection engine with spike detection
- ✅ Exception tracking by class (top 3 affected classes)
- ✅ Top-N analysis with percentages

### Phase 2 - Enhanced UX ✅ COMPLETED
- ✅ Context view (see logs before/after)
- ✅ Saved searches with relative time support
- ✅ Dashboards with widgets and drill-down
- ✅ Field highlighting (errors, exceptions, IPs, URLs, etc.)
- ✅ Responsive sidebar with faceted filters
- ✅ Custom dashboards tab with CRUD operations
- ✅ Dashboard widgets (pie charts, bar charts, statistics)
- ✅ Quick time range buttons
- ✅ Dashboard-to-search navigation

### Phase 3 - Real-time & Advanced Patterns (Planned)
- ⚙️ **Live Tail** - Real-time log streaming (WebSocket)
- ⚙️ **Auto-refresh** - Periodic search updates
- ⚙️ **Advanced Pattern Detection** - More sophisticated anomaly detection
- ⚙️ **Trend Analysis** - Error rate changes over time
- ⚙️ **Related Logs** - Find logs from same session/user/request
- ⚙️ **Smart Suggestions** - Query auto-complete based on indexed data

### Phase 4 - Alerting & Collaboration (Future)
- ⚙️ **Custom Alerts** - Email/webhook when conditions met
- ⚙️ **Threshold Alerts** - Notify on error rate spikes
- ⚙️ **Pattern Alerts** - Detect and alert on specific patterns
- ⚙️ **Scheduled Reports** - Daily/weekly error summaries
- ⚙️ **Share Links** - Shareable URLs with filter state
- ⚙️ **Integration Hooks** - Slack, Teams, PagerDuty, etc.
- ⚙️ **Advanced Export** - JSON, Excel, formatted reports

### Phase 5 - Enterprise Features (Future Consideration)
- ⚙️ **Multi-user Support** - User accounts and permissions
- ⚙️ **Team Collaboration** - Share searches and dashboards
- ⚙️ **Audit Logging** - Track who searched what
- ⚙️ **SSO Integration** - LDAP, SAML, OAuth
- ⚙️ **Role-based Access** - Granular permissions

---

## Current Status: Phase 1 & 2 Complete ✅

### Verified & Working (as of March 14, 2026)

**Backend:**
- ✅ Aggregations API endpoint (`/api/aggregations`)
- ✅ Multi-facet aggregation (level, exception, logger, user, file)
- ✅ Hourly timeline generation (tested with 263 hourly data points)
- ✅ Pattern detection engine:
  - Spike detection (3x average threshold)
  - Exception tracking by class (shows top 3 affected classes with counts)
  - Memory issue detection with class-level detail
  - Automatically filters out framework classes (java.*, org.springframework.*, etc.)
- ✅ Top-N analysis with percentages
- ✅ Exception type and class extraction from stack traces
- ✅ Context API endpoint (`/api/context`)
- ✅ Full re-index functionality (incremental and full)

**Frontend:**
- ✅ Responsive sidebar with faceted filters
- ✅ Analytics dashboard with stats cards
- ✅ Timeline chart (Chart.js integration)
- ✅ Pattern alerts with severity levels
- ✅ Click-to-filter facet interaction
- ✅ Toggle analytics visibility
- ✅ Custom Dashboards tab with CRUD operations
- ✅ Dashboard widgets (pie charts, stacked bar charts, statistics)
- ✅ Field highlighting for errors, exceptions, IPs, URLs, timestamps, paths, IDs
- ✅ Saved searches with localStorage persistence
- ✅ Quick time range buttons (Last 1h, 6h, 24h, 2d, 7d)
- ✅ Relative time support with auto-refresh
- ✅ Context view modal showing surrounding log lines
- ✅ Dashboard-to-search drill-down navigation
- ✅ Re-index modal with full re-index checkbox

**Multi-Format Log Parsing:**
- ✅ JSON log format detection and field extraction
- ✅ Apache/nginx access log format support
- ✅ 5-tier fallback parsing strategy
- ✅ Auto-detection of common timestamp formats
- ✅ Multi-line stack trace handling

**Tested & Verified:**
- ✅ API returns correct aggregations (tested with 7,665+ logs)
- ✅ Timeline generates accurate hourly data points
- ✅ Pattern detection with class-level detail
- ✅ UI loads with all components
- ✅ Async aggregations don't block search results (~0.6s independent load time)
- ✅ Dashboards with relative time auto-refresh
- ✅ Field highlighting across all log message types
- ✅ Dashboard-to-search navigation preserves query and time context
- ✅ JSON and Apache log formats correctly parsed and indexed
- ✅ Full re-index deletes and rebuilds all indexes

---

## Design Philosophy

This is a **free, open-source tool** designed for developers who need:
- Fast, local log analysis without cloud dependencies
- Splunk-like features without enterprise pricing
- Self-hosted solution with complete data privacy
- Single JAR deployment with zero external services
- Code-aware search optimized for Java applications

**All features are and will remain free and open source.**

---

## Contributing

Feature requests and contributions welcome! See the roadmap above for planned features.

If you'd like to contribute to Phase 3, 4, or 5 features, please open an issue to discuss the approach first.

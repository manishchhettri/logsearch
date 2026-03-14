# Log Search - Feature Tiers

## Free Version (Community Edition)

**Core Search Capabilities:**
- ✓ Full-text search across all log fields
- ✓ Date range filtering
- ✓ Basic pagination (50 results per page)
- ✓ Code-aware search (Java class names, stack traces)
- ✓ Simple result display with formatted stack traces
- ✓ Basic export (CSV)
- ✓ Manual re-indexing
- ✓ Web UI

**Limitations:**
- Maximum 100 results per search
- No aggregations or analytics
- No saved searches
- No pattern detection
- No alerting

---

## Premium Version (Professional Edition)

**Everything in Free, plus:**

### Analytics & Insights 📊
- ✅ **Error Hotspots** - Automatic grouping by exception type, component, user
- ✅ **Timeline Visualization** - Hourly/daily error distribution with spike detection
- ✅ **Top N Analysis** - Most affected components, users, files
- ✅ **Pattern Detection** - Auto-detect memory leaks, retry loops, deployment issues
- ⚙️ **Trend Analysis** - Error rate changes over time

### Advanced Search 🔍
- ✅ **Faceted Filters** - Dynamic left-sidebar filters (click to refine)
- ⚙️ **Context View** - See surrounding logs (before/after)
- ⚙️ **Related Logs** - Find logs from same session/user/request
- ⚙️ **Smart Suggestions** - Query auto-complete based on indexed data
- ⚙️ **Unlimited results (1000+ per search)**

### Productivity Features ⚡
- ✅ **Saved Searches** - Persist and reload common queries with relative time support
- ✅ **Dashboards** - Custom views with analytics widgets (pie charts, bar charts, statistics)
- ✅ **Dashboard Drill-down** - Navigate from aggregated stats to detailed search results
- ✅ **Relative Time Ranges** - Auto-updating time windows (Last 1h, 6h, 24h, 2d, 7d)
- ⚙️ **Share Links** - Shareable URLs with filter state
- ⚙️ **Advanced Export** - JSON, Excel, formatted reports
- ⚙️ **Bulk Operations** - Export all results (not just current page)

### Real-time Monitoring 🔴
- **Live Tail** - Real-time log streaming (WebSocket)
- **Auto-refresh** - Periodic search updates
- **Change Notifications** - Alert when new errors appear

### Alerting & Automation 🚨
- **Custom Alerts** - Email/webhook when conditions met
- **Threshold Alerts** - Notify on error rate spikes
- **Pattern Alerts** - Detect and alert on specific patterns
- **Scheduled Reports** - Daily/weekly error summaries
- **Integration Hooks** - Slack, Teams, PagerDuty, etc.

### Enterprise Features 🏢
- **Multi-user Support** - User accounts and permissions
- **Team Collaboration** - Share searches and dashboards
- **Audit Logging** - Track who searched what
- **API Access** - Programmatic search and analytics
- **SSO Integration** - LDAP, SAML, OAuth

---

## Pricing Strategy (Future)

**Free:**
- Unlimited for personal/development use
- Single user
- Community support

**Professional:**
- $49/user/month or $490/year
- Up to 10 users
- Email support
- All premium features

**Enterprise:**
- Custom pricing
- Unlimited users
- Dedicated support
- Custom integrations
- On-premise deployment support

---

## Implementation Phases

**Phase 1 - Analytics Foundation** ✅ COMPLETED
- ✅ Aggregation API
- ✅ Error hotspots UI
- ✅ Timeline visualization
- ✅ Quick filters (faceted sidebar)

**Phase 2 - Enhanced UX** ✅ COMPLETED
- ✅ Context view (see logs before/after)
- ✅ Saved searches with relative time support
- ✅ Dashboards with widgets and drill-down
- ✅ Field highlighting (errors, exceptions, IPs, URLs, etc.)
- ⚙️ Export improvements
- ⚙️ Share links

**Phase 3 - Real-time & Patterns** (1-2 weeks)
- Pattern detection
- Live tail
- Auto-refresh

**Phase 4 - Alerting & Enterprise** (2 weeks)
- Alert engine
- Webhook support
- Email notifications
- User management

---

## Current Status: Phase 1 & 2 Complete ✅

### Completed Features (as of March 14, 2026)

**Backend:**
- ✅ Aggregations API endpoint (`/api/aggregations`)
- ✅ Multi-facet aggregation (level, exception, logger, user, file)
- ✅ Hourly timeline generation
- ✅ Pattern detection engine:
  - Spike detection (3x average threshold)
  - Exception tracking by class (shows top 3 affected classes with counts)
  - Memory issue detection with class-level detail
  - Automatically filters out framework classes (java.*, org.springframework.*, etc.)
- ✅ Top-N analysis with percentages
- ✅ Exception type and class extraction from stack traces

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

**Multi-Format Log Parsing:**
- ✅ JSON log format detection and field extraction
- ✅ Apache/nginx access log format support
- ✅ 5-tier fallback parsing strategy
- ✅ Auto-detection of common timestamp formats

**Verified Testing:**
- ✅ API returns correct aggregations (tested with 7,665 logs)
- ✅ Timeline generates 263 hourly data points
- ✅ Pattern detection with class-level detail
- ✅ UI loads with all components
- ✅ Async aggregations don't block search results (~0.6s independent load time)
- ✅ Dashboards with relative time auto-refresh
- ✅ Field highlighting across all log message types
- ✅ Dashboard-to-search navigation preserves query and time context
- ✅ JSON and Apache log formats correctly parsed and indexed

### Next Steps: Phase 3 - Real-time & Advanced Patterns
All Phase 1 and Phase 2 features are implemented and available.
Free tier limitations can be enforced via configuration flag in future releases.

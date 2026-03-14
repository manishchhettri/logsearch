# Default Log Download Locations Guide

## Overview

LogSearch now supports configuring **default log download locations** in `application.yml`. This allows you to predefine common log server URLs for quick access in the UI.

---

## Configuration

### Basic Configuration

Add download locations to your `application.yml`:

```yaml
log-search:
  download-locations:
    - name: "Dev Server 1"
      url: "http://dev1.example.com/logs/"
      description: "Development environment 1 logs"
      enabled: true

    - name: "UAT Server"
      url: "http://uat.example.com/logs/"
      description: "UAT environment logs"
      enabled: true

    - name: "Production Server"
      url: "http://prod.example.com/logs/"
      description: "Production logs (read-only access)"
      enabled: false
      requires-auth: true
      default-username: "readonly"
```

### Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Display name for this location |
| `url` | Yes | Base URL of the log server |
| `description` | No | Description shown to users |
| `enabled` | No | Whether this location is active (default: true) |
| `requires-auth` | No | Indicates if authentication is required (default: false) |
| `default-username` | No | Pre-fill username for auth (password still required from user) |

---

## REST API

### Get Download Locations

```
GET /api/download-locations
```

**Response:**
```json
[
  {
    "name": "Dev Server 1",
    "url": "http://dev1.example.com/logs/",
    "description": "Development environment 1 logs",
    "enabled": true,
    "requiresAuth": false
  },
  {
    "name": "UAT Server",
    "url": "http://uat.example.com/logs/",
    "description": "UAT environment logs",
    "enabled": true,
    "requiresAuth": false
  }
]
```

**Notes:**
- Only returns `enabled: true` locations
- Disabled locations are filtered out

---

## Example Configurations

### 1. Multiple Environment Servers

```yaml
log-search:
  download-locations:
    # Development Environments
    - name: "Dev 1"
      url: "http://dev1.company.com/logs/"
      description: "Development environment 1"
      enabled: true

    - name: "Dev 2"
      url: "http://dev2.company.com/logs/"
      description: "Development environment 2"
      enabled: true

    # UAT Environment
    - name: "UAT"
      url: "http://uat.company.com/logs/"
      description: "User Acceptance Testing"
      enabled: true
      requires-auth: true

    # Production (disabled by default for safety)
    - name: "Production"
      url: "http://prod.company.com/logs/"
      description: "Production environment (use with caution)"
      enabled: false
      requires-auth: true
      default-username: "readonly"
```

### 2. Multi-Tenant SaaS Deployment

```yaml
log-search:
  download-locations:
    - name: "Tenant A - Production"
      url: "http://tenant-a.logs.company.com/"
      description: "Tenant A production logs"
      enabled: true
      requires-auth: true

    - name: "Tenant B - Production"
      url: "http://tenant-b.logs.company.com/"
      description: "Tenant B production logs"
      enabled: true
      requires-auth: true

    - name: "Shared Services"
      url: "http://shared.logs.company.com/"
      description: "Shared infrastructure logs"
      enabled: true
```

### 3. Different Log Types

```yaml
log-search:
  download-locations:
    - name: "Application Logs"
      url: "http://appserver.company.com/logs/application/"
      description: "Java application server logs"
      enabled: true

    - name: "Web Server Logs"
      url: "http://webserver.company.com/logs/nginx/"
      description: "NGINX web server access logs"
      enabled: true

    - name: "Database Logs"
      url: "http://dbserver.company.com/logs/postgresql/"
      description: "PostgreSQL database logs"
      enabled: true
      requires-auth: true

    - name: "Integration Bus Logs"
      url: "http://iib.company.com/logs/"
      description: "IBM Integration Bus logs"
      enabled: true
      requires-auth: true
```

---

## Integration with Multi-Index Architecture

You can combine download locations with multi-index configuration:

```yaml
log-search:
  # Multi-index configuration
  indexes:
    - name: payment-prod1
      display-name: "Payment Service (Production)"
      path: ./logs/prod1/payment-service
      file-pattern: "server-\\d{8}\\.log"
      environment: prod1
      # ... other settings

  # Download locations (for fetching logs)
  download-locations:
    - name: "Payment Service - Prod Server"
      url: "http://prod1.payment.company.com/logs/"
      description: "Download payment service logs for indexing"
      enabled: true
      requires-auth: true
```

**Workflow:**
1. User selects "Payment Service - Prod Server" from dropdown
2. Downloads logs to `./logs/prod1/payment-service/`
3. Triggers re-indexing
4. Logs appear in `payment-prod1` index

---

## UI Integration (Future)

### Planned UI Features

The UI can be enhanced to use these download locations:

**Download Tab Enhancement:**
```html
<div class="download-section">
  <h3>Quick Download</h3>

  <!-- Dropdown of configured locations -->
  <select id="downloadLocationSelect">
    <option value="">Select a predefined location...</option>
    <!-- Populated from /api/download-locations -->
  </select>

  <!-- Or custom URL -->
  <input type="text" id="customUrl" placeholder="Or enter custom URL...">

  <!-- Auth fields (shown if location requires auth) -->
  <input type="text" id="username" placeholder="Username">
  <input type="password" id="password" placeholder="Password">

  <button onclick="downloadFromLocation()">Download Logs</button>
</div>

<script>
async function loadDownloadLocations() {
  const response = await fetch('/api/download-locations');
  const locations = await response.json();

  const select = document.getElementById('downloadLocationSelect');

  locations.forEach(location => {
    const option = document.createElement('option');
    option.value = location.url;
    option.textContent = location.name;
    option.dataset.requiresAuth = location.requiresAuth;
    option.dataset.defaultUsername = location.defaultUsername || '';
    option.dataset.description = location.description || '';
    select.appendChild(option);
  });
}

function downloadFromLocation() {
  const select = document.getElementById('downloadLocationSelect');
  const url = select.value || document.getElementById('customUrl').value;
  const username = document.getElementById('username').value;
  const password = document.getElementById('password').value;

  // Call download API
  fetch('/api/download-logs', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({
      urls: [url],
      username: username,
      password: password
    })
  })
  .then(response => response.json())
  .then(result => {
    console.log('Download result:', result);
    // Show success message and trigger re-index
  });
}

loadDownloadLocations();
</script>
```

---

## Security Considerations

### 1. Password Storage

**Never store passwords in application.yml:**
```yaml
# ❌ WRONG - Never do this
- name: "Production"
  url: "http://prod.company.com/logs/"
  default-username: "admin"
  default-password: "secret123"  # ❌ Security risk!

# ✅ CORRECT - Only pre-fill username, require password from user
- name: "Production"
  url: "http://prod.company.com/logs/"
  default-username: "readonly"
  requires-auth: true
```

### 2. Disable Sensitive Locations by Default

```yaml
# Production logs disabled by default
- name: "Production Logs"
  url: "http://prod.company.com/logs/"
  enabled: false  # ✅ Must be manually enabled
  requires-auth: true
```

### 3. Use Read-Only Accounts

Configure log servers with read-only accounts:
- Create dedicated `log-viewer` user
- Grant only read access to log directories
- Use `default-username: "log-viewer"` in config

---

## Testing

### Test Default Locations API

```bash
# Get configured download locations
curl http://localhost:8080/api/download-locations

# Response:
[
  {
    "name": "Dev Server 1",
    "url": "http://dev1.example.com/logs/",
    "description": "Development environment 1 logs",
    "enabled": true,
    "requiresAuth": false,
    "defaultUsername": null
  }
]
```

### Test Download Using Default Location

```bash
# Download from a configured location
curl -X POST http://localhost:8080/api/download-logs \
  -H "Content-Type: application/json" \
  -d '{
    "urls": ["http://dev1.example.com/logs/"],
    "username": "",
    "password": ""
  }'
```

---

## Migration from Manual URLs

### Before (Manual URL Entry)

Users had to remember and type full URLs:
```
http://dev1.company.com/logs/
http://uat.company.com/weblogic/logs/
http://prod.company.com/application/logs/server1/
```

### After (Predefined Locations)

Users select from dropdown:
- "Dev 1"
- "UAT WebLogic"
- "Production App Server 1"

**Benefits:**
- No typos
- Consistent naming
- Easy discovery
- Security flags visible (requires-auth)

---

## Advanced Use Cases

### Dynamic URL Construction

For advanced scenarios, you could enhance the system to support URL templates:

```yaml
# Future enhancement idea
log-search:
  download-locations:
    - name: "Payment Service - {env}"
      url-template: "http://{env}.payment.company.com/logs/"
      environments: [dev1, dev2, uat1, prod1]
      requires-auth: true
```

This would generate:
- "Payment Service - dev1" → http://dev1.payment.company.com/logs/
- "Payment Service - dev2" → http://dev2.payment.company.com/logs/
- etc.

---

## Summary

✅ **Added:**
- `DownloadLocation` model
- Configuration in `application.yml`
- GET `/api/download-locations` endpoint
- Sample configuration

✅ **Benefits:**
- Predefined server URLs
- Easy selection in UI
- Security flags (requires-auth)
- Environment organization

✅ **Next Steps:**
- Enhance UI to use download locations
- Add location selection dropdown
- Show auth requirements
- Auto-fill username from config

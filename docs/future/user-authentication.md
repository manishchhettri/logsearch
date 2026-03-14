# User Authentication and Personalized Dashboards

## Overview

This document outlines the implementation plan for user authentication and personalized dashboard storage to enable multi-user enterprise deployment where each user can save and manage their own dashboards, saved searches, and preferences.

## Problem Statement

### Current Architecture

**Single-user mode:**
- No user authentication
- Dashboards stored in browser localStorage (not persistent across browsers/devices)
- No user-specific preferences
- Not suitable for shared enterprise server deployment

### Enterprise Requirements

When deployed as a shared service for multiple developers:

1. **User Identity:**
   - Each user needs their own login
   - Track who performed which searches (audit trail)
   - User-specific preferences

2. **Data Isolation:**
   - Each user's dashboards are private
   - Saved searches are per-user
   - Optional: shared dashboards for teams

3. **Persistence:**
   - Dashboards survive browser cache clearing
   - Access from any device/browser
   - Data backed up with application

4. **Zero External Dependencies:**
   - No database server (PostgreSQL, MySQL, etc.)
   - No external authentication service (LDAP, AD)
   - Simple JSON-based file storage
   - Optional future: integrate with corporate LDAP

## Solution: File-Based User Authentication

### Design Principles

1. **Simple JSON storage** (no database required)
2. **Bcrypt password hashing** (secure, industry-standard)
3. **JWT session tokens** (stateless authentication)
4. **Per-user data files** (isolated user data)
5. **Configuration flag** to enable/disable authentication

## Architecture Design

### Directory Structure

```
.log-search/
├── users/
│   ├── users.json                      # User credentials and metadata
│   └── data/
│       ├── john.doe/
│       │   ├── dashboards.json         # User's dashboards
│       │   ├── saved-searches.json     # User's saved searches
│       │   └── preferences.json        # User preferences
│       ├── jane.smith/
│       │   ├── dashboards.json
│       │   ├── saved-searches.json
│       │   └── preferences.json
│       └── admin/
│           └── ...
```

### Data Models

#### users.json
```json
{
  "users": [
    {
      "username": "john.doe",
      "passwordHash": "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
      "email": "john.doe@company.com",
      "fullName": "John Doe",
      "role": "user",
      "createdAt": "2026-03-12T10:30:00Z",
      "lastLogin": "2026-03-15T09:15:00Z",
      "enabled": true
    },
    {
      "username": "admin",
      "passwordHash": "$2a$10$...",
      "email": "admin@company.com",
      "fullName": "Administrator",
      "role": "admin",
      "createdAt": "2026-03-01T00:00:00Z",
      "lastLogin": "2026-03-15T08:00:00Z",
      "enabled": true
    }
  ]
}
```

#### dashboards.json (per user)
```json
{
  "dashboards": [
    {
      "id": "dash-1",
      "name": "Production Errors",
      "query": "level:ERROR",
      "timeRange": "last-24h",
      "createdAt": "2026-03-12T10:00:00Z",
      "updatedAt": "2026-03-14T15:30:00Z",
      "widgets": [
        {
          "type": "error-count",
          "title": "Total Errors"
        },
        {
          "type": "level-distribution",
          "title": "Log Levels"
        }
      ]
    },
    {
      "id": "dash-2",
      "name": "Payment Service Issues",
      "query": "PaymentService AND (ERROR OR Exception)",
      "timeRange": "last-7d",
      "createdAt": "2026-03-13T14:00:00Z",
      "updatedAt": "2026-03-13T14:00:00Z",
      "widgets": [
        {
          "type": "error-count",
          "title": "Payment Errors"
        }
      ]
    }
  ]
}
```

#### saved-searches.json (per user)
```json
{
  "searches": [
    {
      "id": "search-1",
      "name": "NPE in PaymentService",
      "query": "NullPointerException AND PaymentService",
      "timeRange": "last-30d",
      "createdAt": "2026-03-10T09:00:00Z"
    },
    {
      "id": "search-2",
      "name": "Database timeouts",
      "query": "timeout AND database",
      "timeRange": "last-7d",
      "createdAt": "2026-03-11T11:30:00Z"
    }
  ]
}
```

#### preferences.json (per user)
```json
{
  "theme": "light",
  "defaultTimeRange": "last-24h",
  "pageSize": 50,
  "dateFormat": "yyyy-MM-dd HH:mm:ss",
  "timezone": "Pacific/Auckland",
  "notifications": {
    "enabled": false,
    "email": "john.doe@company.com"
  }
}
```

## Implementation Plan

### Phase 1: Core Authentication

#### 1. Add Dependencies

**pom.xml:**
```xml
<!-- JWT for session tokens -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>

<!-- BCrypt for password hashing -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
    <version>5.7.3</version>
</dependency>

<!-- Gson for JSON serialization -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

#### 2. Configuration

**application.yml:**
```yaml
log-search:
  # Existing config...

  # NEW: User authentication
  auth:
    enabled: false                       # Set to true to enable authentication
    jwt-secret: "change-this-secret-key-in-production"
    jwt-expiration-hours: 24            # Token validity period
    users-file: "./.log-search/users/users.json"
    user-data-dir: "./.log-search/users/data"
    allow-registration: false           # Allow self-registration or admin-only
    default-admin-username: "admin"
    default-admin-password: "admin"     # Changed on first login
```

#### 3. User Model

**User.java:**
```java
package com.lsearch.logsearch.model;

import java.time.ZonedDateTime;

public class User {
    private String username;
    private String passwordHash;
    private String email;
    private String fullName;
    private UserRole role;
    private ZonedDateTime createdAt;
    private ZonedDateTime lastLogin;
    private boolean enabled;

    // Getters and setters

    public enum UserRole {
        ADMIN,
        USER
    }
}
```

**UserCredentials.java:**
```java
public class UserCredentials {
    private String username;
    private String password;

    // Getters and setters
}
```

**AuthToken.java:**
```java
public class AuthToken {
    private String token;
    private String username;
    private long expiresAt;

    // Getters and setters
}
```

#### 4. User Repository (JSON File-Based)

**UserRepository.java:**
```java
package com.lsearch.logsearch.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final LogSearchProperties properties;
    private final Gson gson;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private Path usersFile;
    private UserStore userStore;

    public UserRepository(LogSearchProperties properties) {
        this.properties = properties;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .create();
    }

    @PostConstruct
    public void initialize() throws IOException {
        if (!properties.isAuthEnabled()) {
            log.info("Authentication disabled, skipping user repository initialization");
            return;
        }

        this.usersFile = Paths.get(properties.getAuthUsersFile());
        Files.createDirectories(usersFile.getParent());

        if (!Files.exists(usersFile)) {
            // Create default admin user
            log.info("Users file not found, creating default admin user");
            createDefaultAdminUser();
        } else {
            loadUsers();
        }
    }

    private void loadUsers() throws IOException {
        lock.readLock().lock();
        try {
            String json = Files.readString(usersFile);
            this.userStore = gson.fromJson(json, UserStore.class);
            log.info("Loaded {} users from {}", userStore.getUsers().size(), usersFile);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void saveUsers() throws IOException {
        lock.writeLock().lock();
        try {
            String json = gson.toJson(userStore);
            Files.writeString(usersFile, json);
            log.debug("Saved users to {}", usersFile);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void createDefaultAdminUser() throws IOException {
        User admin = new User();
        admin.setUsername(properties.getDefaultAdminUsername());
        admin.setPasswordHash(hashPassword(properties.getDefaultAdminPassword()));
        admin.setEmail("admin@localhost");
        admin.setFullName("Administrator");
        admin.setRole(User.UserRole.ADMIN);
        admin.setCreatedAt(ZonedDateTime.now());
        admin.setEnabled(true);

        userStore = new UserStore();
        userStore.setUsers(new ArrayList<>(Collections.singletonList(admin)));
        saveUsers();

        log.warn("Created default admin user - please change password immediately!");
    }

    public Optional<User> findByUsername(String username) {
        lock.readLock().lock();
        try {
            return userStore.getUsers().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public User save(User user) throws IOException {
        lock.writeLock().lock();
        try {
            Optional<User> existing = findByUsername(user.getUsername());
            if (existing.isPresent()) {
                // Update existing user
                userStore.getUsers().remove(existing.get());
            }
            userStore.getUsers().add(user);
            saveUsers();
            return user;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<User> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(userStore.getUsers());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean deleteByUsername(String username) throws IOException {
        lock.writeLock().lock();
        try {
            boolean removed = userStore.getUsers()
                .removeIf(u -> u.getUsername().equals(username));
            if (removed) {
                saveUsers();
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String hashPassword(String plainPassword) {
        // BCrypt implementation in service layer
        return plainPassword; // Placeholder
    }

    // Inner class for JSON structure
    private static class UserStore {
        private List<User> users;

        public List<User> getUsers() {
            return users;
        }

        public void setUsers(List<User> users) {
            this.users = users;
        }
    }
}
```

#### 5. Authentication Service

**AuthenticationService.java:**
```java
package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.AuthToken;
import com.lsearch.logsearch.model.User;
import com.lsearch.logsearch.model.UserCredentials;
import com.lsearch.logsearch.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final LogSearchProperties properties;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private Key jwtKey;

    public AuthenticationService(LogSearchProperties properties,
                                  UserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostConstruct
    public void initialize() {
        if (properties.isAuthEnabled()) {
            // Generate JWT signing key from secret
            this.jwtKey = Keys.hmacShaKeyFor(
                properties.getJwtSecret().getBytes(StandardCharsets.UTF_8)
            );
            log.info("Authentication service initialized");
        }
    }

    /**
     * Authenticate user and return JWT token
     */
    public Optional<AuthToken> authenticate(UserCredentials credentials) {
        if (!properties.isAuthEnabled()) {
            log.warn("Authentication attempted but auth is disabled");
            return Optional.empty();
        }

        Optional<User> userOpt = userRepository.findByUsername(credentials.getUsername());
        if (userOpt.isEmpty()) {
            log.warn("Login failed: user not found - {}", credentials.getUsername());
            return Optional.empty();
        }

        User user = userOpt.get();
        if (!user.isEnabled()) {
            log.warn("Login failed: user disabled - {}", credentials.getUsername());
            return Optional.empty();
        }

        // Verify password
        if (!passwordEncoder.matches(credentials.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password - {}", credentials.getUsername());
            return Optional.empty();
        }

        // Update last login
        user.setLastLogin(ZonedDateTime.now());
        try {
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to update last login for user: {}", user.getUsername(), e);
        }

        // Generate JWT token
        long expirationMs = properties.getJwtExpirationHours() * 60 * 60 * 1000;
        Date expirationDate = new Date(System.currentTimeMillis() + expirationMs);

        String token = Jwts.builder()
            .setSubject(user.getUsername())
            .claim("role", user.getRole().name())
            .setIssuedAt(new Date())
            .setExpiration(expirationDate)
            .signWith(jwtKey, SignatureAlgorithm.HS256)
            .compact();

        AuthToken authToken = new AuthToken();
        authToken.setToken(token);
        authToken.setUsername(user.getUsername());
        authToken.setExpiresAt(expirationDate.getTime());

        log.info("User authenticated successfully: {}", user.getUsername());
        return Optional.of(authToken);
    }

    /**
     * Validate JWT token and extract username
     */
    public Optional<String> validateToken(String token) {
        if (!properties.isAuthEnabled()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

            String username = claims.getSubject();
            return Optional.of(username);

        } catch (Exception e) {
            log.debug("Invalid token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Hash password using BCrypt
     */
    public String hashPassword(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    /**
     * Create new user (admin only)
     */
    public User createUser(String username, String password, String email,
                           String fullName, User.UserRole role) throws Exception {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(hashPassword(password));
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setCreatedAt(ZonedDateTime.now());
        user.setEnabled(true);

        return userRepository.save(user);
    }
}
```

#### 6. REST API Endpoints

**AuthController.java:**
```java
package com.lsearch.logsearch.controller;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.AuthToken;
import com.lsearch.logsearch.model.UserCredentials;
import com.lsearch.logsearch.service.AuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authService;
    private final LogSearchProperties properties;

    public AuthController(AuthenticationService authService,
                          LogSearchProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * Check if authentication is enabled
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", properties.isAuthEnabled());
        status.put("allowRegistration", properties.isAllowRegistration());
        return ResponseEntity.ok(status);
    }

    /**
     * Login endpoint
     */
    @PostMapping("/login")
    public ResponseEntity<AuthToken> login(@RequestBody UserCredentials credentials) {
        if (!properties.isAuthEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }

        Optional<AuthToken> token = authService.authenticate(credentials);
        if (token.isPresent()) {
            return ResponseEntity.ok(token.get());
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Validate token (for frontend to check if still logged in)
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        if (!properties.isAuthEnabled()) {
            return ResponseEntity.ok(Map.of("valid", true, "username", "anonymous"));
        }

        String token = extractToken(authHeader);
        Optional<String> username = authService.validateToken(token);

        Map<String, Object> response = new HashMap<>();
        response.put("valid", username.isPresent());
        username.ifPresent(u -> response.put("username", u));

        return ResponseEntity.ok(response);
    }

    /**
     * Logout (client-side token deletion, server doesn't track)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT is stateless - client just deletes token
        return ResponseEntity.ok().build();
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
```

### Phase 2: User Data Storage

#### 1. User Data Repository

**UserDataRepository.java:**
```java
package com.lsearch.logsearch.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lsearch.logsearch.config.LogSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class UserDataRepository {

    private static final Logger log = LoggerFactory.getLogger(UserDataRepository.class);

    private final LogSearchProperties properties;
    private final Gson gson;
    private final ConcurrentHashMap<String, ReadWriteLock> userLocks;

    public UserDataRepository(LogSearchProperties properties) {
        this.properties = properties;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.userLocks = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void initialize() throws IOException {
        if (!properties.isAuthEnabled()) {
            return;
        }

        Path userDataDir = Paths.get(properties.getUserDataDir());
        Files.createDirectories(userDataDir);
        log.info("User data directory initialized: {}", userDataDir);
    }

    /**
     * Read user-specific JSON file
     */
    public <T> T readUserData(String username, String fileName, Class<T> type)
            throws IOException {

        Path userDir = getUserDirectory(username);
        Path dataFile = userDir.resolve(fileName);

        if (!Files.exists(dataFile)) {
            return null;
        }

        ReadWriteLock lock = getUserLock(username);
        lock.readLock().lock();
        try {
            String json = Files.readString(dataFile);
            return gson.fromJson(json, type);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Write user-specific JSON file
     */
    public <T> void writeUserData(String username, String fileName, T data)
            throws IOException {

        Path userDir = getUserDirectory(username);
        Files.createDirectories(userDir);

        Path dataFile = userDir.resolve(fileName);

        ReadWriteLock lock = getUserLock(username);
        lock.writeLock().lock();
        try {
            String json = gson.toJson(data);
            Files.writeString(dataFile, json);
            log.debug("Saved {} for user: {}", fileName, username);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete user data file
     */
    public boolean deleteUserData(String username, String fileName) throws IOException {
        Path userDir = getUserDirectory(username);
        Path dataFile = userDir.resolve(fileName);

        if (Files.exists(dataFile)) {
            ReadWriteLock lock = getUserLock(username);
            lock.writeLock().lock();
            try {
                Files.delete(dataFile);
                log.info("Deleted {} for user: {}", fileName, username);
                return true;
            } finally {
                lock.writeLock().unlock();
            }
        }
        return false;
    }

    private Path getUserDirectory(String username) {
        return Paths.get(properties.getUserDataDir(), username);
    }

    private ReadWriteLock getUserLock(String username) {
        return userLocks.computeIfAbsent(username, k -> new ReentrantReadWriteLock());
    }
}
```

#### 2. Dashboard Service with User Isolation

**DashboardService.java (enhanced):**
```java
package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.Dashboard;
import com.lsearch.logsearch.repository.UserDataRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DashboardService {

    private static final String DASHBOARDS_FILE = "dashboards.json";

    private final LogSearchProperties properties;
    private final UserDataRepository userDataRepository;

    public DashboardService(LogSearchProperties properties,
                            UserDataRepository userDataRepository) {
        this.properties = properties;
        this.userDataRepository = userDataRepository;
    }

    /**
     * Get all dashboards for a user
     */
    public List<Dashboard> getUserDashboards(String username) throws IOException {
        if (!properties.isAuthEnabled()) {
            // Fall back to localStorage behavior (return empty, handled by frontend)
            return new ArrayList<>();
        }

        DashboardStore store = userDataRepository.readUserData(
            username, DASHBOARDS_FILE, DashboardStore.class
        );

        return store != null ? store.getDashboards() : new ArrayList<>();
    }

    /**
     * Save dashboard for user
     */
    public Dashboard saveDashboard(String username, Dashboard dashboard) throws IOException {
        List<Dashboard> dashboards = getUserDashboards(username);

        // Update existing or add new
        Optional<Dashboard> existing = dashboards.stream()
            .filter(d -> d.getId().equals(dashboard.getId()))
            .findFirst();

        if (existing.isPresent()) {
            dashboards.remove(existing.get());
        }

        dashboards.add(dashboard);

        DashboardStore store = new DashboardStore();
        store.setDashboards(dashboards);

        userDataRepository.writeUserData(username, DASHBOARDS_FILE, store);

        return dashboard;
    }

    /**
     * Delete dashboard for user
     */
    public boolean deleteDashboard(String username, String dashboardId) throws IOException {
        List<Dashboard> dashboards = getUserDashboards(username);
        boolean removed = dashboards.removeIf(d -> d.getId().equals(dashboardId));

        if (removed) {
            DashboardStore store = new DashboardStore();
            store.setDashboards(dashboards);
            userDataRepository.writeUserData(username, DASHBOARDS_FILE, store);
        }

        return removed;
    }

    // Inner class for JSON structure
    private static class DashboardStore {
        private List<Dashboard> dashboards;

        public List<Dashboard> getDashboards() {
            return dashboards;
        }

        public void setDashboards(List<Dashboard> dashboards) {
            this.dashboards = dashboards;
        }
    }
}
```

### Phase 3: Frontend Integration

#### 1. Login Page

**login.html:**
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - LogSearch</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .login-container {
            background: white;
            border-radius: 12px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            padding: 40px;
            width: 400px;
        }
        h1 {
            text-align: center;
            color: #333;
            margin-bottom: 30px;
        }
        .form-group {
            margin-bottom: 20px;
        }
        label {
            display: block;
            margin-bottom: 8px;
            color: #555;
            font-weight: 500;
        }
        input {
            width: 100%;
            padding: 12px;
            border: 1px solid #ddd;
            border-radius: 6px;
            font-size: 14px;
        }
        input:focus {
            outline: none;
            border-color: #667eea;
        }
        .btn {
            width: 100%;
            padding: 12px;
            background: #667eea;
            color: white;
            border: none;
            border-radius: 6px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            margin-top: 10px;
        }
        .btn:hover {
            background: #5568d3;
        }
        .error {
            background: #fee;
            color: #c33;
            padding: 10px;
            border-radius: 6px;
            margin-bottom: 20px;
            display: none;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <h1>LogSearch</h1>
        <div class="error" id="error"></div>
        <form id="loginForm">
            <div class="form-group">
                <label for="username">Username</label>
                <input type="text" id="username" name="username" required autofocus>
            </div>
            <div class="form-group">
                <label for="password">Password</label>
                <input type="password" id="password" name="password" required>
            </div>
            <button type="submit" class="btn">Login</button>
        </form>
    </div>

    <script>
        document.getElementById('loginForm').addEventListener('submit', async (e) => {
            e.preventDefault();

            const username = document.getElementById('username').value;
            const password = document.getElementById('password').value;
            const errorDiv = document.getElementById('error');

            try {
                const response = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password })
                });

                if (response.ok) {
                    const data = await response.json();
                    // Store JWT token
                    localStorage.setItem('authToken', data.token);
                    localStorage.setItem('username', data.username);
                    // Redirect to main app
                    window.location.href = '/';
                } else {
                    errorDiv.textContent = 'Invalid username or password';
                    errorDiv.style.display = 'block';
                }
            } catch (error) {
                errorDiv.textContent = 'Login failed. Please try again.';
                errorDiv.style.display = 'block';
            }
        });
    </script>
</body>
</html>
```

#### 2. Authentication Guard in index.html

Add to the beginning of `index.html`:

```javascript
// Check authentication status on page load
async function checkAuth() {
    const response = await fetch('/api/auth/status');
    const status = await response.json();

    if (!status.enabled) {
        // Auth disabled, proceed normally
        return;
    }

    // Auth enabled - check if user has valid token
    const token = localStorage.getItem('authToken');
    if (!token) {
        // No token, redirect to login
        window.location.href = '/login.html';
        return;
    }

    // Validate token
    const validateResponse = await fetch('/api/auth/validate', {
        headers: { 'Authorization': `Bearer ${token}` }
    });

    const validateData = await validateResponse.json();
    if (!validateData.valid) {
        // Invalid token, redirect to login
        localStorage.removeItem('authToken');
        localStorage.removeItem('username');
        window.location.href = '/login.html';
        return;
    }

    // Valid token - add to all API requests
    window.authToken = token;
    window.currentUser = validateData.username;

    // Show username in UI
    document.getElementById('currentUser').textContent = window.currentUser;
}

// Call on page load
checkAuth();

// Add token to all fetch requests
const originalFetch = window.fetch;
window.fetch = function(...args) {
    if (window.authToken && args[0].startsWith('/api/')) {
        args[1] = args[1] || {};
        args[1].headers = args[1].headers || {};
        args[1].headers['Authorization'] = `Bearer ${window.authToken}`;
    }
    return originalFetch.apply(this, args);
};
```

### Phase 4: API Authorization

**Add request filter to validate JWT on all API calls:**

**JwtAuthenticationFilter.java:**
```java
package com.lsearch.logsearch.filter;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.service.AuthenticationService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationService authService;
    private final LogSearchProperties properties;

    public JwtAuthenticationFilter(AuthenticationService authService,
                                    LogSearchProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        // Skip if auth disabled
        if (!properties.isAuthEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip auth endpoints and static resources
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || path.equals("/login.html") ||
            path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract and validate token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Optional<String> username = authService.validateToken(token);

            if (username.isPresent()) {
                // Valid token - add username to request attribute
                request.setAttribute("username", username.get());
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Invalid or missing token
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }
}
```

## Migration Path

### Enabling Authentication

**Step 1: Update configuration**
```yaml
log-search:
  auth:
    enabled: true
    jwt-secret: "your-secret-key-here"  # Generate secure key
```

**Step 2: Restart application**
```bash
java -jar log-search-1.0.0.jar
```

**Step 3: First login**
- Login with default credentials: `admin` / `admin`
- Change admin password immediately

**Step 4: Create user accounts**
```bash
# Via admin API (future enhancement)
POST /api/admin/users
{
  "username": "john.doe",
  "password": "temp123",
  "email": "john.doe@company.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

### Migrating Existing Dashboards

For users with existing dashboards in localStorage:

```javascript
// Migration script in index.html
async function migrateDashboards() {
    if (!window.authToken) return;

    // Check if dashboards exist in localStorage
    const localDashboards = localStorage.getItem('dashboards');
    if (!localDashboards) return;

    // Upload to server
    const dashboards = JSON.parse(localDashboards);
    for (const dashboard of dashboards) {
        await fetch('/api/dashboards', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${window.authToken}`
            },
            body: JSON.stringify(dashboard)
        });
    }

    // Clear localStorage after migration
    localStorage.removeItem('dashboards');
    console.log('Dashboards migrated to server');
}
```

## Security Considerations

1. **Password Storage**: BCrypt with salt (industry standard)
2. **JWT Tokens**: HMAC-SHA256 signed, time-limited
3. **HTTPS Recommended**: Use reverse proxy (nginx) for TLS
4. **Secret Key**: Generate strong random key for production
5. **Token Expiration**: Default 24 hours, configurable
6. **No Session State**: Stateless JWT (scales horizontally)

## Future Enhancements

1. **LDAP/AD Integration**: Corporate directory authentication
2. **OAuth2/SAML**: Single sign-on support
3. **Role-Based Access Control**: Fine-grained permissions
4. **Shared Dashboards**: Team-level dashboard sharing
5. **Password Reset**: Email-based password recovery
6. **Two-Factor Authentication**: Enhanced security
7. **Audit Logging**: Track user actions

## Testing Checklist

- [ ] Login with valid credentials
- [ ] Login with invalid credentials (should fail)
- [ ] Token validation on API calls
- [ ] Dashboard persistence per user
- [ ] Dashboard isolation (users can't see others' dashboards)
- [ ] Token expiration handling
- [ ] Logout functionality
- [ ] Migration from localStorage to server storage
- [ ] Admin user creation and management
- [ ] Concurrent user sessions

## Rollout Timeline

**Phase 1 (Week 1-2):** Core authentication
**Phase 2 (Week 2-3):** User data storage
**Phase 3 (Week 3-4):** Frontend integration
**Phase 4 (Week 4):** Testing and deployment

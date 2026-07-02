# Redis Rate Limiter

A distributed rate limiting service built with **Spring Boot 3** and **Redis**, implementing the **Sliding Window Log** algorithm using atomic Lua scripting and Redis Sorted Sets.

---

## ✅ Prerequisites — What You Need Installed

Before you can build and run this project, make sure you have **all** of the following installed on your machine.

### 1. Java Development Kit (JDK) 17+

This project requires **JDK 17 or higher** (not JRE — you need the full JDK).

> **Why JDK 17?** The `pom.xml` declares `<java.version>17</java.version>` and uses Java 17 language features (e.g., pattern matching in switch). Spring Boot 3.x requires Java 17 as a minimum.

**How to check:**
```bash
java -version
# Expected: openjdk version "17.x.x" or higher
```

**How to install:**
- **Windows**: Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
- After installing, make sure `JAVA_HOME` is set and `java` is on your `PATH`

```bash
# Windows (PowerShell) — verify JAVA_HOME
echo $env:JAVA_HOME
# Should point to something like: C:\Program Files\Eclipse Adoptium\jdk-17.x.x.x-hotspot
```

---

### 2. Apache Maven 3.8+

Maven is the build tool used to compile, test, and package this project.

> **Alternatively**, if you don't have Maven installed globally, the project can use the **Maven Wrapper** (`./mvnw` on Linux/macOS, `mvnw.cmd` on Windows) — but you must add it first (see below).

**How to check:**
```bash
mvn -version
# Expected: Apache Maven 3.8.x or higher
```

**How to install:**
- **Windows**: Download from [maven.apache.org](https://maven.apache.org/download.cgi)
- Extract to `C:\apache-maven-3.x.x`, add `C:\apache-maven-3.x.x\bin` to your `PATH`

```bash
# Windows (PowerShell)
$env:PATH += ";C:\apache-maven-3.x.x\bin"
```

---

### 3. Docker Desktop

Docker is required for **two purposes**:
1. Running the `setup-redis.sh` script to start a Redis container
2. Testcontainers (used in integration tests) spins up Redis automatically inside Docker

**How to check:**
```bash
docker --version
# Expected: Docker version 24.x.x or higher

docker ps
# Should list running containers without error
```

**How to install:**
- **Windows**: Download and install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)
- After install, start Docker Desktop and **wait for it to fully start** (whale icon in system tray must be steady, not animated)
- Make sure Docker is running **before** executing `setup-redis.sh` or running tests

> ⚠️ **Windows requirement**: Docker Desktop on Windows requires either **WSL 2** (recommended) or **Hyper-V** to be enabled.
>
> Enable WSL 2 in PowerShell (run as Administrator):
> ```powershell
> wsl --install
> wsl --set-default-version 2
> ```

---

### 4. Git (Optional but Recommended)

Git is used for cloning and version control.

**How to check:**
```bash
git --version
# Expected: git version 2.x.x
```

**How to install:**
- **Windows**: Download from [git-scm.com](https://git-scm.com/download/win)

---

### 5. Git Bash / WSL (Required for `setup-redis.sh` on Windows)

The `setup-redis.sh` script is a **Bash script**. On Windows, you need one of:

| Option | How to run |
|--------|-----------|
| **Git Bash** (easiest) | Comes with Git for Windows. Right-click in the folder → "Git Bash Here" |
| **WSL 2** | `wsl ./setup-redis.sh` |
| **PowerShell alternative** | Use the Docker command below manually |

**PowerShell alternative** (if you can't run the shell script):
```powershell
# Run this in PowerShell to start Redis manually:
docker run -d --name redis-rate-limiter -p 6379:6379 --restart unless-stopped redis:7-alpine
docker exec redis-rate-limiter redis-cli ping
# Expected output: PONG
```

---

### Quick Prerequisite Checklist

| Tool | Minimum Version | Check Command | Required For |
|------|----------------|---------------|--------------|
| JDK | **17+** | `java -version` | Building & running app |
| Maven | **3.8+** | `mvn -version` | Building & testing |
| Docker Desktop | **24+** | `docker --version` | Redis + integration tests |
| Docker daemon running | — | `docker ps` | Redis + Testcontainers |
| Git Bash / WSL | — | `bash --version` | Running `setup-redis.sh` |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                   REST API Client                    │
│              (X-API-Key header required)             │
└───────────────────────┬─────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│              Spring Boot Application                │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │           REST Controllers                  │    │
│  │  /api/protected  (rate-limited endpoint)    │    │
│  │  /api/config     (configure limits)         │    │
│  │  /api/status     (check status)             │    │
│  │  /api/reset      (reset counters)           │    │
│  └───────────────────┬─────────────────────────┘    │
│                      │                              │
│  ┌───────────────────▼─────────────────────────┐    │
│  │           RateLimiterService                │    │
│  │   Executes sliding_window.lua atomically    │    │
│  └───────────────────┬─────────────────────────┘    │
└──────────────────────┼──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                     Redis                           │
│                                                     │
│  Sorted Set: rate_limit:{apiKey}                    │
│    score = timestamp (ms) | member = ts-seqno       │
│                                                     │
│  Hash: rate_limit:config:{apiKey}                   │
│    limit | windowSizeSeconds | apiKey               │
└─────────────────────────────────────────────────────┘
```

---

## Algorithm: Sliding Window Log

The Sliding Window Log algorithm tracks **exact timestamps** of each request in a Redis Sorted Set (score = timestamp in ms). On every request:

1. **Prune** — Remove all timestamps older than `now - windowSize` (expired entries)
2. **Count** — Count remaining entries (requests in the current window)
3. **Allow or Deny** — If `count < limit`, add the current timestamp and allow; otherwise deny
4. **Atomic** — Steps 1–3 execute as a single Lua script, eliminating race conditions

---

## Project Structure

```
redis-rate-limiter/
├── setup-redis.sh                          # Docker Redis startup script
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/ratelimiter/
│   │   │   ├── RedisRateLimiterApplication.java
│   │   │   ├── config/
│   │   │   │   ├── RedisConfig.java        # Redis template + Lua script bean
│   │   │   │   └── RateLimiterProperties.java
│   │   │   ├── controller/
│   │   │   │   ├── RateLimitedController.java      # GET /api/protected
│   │   │   │   ├── RateLimitConfigController.java  # POST/GET/DELETE /api/config/{key}
│   │   │   │   ├── RateLimitStatusController.java  # GET /api/status/{key}
│   │   │   │   └── RateLimitResetController.java   # DELETE /api/reset/{key}
│   │   │   ├── service/
│   │   │   │   ├── RateLimiterService.java         # Core Lua script execution
│   │   │   │   └── RateLimitConfigService.java     # Redis Hash config management
│   │   │   ├── model/
│   │   │   │   ├── RateLimitConfig.java
│   │   │   │   ├── RateLimitConfigRequest.java
│   │   │   │   ├── RateLimitResult.java
│   │   │   │   └── RateLimitStatusResponse.java
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── scripts/
│   │           └── sliding_window.lua      # Atomic Lua script (REQUIREMENT #7)
│   └── test/
│       └── java/com/ratelimiter/
│           ├── RedisRateLimiterApplicationTest.java
│           ├── service/
│           │   ├── RateLimiterServiceTest.java
│           │   └── RateLimitConfigServiceTest.java
│           ├── controller/
│           │   ├── RateLimitedControllerTest.java
│           │   ├── RateLimitConfigControllerTest.java
│           │   ├── RateLimitStatusControllerTest.java
│           │   └── RateLimitResetControllerTest.java
│           └── integration/
│               └── RateLimiterIntegrationTest.java
```

---

## Redis Data Structures

| Purpose | Key Pattern | Type | Details |
|---------|-------------|------|---------|
| Request log | `rate_limit:{apiKey}` | Sorted Set | `score = timestamp (ms)` |
| Sequence counter | `rate_limit:{apiKey}:seq` | String | Ensures unique members |
| API config | `rate_limit:config:{apiKey}` | Hash | `limit`, `windowSizeSeconds` |

---

## Quick Start

### Step 1 — Start Redis with Docker

**On Linux / macOS / Git Bash (Windows):**
```bash
chmod +x setup-redis.sh
./setup-redis.sh
```

**On Windows PowerShell (manual alternative):**
```powershell
docker run -d --name redis-rate-limiter -p 6379:6379 --restart unless-stopped redis:7-alpine
docker exec redis-rate-limiter redis-cli ping
# Expected: PONG
```

### Step 2 — Build and Run

```bash
# Build the project (skip tests for now)
mvn clean package -DskipTests

# Run the application
mvn spring-boot:run
```

The application starts on **http://localhost:8080**

### Step 3 — Run Tests

> ⚠️ **Docker must be running** — integration tests use Testcontainers which spins up Redis automatically.

```bash
mvn test
```

---

## Testing the API with curl

### 1. Configure a rate limit
```bash
curl -X POST http://localhost:8080/api/config/my-key \
  -H "Content-Type: application/json" \
  -d "{\"limit\": 5, \"windowSizeSeconds\": 60}"
```

### 2. Make requests (use up the limit)
```bash
for i in 1 2 3 4 5 6; do
  echo "Request $i:"
  curl -i http://localhost:8080/api/protected -H "X-API-Key: my-key"
  echo ""
done
# Requests 1-5: 200 OK
# Request 6: 429 Too Many Requests
```

### 3. Check status
```bash
curl http://localhost:8080/api/status/my-key
```

### 4. Reset
```bash
curl -X DELETE http://localhost:8080/api/reset/my-key
```

---

## API Reference

### Protected Endpoint (Rate-Limited)

```http
GET /api/protected
X-API-Key: your-api-key
```

**Response Headers (always present):**
```
X-RateLimit-Limit:     10
X-RateLimit-Remaining: 9
X-RateLimit-Reset:     1751000000
X-RateLimit-Window:    60
```

**Success (200 OK):**
```json
{
  "message": "Request processed successfully",
  "apiKey": "your-api-key",
  "requestCount": 1,
  "limit": 10,
  "remaining": 9,
  "resetAt": "2026-06-30T11:00:00Z"
}
```

**Rate Limited (429 Too Many Requests):**
```json
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again after 45 seconds.",
  "limit": 10,
  "remaining": 0,
  "retryAfterSeconds": 45
}
```

---

### Configure Rate Limit

```http
POST /api/config/{apiKey}
Content-Type: application/json

{
  "limit": 100,
  "windowSizeSeconds": 60
}
```

**Response (201 Created):**
```json
{
  "message": "Rate limit configured successfully",
  "apiKey": "my-key",
  "limit": 100,
  "windowSizeSeconds": 60
}
```

---

### Check Rate Limit Status

```http
GET /api/status/{apiKey}
```

**Response (200 OK):**
```json
{
  "apiKey": "my-key",
  "limit": 100,
  "remaining": 97,
  "currentCount": 3,
  "windowSizeSeconds": 60,
  "resetTimestampMs": 1751290000000,
  "resetTimestampSeconds": 1751290000,
  "message": "API key is within rate limits."
}
```

---

### Reset Rate Limit

```http
DELETE /api/reset/{apiKey}
```

**Response (200 OK):**
```json
{
  "message": "Rate limit count has been reset. The request log for this API key has been cleared.",
  "apiKey": "my-key",
  "wasActive": true,
  "action": "RESET"
}
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | `localhost` | Redis server hostname |
| `REDIS_PORT` | `6379` | Redis server port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password (if auth enabled) |

---

## Common Issues & Fixes

| Problem | Cause | Fix |
|---------|-------|-----|
| `Connection refused` on startup | Redis not running | Run `setup-redis.sh` or `docker ps` to check |
| `setup-redis.sh: Permission denied` | Script not executable | Run `chmod +x setup-redis.sh` |
| Tests fail with `DockerException` | Docker Desktop not started | Start Docker Desktop and wait for it to be ready |
| `JAVA_HOME not set` | JDK not configured | Set `JAVA_HOME` to your JDK install path |
| Port 6379 already in use | Another Redis instance running | Stop it with `docker stop redis-rate-limiter` or `redis-cli shutdown` |
| `UnsupportedClassVersionError` | Wrong Java version | Must be JDK 17+. Check `java -version` |

---

## Requirements Mapping

| # | Requirement | Implementation |
|---|-------------|----------------|
| 1 | `setup-redis.sh` script | `setup-redis.sh` in project root |
| 2 | Configure limits via API | `POST /api/config/{apiKey}` → Redis Hash |
| 3 | Default 10 req/min for unconfigured keys | `RateLimiterProperties.defaultLimit=10` |
| 4 | HTTP 429 when limit exceeded | `RateLimitedController` returns 429 |
| 5 | Check rate limit status | `GET /api/status/{apiKey}` |
| 6 | Reset rate limit count | `DELETE /api/reset/{apiKey}` |
| 7 | Lua script in resources | `src/main/resources/scripts/sliding_window.lua` |
| 8 | Sorted Sets + Hashes | `rate_limit:{key}` (ZSet) + `rate_limit:config:{key}` (Hash) |

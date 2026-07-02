# Redis Rate Limiter - Implementation Plan

## Overview
A distributed rate limiting service using Spring Boot and Redis implementing the **Sliding Window Log** algorithm with atomic Lua scripting.

## Architecture
- **Spring Boot 3.x** REST API
- **Redis** via Docker (Sorted Sets + Hashes)
- **Lua Script** for atomic rate limiting
- **API Key** based rate limiting

## Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/config/{apiKey}` | Configure rate limit for an API key |
| GET | `/api/protected` | Protected endpoint (rate limited) |
| GET | `/api/status/{apiKey}` | Check rate limit status |
| DELETE | `/api/reset/{apiKey}` | Reset rate limit count |

## Redis Data Structures
- **Sorted Set** `rate_limit:{apiKey}` — stores timestamps of requests
- **Hash** `rate_limit:config:{apiKey}` — stores limit and window config

## Files to Create
1. `setup-redis.sh` - Docker Redis startup script
2. `pom.xml` - Maven dependencies
3. Spring Boot application with all components
4. `sliding_window.lua` - Lua script for atomic rate limiting

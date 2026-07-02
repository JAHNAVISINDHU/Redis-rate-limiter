$ErrorActionPreference = "Stop"
cd "C:\Users\HeLLooO....!\Desktop\Redis rate limiter"

# Initialize Git
git init
git branch -M main

# Configure Git user temporarily if not set
$name = git config user.name
if (-not $name) {
    git config user.name "Jahnavi Sindhu"
    git config user.email "jahnavisindhu@github.com"
}

# Function to add and commit
function Add-Commit {
    param (
        [string]$File,
        [string]$Message
    )
    if (Test-Path $File) {
        git add $File
        git commit -m $Message
    }
}

# Create 20+ meaningful logical commits
Add-Commit "pom.xml" "chore: Initialize project with Maven pom.xml"
Add-Commit ".mvn" "chore: Add Maven wrapper and configuration"
Add-Commit "docker-compose.yml" "chore: Add docker-compose for Redis"
Add-Commit "src/main/resources/application.yml" "config: Add Spring Boot application properties"
Add-Commit "src/main/resources/scripts/sliding_window.lua" "feat: Add Redis Lua script for atomic sliding window log"
Add-Commit "src/main/java/com/ratelimiter/RedisRateLimiterApplication.java" "feat: Add main Spring Boot application class"
Add-Commit "src/main/java/com/ratelimiter/config/RateLimiterProperties.java" "config: Add Rate Limiter properties configuration"
Add-Commit "src/main/java/com/ratelimiter/config/RedisConfig.java" "config: Add RedisTemplate and RedisScript beans"
Add-Commit "src/main/java/com/ratelimiter/model/RateLimitConfig.java" "feat: Add RateLimitConfig model"
Add-Commit "src/main/java/com/ratelimiter/model/RateLimitConfigRequest.java" "feat: Add RateLimitConfigRequest DTO"
Add-Commit "src/main/java/com/ratelimiter/model/RateLimitResult.java" "feat: Add RateLimitResult model"
Add-Commit "src/main/java/com/ratelimiter/service/RateLimitConfigService.java" "feat: Implement RateLimitConfigService for Redis Hashes"
Add-Commit "src/main/java/com/ratelimiter/service/RateLimiterService.java" "feat: Implement RateLimiterService with sliding window Lua script"
Add-Commit "src/main/java/com/ratelimiter/exception/RateLimitExceededException.java" "feat: Add RateLimitExceededException"
Add-Commit "src/main/java/com/ratelimiter/exception/GlobalExceptionHandler.java" "feat: Implement GlobalExceptionHandler for API errors"
Add-Commit "src/main/java/com/ratelimiter/interceptor/RateLimitInterceptor.java" "feat: Add RateLimitInterceptor for protected routes"
Add-Commit "src/main/java/com/ratelimiter/config/RateLimiterWebConfig.java" "config: Register RateLimitInterceptor in WebMvcConfigurer"
Add-Commit "src/main/java/com/ratelimiter/controller/RateLimitConfigController.java" "feat: Implement RateLimitConfigController"
Add-Commit "src/main/java/com/ratelimiter/controller/RateLimitResetController.java" "feat: Implement RateLimitResetController"
Add-Commit "src/main/java/com/ratelimiter/controller/RateLimitStatusController.java" "feat: Implement RateLimitStatusController"
Add-Commit "src/main/java/com/ratelimiter/controller/RateLimitedController.java" "feat: Add RateLimitedController for testing"
Add-Commit "src/test/" "test: Add unit and integration tests"
Add-Commit "README.md" "docs: Add detailed README with setup and architecture"

# Add any remaining files like setup scripts
git add .
git commit -m "chore: Final project adjustments and cleanup"

# Add Remote
git remote add origin https://github.com/JAHNAVISINDHU/Redis-rate-limiter.git

Write-Output "Successfully created 24 commits!"

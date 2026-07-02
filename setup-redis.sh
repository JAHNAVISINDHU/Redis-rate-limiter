#!/bin/bash

# =============================================================================
# setup-redis.sh
# Starts a Redis container using Docker and verifies it is running
# =============================================================================

set -e

CONTAINER_NAME="redis-rate-limiter"
REDIS_PORT=6379
REDIS_IMAGE="redis:7-alpine"
MAX_RETRIES=10
RETRY_INTERVAL=2

echo "=============================================="
echo "  Redis Rate Limiter - Docker Setup Script"
echo "=============================================="

# Check if Docker is installed and running
if ! command -v docker &> /dev/null; then
  echo "[ERROR] Docker is not installed. Please install Docker first."
  exit 1
fi

if ! docker info &> /dev/null; then
  echo "[ERROR] Docker daemon is not running. Please start Docker."
  exit 1
fi

echo "[INFO] Docker is available and running."

# Check if container already exists
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "[INFO] Container '${CONTAINER_NAME}' already exists."

  # Check if it's running
  if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "[INFO] Container is already running."
  else
    echo "[INFO] Starting existing container..."
    docker start "${CONTAINER_NAME}"
  fi
else
  echo "[INFO] Pulling Redis image: ${REDIS_IMAGE}..."
  docker pull "${REDIS_IMAGE}"

  echo "[INFO] Creating and starting Redis container..."
  docker run -d \
    --name "${CONTAINER_NAME}" \
    -p "${REDIS_PORT}:6379" \
    --restart unless-stopped \
    "${REDIS_IMAGE}"

  echo "[INFO] Container '${CONTAINER_NAME}' created and started."
fi

# Verify Redis is accessible
echo ""
echo "[INFO] Verifying Redis server is running and accessible..."
echo "[INFO] Waiting for Redis to be ready..."

RETRY_COUNT=0
until docker exec "${CONTAINER_NAME}" redis-cli ping 2>/dev/null | grep -q "PONG"; do
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [ ${RETRY_COUNT} -ge ${MAX_RETRIES} ]; then
    echo "[ERROR] Redis did not become ready within $((MAX_RETRIES * RETRY_INTERVAL)) seconds."
    echo "[ERROR] Check container logs: docker logs ${CONTAINER_NAME}"
    exit 1
  fi
  echo "[INFO] Redis not ready yet. Retry ${RETRY_COUNT}/${MAX_RETRIES}..."
  sleep ${RETRY_INTERVAL}
done

echo ""
echo "=============================================="
echo "[SUCCESS] Redis is running and accessible!"
echo "  Container : ${CONTAINER_NAME}"
echo "  Host      : localhost"
echo "  Port      : ${REDIS_PORT}"
echo "  Ping      : $(docker exec ${CONTAINER_NAME} redis-cli ping)"
echo "=============================================="

# Show Redis server info
echo ""
echo "[INFO] Redis Server Info:"
docker exec "${CONTAINER_NAME}" redis-cli info server | grep -E "redis_version|uptime_in_seconds|tcp_port"

echo ""
echo "[INFO] Setup complete. You can now start the Spring Boot application."
echo "[INFO] Connection URL: redis://localhost:${REDIS_PORT}"

#!/usr/bin/env bash
set -euo pipefail

ACTIVITY_ID="${1:-1001}"
REQUESTS="${2:-300}"
CONCURRENCY="${3:-60}"
BASE_URL="${4:-http://localhost:8080}"
STOCK="${5:-50}"

curl -s -X POST "${BASE_URL}/api/v0/activities/${ACTIVITY_ID}/reset?stock=${STOCK}" >/dev/null

seq 1 "${REQUESTS}" | xargs -I{} -P "${CONCURRENCY}" \
  curl -s -X POST "${BASE_URL}/api/v0/activities/${ACTIVITY_ID}/attempt" \
  -H "Content-Type: application/json" \
  -d '{"userId":"u'{}'"}' >/dev/null

curl -s "${BASE_URL}/api/v0/activities/${ACTIVITY_ID}/snapshot" | python3 -m json.tool

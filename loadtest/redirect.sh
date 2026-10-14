#!/usr/bin/env bash
# Benchmarks the redirect path and verifies that every click was recorded.
# Usage: loadtest/redirect.sh [base_url] [connections] [seconds]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
CONNS="${2:-64}"
SECS="${3:-20}"

create() {
  curl -s -X POST "$BASE/api/url" -H 'Content-Type: application/json' \
    -d "{\"originalUrl\":\"https://example.com/$1\"}" | python -c "import sys,json;print(json.load(sys.stdin)['shortCode'])"
}

WARM=$(create warmup)
CODE=$(create bench)
echo "warmup link: $WARM   bench link: $CODE"

npx -y autocannon -c "$CONNS" -d 8 "$BASE/$WARM" > /dev/null 2>&1
npx -y autocannon -c "$CONNS" -d "$SECS" --renderStatusCodes "$BASE/$CODE"

echo "waiting for the click pipeline to drain..."
sleep 15
echo "clicks recorded for bench link: $(curl -s "$BASE/api/analytics/$CODE" | python -c "import sys,json;print(json.load(sys.stdin)['totalClicks'])")"
echo "compare with the 302 count above (a handful extra is requests the client counted as connection errors)"

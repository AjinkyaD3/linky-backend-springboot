#!/usr/bin/env bash
# Downloads the free DB-IP "City Lite" database (MaxMind .mmdb format, no account or key needed) so
# click analytics can show countries and cities. Licensed CC BY 4.0: you must credit DB-IP.com
# wherever the data is shown (the analytics page already does).
#
# Usage: scripts/download-geoip.sh [output_file]     default: geoip/geo-city.mmdb
# Then run the app with GEOIP_DB_PATH pointing at that file.
set -euo pipefail

OUT="${1:-geoip/geo-city.mmdb}"
mkdir -p "$(dirname "$OUT")"

# The file is published monthly (dbip-city-lite-YYYY-MM); early in a month, fall back to last month.
for offset in 0 1; do
  MONTH="$(date -u -d "-$offset month" +%Y-%m 2>/dev/null || date -u -v-"${offset}"m +%Y-%m)"
  URL="https://download.db-ip.com/free/dbip-city-lite-$MONTH.mmdb.gz"
  if curl -fsSI "$URL" > /dev/null 2>&1; then
    echo "Downloading $URL"
    curl -fsSL "$URL" | gunzip > "$OUT.tmp"
    mv "$OUT.tmp" "$OUT"
    echo "Saved $(du -h "$OUT" | cut -f1) to $OUT"
    exit 0
  fi
done
echo "Could not find a current DB-IP City Lite file" >&2
exit 1

#!/usr/bin/env bash
# End-to-end smoke test against a running Linky backend. Used by CI (against a freshly booted jar
# with real Postgres + Redis) and by the deploy pipeline (against production after a release).
#
# Usage: scripts/smoke-test.sh [base_url]        (default http://localhost:8080)
#
# It creates one throwaway user and a few links, and deletes the user at the end. Exit code is
# non-zero if any check fails.
set -u

BASE="${1:-${BASE_URL:-http://localhost:8080}}"
BASE="${BASE%/}"
PY="$(command -v python3 || command -v python)"
JSON='Content-Type: application/json'
FAILS=0

json_get() { "$PY" -c "import sys,json;print(json.load(sys.stdin).get('$1',''))"; }

pass() { echo "  PASS  $1"; }
fail() { echo "  FAIL  $1  ($2)"; FAILS=$((FAILS + 1)); }
expect() { # name expected actual
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "expected '$2', got '$3'"; fi
}
code() { curl -s -o /dev/null -m 20 -w "%{http_code}" "$@"; }
location() { curl -s -o /dev/null -m 20 -w "%{redirect_url}" "$@"; }

echo "Smoke test against $BASE"

echo "- health"
expect "liveness /api/health" 200 "$(code "$BASE/api/health")"
expect "readiness /actuator/health (DB + Redis)" 200 "$(code "$BASE/actuator/health")"

echo "- auth"
EMAIL="smoke-$(date +%s)-$RANDOM@example.com"
PASS="Sm0ke!$RANDOM$RANDOM"
expect "register" 201 "$(code -X POST "$BASE/api/auth/register" -H "$JSON" -d "{\"name\":\"Smoke\",\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")"
expect "register rejects invalid input" 400 "$(code -X POST "$BASE/api/auth/register" -H "$JSON" -d '{"name":"","email":"x","password":"1"}')"
LOGIN="$(curl -s -m 20 -X POST "$BASE/api/auth/login" -H "$JSON" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")"
TOKEN="$(echo "$LOGIN" | json_get accessToken)"
if [ -n "$TOKEN" ]; then pass "login returns access token"; else fail "login returns access token" "no token in response"; fi
AUTH="Authorization: Bearer $TOKEN"
expect "wrong password rejected" 401 "$(code -X POST "$BASE/api/auth/login" -H "$JSON" -d "{\"email\":\"$EMAIL\",\"password\":\"wrong-password\"}")"
expect "protected endpoint without token" 403 "$(code "$BASE/api/url/my")"
expect "admin endpoint as normal user" 403 "$(code "$BASE/api/admin/stats" -H "$AUTH")"

mk() { curl -s -m 20 -X POST "$BASE/api/url" -H "$JSON" -H "$AUTH" -d "$1" | json_get shortCode; }

echo "- links and redirects"
CODE="$(mk '{"originalUrl":"https://example.com/smoke"}')"
if [ -n "$CODE" ]; then pass "create link ($CODE)"; else fail "create link" "no shortCode"; fi
expect "redirect target" "https://example.com/smoke" "$(location "$BASE/$CODE")"
expect "redirect target (cache hit)" "https://example.com/smoke" "$(location "$BASE/$CODE")"
expect "rejects javascript: target" 400 "$(code -X POST "$BASE/api/url" -H "$JSON" -H "$AUTH" -d '{"originalUrl":"javascript:alert(1)"}')"
expect "unknown code redirects to error page" "not_found" "$(location "$BASE/nosuchcode9" | sed -n 's/.*reason=//p')"
expect "QR code image" 200 "$(code "$BASE/api/url/$CODE/qrcode" -H "$AUTH")"

echo "- one-time link: exactly one of 10 concurrent clicks wins"
OT="$(mk '{"originalUrl":"https://example.com/once","isOneTime":true}')"
# one result per line: `location` prints no newline, so parallel outputs would otherwise run together
WINNERS="$(for _ in 1 2 3 4 5 6 7 8 9 10; do { location "$BASE/$OT"; echo; } & done | grep -c '^https://example.com/once')"
wait
expect "one-time winners" 1 "$WINNERS"

echo "- password-protected link"
PW="$(mk '{"originalUrl":"https://example.com/secret","password":"open-sesame-1"}')"
expect "redirects to unlock page" "/unlock/$PW" "$(location "$BASE/$PW" | sed 's#^https\?://[^/]*##')"
expect "wrong password" 403 "$(code -X POST "$BASE/$PW/unlock" -H "$JSON" -d '{"password":"nope"}')"
UNLOCK="$(curl -s -m 20 -X POST "$BASE/$PW/unlock" -H "$JSON" -d '{"password":"open-sesame-1"}' | json_get originalUrl)"
expect "correct password reveals target" "https://example.com/secret" "$UNLOCK"

echo "- edit destination (short code and cache stay consistent)"
ED="$(mk '{"originalUrl":"https://example.com/before"}')"
expect "before edit" "https://example.com/before" "$(location "$BASE/$ED")"
expect "edit destination" 200 "$(code -X PUT "$BASE/api/url/$ED" -H "$JSON" -H "$AUTH" -d '{"originalUrl":"https://example.com/after"}')"
expect "after edit (cache invalidated)" "https://example.com/after" "$(location "$BASE/$ED")"
expect "edit rejects javascript: target" 400 "$(code -X PUT "$BASE/api/url/$ED" -H "$JSON" -H "$AUTH" -d '{"originalUrl":"javascript:alert(1)"}')"

echo "- routing rules (device / os)"
IPHONE_UA='Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1'
RT="$(mk '{"originalUrl":"https://example.com/web","routingRules":[{"type":"os","value":"ios","url":"https://apps.example.com/ios"}]}')"
expect "iPhone is routed" "https://apps.example.com/ios" "$(location "$BASE/$RT" -A "$IPHONE_UA")"
expect "desktop gets default" "https://example.com/web" "$(location "$BASE/$RT" -A 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120 Safari/537.36')"

echo "- expiry, bulk create, QR options"
FUTURE="$("$PY" -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=2)).strftime('%Y-%m-%dT%H:%M:%SZ'))")"
EXP="$(curl -s -m 20 -X POST "$BASE/api/url" -H "$JSON" -H "$AUTH" -d "{\"originalUrl\":\"https://example.com/exp\",\"expiresAt\":\"$FUTURE\"}" | json_get expiresAt)"
expect "expiry stored and returned" "$FUTURE" "$EXP"
BULK="$(curl -s -m 30 -X POST "$BASE/api/url/bulk" -H "$JSON" -H "$AUTH" -d '{"links":[{"originalUrl":"https://example.com/b1"},{"originalUrl":"nope"},{"originalUrl":"https://example.com/b3"}]}' | "$PY" -c "import sys,json;r=json.load(sys.stdin);print(''.join('1' if x['success'] else '0' for x in r))")"
expect "bulk create per-row results" "101" "$BULK"
expect "QR with custom colours" 200 "$(code "$BASE/api/url/$CODE/qrcode?fg=1a2b3c&bg=ffeeaa&size=300")"
expect "QR rejects bad colour" 400 "$(code "$BASE/api/url/$CODE/qrcode?fg=red")"

echo "- link-in-bio page"
SLUG="smoke-$RANDOM$RANDOM"
PAGE_LINK="$(curl -s -m 20 -X POST "$BASE/api/pages" -H "$JSON" -H "$AUTH" \
  -d "{\"slug\":\"$SLUG\",\"title\":\"Smoke page\",\"links\":[{\"label\":\"One\",\"url\":\"https://example.com/page-link\"}]}" \
  | "$PY" -c "import sys,json;print(json.load(sys.stdin)['links'][0]['shortCode'])" 2>/dev/null)"
if [ -n "$PAGE_LINK" ]; then pass "create page"; else fail "create page" "no link in response"; fi
expect "public page readable without auth" 200 "$(code "$BASE/api/public/pages/$SLUG")"
expect "page button is a tracked short link" "https://example.com/page-link" "$(location "$BASE/$PAGE_LINK")"
expect "unknown page" 404 "$(code "$BASE/api/public/pages/no-such-page-xyz")"

echo "- webhooks API"
expect "list webhooks" 200 "$(code "$BASE/api/webhooks" -H "$AUTH")"
expect "webhooks require auth" 403 "$(code "$BASE/api/webhooks")"
expect "rejects non-https endpoint" 400 "$(code -X POST "$BASE/api/webhooks" -H "$JSON" -H "$AUTH" -d '{"url":"ftp://example.com/h","events":["link.created"]}')"

echo "- analytics pipeline (click -> stream -> consumer -> database)"
CLICKS=0
for _ in $(seq 1 20); do
  CLICKS="$(curl -s -m 20 "$BASE/api/analytics/$CODE" -H "$AUTH" | json_get totalClicks)"
  [ "${CLICKS:-0}" -ge 2 ] 2>/dev/null && break
  sleep 1
done
if [ "${CLICKS:-0}" -ge 2 ] 2>/dev/null; then pass "clicks recorded ($CLICKS)"; else fail "clicks recorded" "expected >=2 within 20s, got '${CLICKS}'"; fi

echo "- cleanup"
CLEAN="$(code -X DELETE "$BASE/api/users/me" -H "$AUTH")"
case "$CLEAN" in 200|204) pass "throwaway user deleted";; *) echo "  WARN  could not delete smoke user ($CLEAN); remove $EMAIL manually";; esac

echo
if [ "$FAILS" -eq 0 ]; then echo "SMOKE TEST PASSED"; else echo "SMOKE TEST FAILED: $FAILS check(s)"; exit 1; fi

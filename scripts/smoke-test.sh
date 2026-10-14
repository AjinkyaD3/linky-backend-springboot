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
WINNERS="$(for _ in 1 2 3 4 5 6 7 8 9 10; do location "$BASE/$OT" & done | grep -c '^https://example.com/once')"
wait
expect "one-time winners" 1 "$WINNERS"

echo "- password-protected link"
PW="$(mk '{"originalUrl":"https://example.com/secret","password":"open-sesame-1"}')"
expect "redirects to unlock page" "/unlock/$PW" "$(location "$BASE/$PW" | sed 's#^https\?://[^/]*##')"
expect "wrong password" 403 "$(code -X POST "$BASE/$PW/unlock" -H "$JSON" -d '{"password":"nope"}')"
UNLOCK="$(curl -s -m 20 -X POST "$BASE/$PW/unlock" -H "$JSON" -d '{"password":"open-sesame-1"}' | json_get originalUrl)"
expect "correct password reveals target" "https://example.com/secret" "$UNLOCK"

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

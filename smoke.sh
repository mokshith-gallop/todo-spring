#!/usr/bin/env bash
# smoke.sh — exercises POST /api/v1/tasks against a live backend.
# Usage: BASE_URL=http://127.0.0.1:8080 ./smoke.sh
# Expects DATABASE_URL (or SPRING_DATASOURCE_* / APP_DB_*) in the environment.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PASS=0
FAIL=0

# ── Resolve DATABASE_URL for psql seeding ────────────────
if [ -z "${DATABASE_URL:-}" ]; then
  if [ -f /workspace/.gallop/preview-env.json ]; then
    eval "$(python3 -c "
import json, sys
d = json.load(open('/workspace/.gallop/preview-env.json'))['backend']
for k, v in d.items():
    print('export %s=%s' % (k, json.dumps(str(v))))
")"
  fi
fi

if [ -z "${DATABASE_URL:-}" ]; then
  echo "FATAL: DATABASE_URL is not set and cannot be resolved."
  exit 1
fi

# ── Helpers ──────────────────────────────────────────────
sql() { psql "$DATABASE_URL" -t -A -c "$1"; }

check() {
  local label="$1" expected_status="$2" actual_status="$3"
  if [ "$actual_status" = "$expected_status" ]; then
    echo "  ✓ $label (HTTP $actual_status)"
    PASS=$((PASS + 1))
  else
    echo "  ✗ $label — expected $expected_status, got $actual_status"
    FAIL=$((FAIL + 1))
  fi
}

check_body() {
  local label="$1" pattern="$2" body="$3"
  if echo "$body" | grep -qE "$pattern"; then
    echo "  ✓ $label"
    PASS=$((PASS + 1))
  else
    echo "  ✗ $label — pattern '$pattern' not found in: $body"
    FAIL=$((FAIL + 1))
  fi
}

post() {
  # $1 = JSON body, remaining args = extra curl flags
  local body="$1"; shift
  curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/tasks" \
    -H "Content-Type: application/json" \
    "$@" \
    -d "$body"
}

# ── Seed data ────────────────────────────────────────────
echo "=== Seeding test data ==="
USER1_ID="11111111-1111-1111-1111-111111111111"
USER2_ID="22222222-2222-2222-2222-222222222222"
LIST1_ID="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
LIST2_ID="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

sql "DELETE FROM task; DELETE FROM task_list; DELETE FROM user_account;"
sql "INSERT INTO user_account (id, email, password_hash) VALUES
       ('$USER1_ID', 'user1@smoke.com', 'h1'),
       ('$USER2_ID', 'user2@smoke.com', 'h2');"
sql "INSERT INTO task_list (id, user_id, name, is_inbox, position) VALUES
       ('$LIST1_ID', '$USER1_ID', 'Smoke List', false, 0),
       ('$LIST2_ID', '$USER2_ID', 'User2 List', false, 0);"
echo "  Seeded 2 users, 2 lists"

# ── AC-1: Happy path — full task creation ────────────────
echo ""
echo "=== AC-1: Create task — happy path ==="

RESP=$(post "{\"title\":\"Buy groceries\",\"listId\":\"$LIST1_ID\",\"notes\":\"Milk, eggs\",\"dueAt\":\"2026-08-15T10:00:00Z\",\"priority\":\"med\"}" \
  -H "X-User-Id: $USER1_ID" -H "X-Request-Id: smoke-req-1")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')

check "Full task creation returns 201" "201" "$HTTP"
check_body "Response contains id" '"id"' "$BODY"
check_body "Position is 0" '"position":0.0' "$BODY"
check_body "completedAt is null" '"completedAt":null' "$BODY"
check_body "version is 0" '"version":0' "$BODY"
check_body "Priority is med" '"priority":"med"' "$BODY"
check_body "Notes are present" '"notes":"Milk, eggs"' "$BODY"
check_body "dueAt is present" '"dueAt":"2026-08-15T10:00:00' "$BODY"
check_body "createdAt is present" '"createdAt"' "$BODY"
check_body "updatedAt is present" '"updatedAt"' "$BODY"

# Second task — position should be 1
echo ""
echo "=== AC-1: Second task — position increments ==="
RESP=$(post "{\"title\":\"Second task\",\"listId\":\"$LIST1_ID\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')

check "Second task returns 201" "201" "$HTTP"
check_body "Position is 1" '"position":1.0' "$BODY"

# Minimal request — only title and listId
echo ""
echo "=== AC-1: Minimal request (omit optional fields) ==="
RESP=$(post "{\"title\":\"Minimal task\",\"listId\":\"$LIST1_ID\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')

check "Minimal request returns 201" "201" "$HTTP"
check_body "notes is null" '"notes":null' "$BODY"
check_body "dueAt is null" '"dueAt":null' "$BODY"
check_body "priority defaults to none" '"priority":"none"' "$BODY"

# ── AC-2: Priority ──────────────────────────────────────
echo ""
echo "=== AC-2: Priority defaults and validation ==="

# Omit priority → defaults to 'none'
RESP=$(post "{\"title\":\"No prio\",\"listId\":\"$LIST1_ID\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "Omitted priority returns 201" "201" "$HTTP"
check_body "Defaults to none" '"priority":"none"' "$BODY"

# Explicit priority=high
RESP=$(post "{\"title\":\"High prio\",\"listId\":\"$LIST1_ID\",\"priority\":\"high\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "priority=high returns 201" "201" "$HTTP"
check_body "Priority is high" '"priority":"high"' "$BODY"

# Invalid priority → 422
RESP=$(post "{\"title\":\"Bad prio\",\"listId\":\"$LIST1_ID\",\"priority\":\"urgent\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "Invalid priority returns 422" "422" "$HTTP"
check_body "Error code is VALIDATION_ERROR" '"code":"VALIDATION_ERROR"' "$BODY"

# ── AC-3: Notes length ──────────────────────────────────
echo ""
echo "=== AC-3: Notes length validation ==="

NOTES_10000=$(python3 -c "print('a' * 10000)")
RESP=$(post "{\"title\":\"Long notes ok\",\"listId\":\"$LIST1_ID\",\"notes\":\"$NOTES_10000\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
check "Notes at 10000 chars returns 201" "201" "$HTTP"

NOTES_10001=$(python3 -c "print('a' * 10001)")
RESP=$(post "{\"title\":\"Long notes fail\",\"listId\":\"$LIST1_ID\",\"notes\":\"$NOTES_10001\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "Notes at 10001 chars returns 422" "422" "$HTTP"
check_body "Error code is VALIDATION_ERROR" '"code":"VALIDATION_ERROR"' "$BODY"

# ── AC-4: Title validation ──────────────────────────────
echo ""
echo "=== AC-4: Title validation ==="

# Empty title
RESP=$(post "{\"title\":\"\",\"listId\":\"$LIST1_ID\"}" -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
check "Empty title returns 422" "422" "$HTTP"

# Whitespace-only title
RESP=$(post "{\"title\":\"   \",\"listId\":\"$LIST1_ID\"}" -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
check "Whitespace title returns 422" "422" "$HTTP"

# Title too long (501 chars)
TITLE_501=$(python3 -c "print('a' * 501)")
RESP=$(post "{\"title\":\"$TITLE_501\",\"listId\":\"$LIST1_ID\"}" -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
check "Title 501 chars returns 422" "422" "$HTTP"

# Title at exactly 500 chars
TITLE_500=$(python3 -c "print('a' * 500)")
RESP=$(post "{\"title\":\"$TITLE_500\",\"listId\":\"$LIST1_ID\"}" -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
check "Title 500 chars returns 201" "201" "$HTTP"

# ── AC-5: dueAt as UTC ISO-8601 ─────────────────────────
echo ""
echo "=== AC-5: dueAt stored as UTC ==="

RESP=$(post "{\"title\":\"Due date test\",\"listId\":\"$LIST1_ID\",\"dueAt\":\"2026-08-15T10:00:00Z\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "dueAt accepted returns 201" "201" "$HTTP"
check_body "dueAt is ISO-8601" '"dueAt":"2026-08-15T10:00:00' "$BODY"

# ── AC-6: List not found / not owned → 404 ──────────────
echo ""
echo "=== AC-6: List ownership — 404 not 403 ==="

# Non-existent list
RESP=$(post "{\"title\":\"Ghost list\",\"listId\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "Non-existent list returns 404" "404" "$HTTP"
check_body "Error code is NOT_FOUND" '"code":"NOT_FOUND"' "$BODY"

# Other user's list
RESP=$(post "{\"title\":\"Stolen list\",\"listId\":\"$LIST2_ID\"}" \
  -H "X-User-Id: $USER1_ID")
HTTP=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "Other user's list returns 404" "404" "$HTTP"
check_body "Error code is NOT_FOUND" '"code":"NOT_FOUND"' "$BODY"

# ── AC-7: X-Request-Id propagation ──────────────────────
echo ""
echo "=== AC-7: X-Request-Id header propagation ==="

# Provided request ID echoed back
RESP_HEADERS=$(curl -s -D - -o /dev/null -X POST "${BASE_URL}/api/v1/tasks" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER1_ID" \
  -H "X-Request-Id: my-custom-request-id-42" \
  -d "{\"title\":\"ReqId test\",\"listId\":\"$LIST1_ID\"}")
if echo "$RESP_HEADERS" | grep -q "X-Request-Id: my-custom-request-id-42"; then
  echo "  ✓ Provided X-Request-Id echoed in response"
  PASS=$((PASS + 1))
else
  echo "  ✗ Provided X-Request-Id NOT echoed in response"
  echo "    Headers: $RESP_HEADERS"
  FAIL=$((FAIL + 1))
fi

# No request ID → auto-generated
RESP_HEADERS=$(curl -s -D - -o /dev/null -X POST "${BASE_URL}/api/v1/tasks" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER1_ID" \
  -d "{\"title\":\"Auto ReqId\",\"listId\":\"$LIST1_ID\"}")
if echo "$RESP_HEADERS" | grep -q "X-Request-Id:"; then
  echo "  ✓ Auto-generated X-Request-Id present in response"
  PASS=$((PASS + 1))
else
  echo "  ✗ X-Request-Id missing from response"
  FAIL=$((FAIL + 1))
fi

# ── Summary ──────────────────────────────────────────────
echo ""
echo "=== RESULTS: $PASS passed, $FAIL failed ==="
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
echo "All smoke checks passed."

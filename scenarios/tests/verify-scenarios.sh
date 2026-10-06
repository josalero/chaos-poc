#!/usr/bin/env bash
# Verifies all assault scenarios against a running chaos-poc stack.
# Each case runs in isolation (setup → execute → teardown), like a transaction.
# Suite ends with rollback to default configuration regardless of pass/fail.
set -euo pipefail

RELAY="${RELAY_URL:-http://localhost:18090}"
if [[ -z "${DEMO_URL:-}" ]]; then
  published=$(docker port chaos-poc-chaos-poc-demo-1 8080 2>/dev/null | head -1 | sed 's/.*://')
  DEMO="${published:+http://localhost:${published}}"
  DEMO="${DEMO:-http://localhost:18080}"
else
  DEMO="${DEMO_URL}"
fi
SCENARIOS_DIR="$(cd "$(dirname "$0")/.." && pwd)"

SUITE_FAILED=0

submit() {
  local file="$1"
  local correlation="$2"
  local payload
  local expires_at
  expires_at=$(python3 -c 'from datetime import datetime, timedelta, timezone; print((datetime.now(timezone.utc) + timedelta(minutes=10)).isoformat().replace("+00:00", "Z"))')
  payload=$(jq -c --arg c "$correlation" --arg e "$expires_at" '
    . + {correlationId: $c, issuedBy: "verify-scenarios"}
    | if (.action == "ENABLE" or .action == "CONFIGURE_AND_ENABLE") then .expiresAt = $e else . end
  ' "$file")
  local response
  response=$(curl -sf -X POST "$RELAY/internal/v1/chaos/commands" -H 'Content-Type: application/json' -d "$payload")
  local command_id
  command_id=$(echo "$response" | jq -r .commandId)
  for _ in $(seq 1 20); do
    local cmd_status
    cmd_status=$(curl -sf "$RELAY/internal/v1/chaos/commands/$command_id" | jq -r .status)
    if [[ "$cmd_status" == "APPLIED" || "$cmd_status" == "FAILED" ]]; then
      echo "$cmd_status"
      return 0
    fi
    sleep 1
  done
  echo "TIMEOUT"
  return 1
}

chaos_monkey_enabled() {
  curl -sf "$DEMO/actuator/chaosmonkey" | jq -r '.chaosMonkeyProperties.enabled // false'
}

assert_default_assaults() {
  local assaults
  assaults=$(curl -sf "$DEMO/actuator/chaosmonkey/assaults")
  if ! jq -e '
    .level == 1 and
    .deterministic == true and
    .latencyActive == false and
    .latencyRangeStart == 1000 and
    .latencyRangeEnd == 3000 and
    .exceptionsActive == false and
    .exception.type == "java.lang.RuntimeException" and
    (.watchedCustomServices | length) == 0
  ' <<<"$assaults" >/dev/null; then
    echo "FAIL default assaults do not match canonical state: $assaults" >&2
    return 1
  fi
}

unique_sku() {
  echo "TC-$(date +%s)-$RANDOM"
}

# Roll back Chaos Monkey + demo data to the default (disabled, empty orders) state.
rollback_to_default() {
  local label="${1:-rollback}"
  local apply_status
  apply_status=$(submit "$SCENARIOS_DIR/disable.json" "verify-$label-$(date +%s)")
  if [[ "$apply_status" != "APPLIED" ]]; then
    echo "FAIL rollback ($label): disable command status=$apply_status" >&2
    return 1
  fi
  curl -sf -X POST "$DEMO/api/v1/admin/reset" >/dev/null
  sleep 1
  local enabled
  enabled=$(chaos_monkey_enabled)
  if [[ "$enabled" != "false" ]]; then
    echo "FAIL rollback ($label): Chaos Monkey still enabled (enabled=$enabled)" >&2
    return 1
  fi
  if ! assert_default_assaults; then
    echo "FAIL rollback ($label): stale assault settings remain" >&2
    return 1
  fi
  echo "OK rollback ($label)"
}

assert_http() {
  local expected="$1"
  local actual
  actual=$(curl -s -o /tmp/chaos-verify-body.json -w '%{http_code}' "${@:2}")
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL expected HTTP $expected got $actual body=$(cat /tmp/chaos-verify-body.json)"
    return 1
  fi
  echo "OK HTTP $expected"
}

assert_normal_create() {
  local sku
  sku=$(unique_sku)
  assert_http 201 -X POST "$DEMO/api/v1/orders" -H 'Content-Type: application/json' \
    -d "{\"sku\":\"$sku\",\"quantity\":1}"
}

# Emergency rollback if the script is interrupted mid-suite.
on_exit() {
  local rc=$?
  if [[ "$rc" -ne 0 ]]; then
    rollback_to_default "exit-trap" >/dev/null 2>&1 || true
  fi
}
trap on_exit EXIT

run_case() {
  local name="$1"
  local file="$2"
  shift 2
  echo "== $name =="
  if ! rollback_to_default "setup-$name"; then
    SUITE_FAILED=1
    return 1
  fi
  local apply_status
  apply_status=$(submit "$file" "verify-$name-$(date +%s)")
  if [[ "$apply_status" != "APPLIED" ]]; then
    echo "FAIL apply status=$apply_status for $file"
    rollback_to_default "teardown-$name-after-fail" >/dev/null 2>&1 || true
    SUITE_FAILED=1
    return 1
  fi
  local test_rc=0
  "$@" || test_rc=$?
  if ! rollback_to_default "teardown-$name"; then
    SUITE_FAILED=1
    return 1
  fi
  if [[ "$test_rc" -ne 0 ]]; then
    SUITE_FAILED=1
    return 1
  fi
}

# Suite setup: ensure default state before any case.
rollback_to_default "suite-setup"

run_scenario_case() {
  local name="$1"
  case "$name" in
    bean-interceptor)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        assert_http 500 -X POST "$DEMO/api/v1/orders" -H 'Content-Type: application/json' \
        -d "{\"sku\":\"$(unique_sku)\",\"quantity\":1}"
      ;;
    service-to-service-latency)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        bash -c 'sku="'"$(unique_sku)"'"; start_ms=$(python3 -c "import time; print(int(time.time()*1000))"); code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "'"$DEMO"'/api/v1/orders" -H "Content-Type: application/json" -d "{\"sku\":\"$sku\",\"quantity\":1}"); end_ms=$(python3 -c "import time; print(int(time.time()*1000))"); ms=$((end_ms-start_ms)); echo "HTTP $code in ${ms}ms"; test "$code" = "201" && test "$ms" -ge 1500'
      ;;
    exception-http-404)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        assert_http 404 -X POST "$DEMO/api/v1/orders" -H 'Content-Type: application/json' \
        -d "{\"sku\":\"$(unique_sku)\",\"quantity\":1}"
      ;;
    exception-http-409)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        assert_http 409 -X POST "$DEMO/api/v1/orders" -H 'Content-Type: application/json' \
        -d "{\"sku\":\"$(unique_sku)\",\"quantity\":1}"
      ;;
    exception-http-500-create)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        assert_http 500 -X POST "$DEMO/api/v1/orders" -H 'Content-Type: application/json' \
        -d "{\"sku\":\"$(unique_sku)\",\"quantity\":1}"
      ;;
    exception-http-500-submit)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        bash -c 'sku="'"$(unique_sku)"'"; create=$(curl -sf -X POST "'"$DEMO"'/api/v1/orders" -H "Content-Type: application/json" -d "{\"sku\":\"$sku\",\"quantity\":1}"); order_id=$(echo "$create" | jq -r .id); code=$(curl -s -o /tmp/chaos-verify-body.json -w "%{http_code}" -X POST "'"$DEMO"'/api/v1/orders/$order_id/submit"); echo "submit HTTP $code"; test "$code" = "500"'
      ;;
    latency-success-path)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        assert_http 201 -X POST "$DEMO/api/v1/orders" -H 'Content-Type: application/json' \
        -d "{\"sku\":\"$(unique_sku)\",\"quantity\":1}"
      ;;
    exception-http-403)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        bash -c 'sku="'"$(unique_sku)"'"; create=$(curl -sf -X POST "'"$DEMO"'/api/v1/orders" -H "Content-Type: application/json" -d "{\"sku\":\"$sku\",\"quantity\":1}"); order_id=$(echo "$create" | jq -r .id); code=$(curl -s -o /tmp/chaos-verify-body.json -w "%{http_code}" -X POST "'"$DEMO"'/api/v1/orders/$order_id/submit"); echo "submit HTTP $code"; test "$code" = "403"'
      ;;
    high-pressure-latency)
      run_case "$name" "$SCENARIOS_DIR/$name.json" \
        bash -c 'sku="'"$(unique_sku)"'"; create=$(curl -sf -X POST "'"$DEMO"'/api/v1/orders" -H "Content-Type: application/json" -d "{\"sku\":\"$sku\",\"quantity\":1}"); order_id=$(echo "$create" | jq -r .id); start_ms=$(python3 -c "import time; print(int(time.time()*1000))"); code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "'"$DEMO"'/api/v1/orders/$order_id/submit"); end_ms=$(python3 -c "import time; print(int(time.time()*1000))"); ms=$((end_ms-start_ms)); echo "submit HTTP $code in ${ms}ms"; test "$code" = "200" && test "$ms" -ge 500'
      ;;
    *)
      echo "Unknown scenario in SCENARIO_ORDER: $name" >&2
      return 2
      ;;
  esac
}

DEFAULT_SCENARIO_ORDER=(
  bean-interceptor
  service-to-service-latency
  exception-http-404
  exception-http-409
  exception-http-500-create
  exception-http-500-submit
  latency-success-path
  exception-http-403
  high-pressure-latency
)

if [[ -n "${SCENARIO_ORDER:-}" ]]; then
  read -r -a scenario_order <<<"${SCENARIO_ORDER//,/ }"
else
  scenario_order=("${DEFAULT_SCENARIO_ORDER[@]}")
fi

echo "Scenario order: ${scenario_order[*]}"
for scenario_name in "${scenario_order[@]}"; do
  run_scenario_case "$scenario_name" || SUITE_FAILED=1
done

# Suite teardown: rollback + prove normal path works.
echo "== suite-teardown =="
rollback_to_default "suite-final"
assert_normal_create

if [[ "$SUITE_FAILED" -ne 0 ]]; then
  echo "One or more scenario checks failed."
  exit 1
fi

echo "All scenario checks passed (isolated, default configuration restored)."

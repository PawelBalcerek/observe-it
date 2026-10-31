#!/usr/bin/env bash
set -euo pipefail

# End-to-end Milestone 1 verification suite
# Enforces five deterministic verification gates against the live Docker Compose environment.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

echo "========================================================================"
echo " Starting Milestone 1 End-to-End Verification Suite (make verify)"
echo "========================================================================"

# ----------------------------------------------------------------------
# Gate 1: Build & Zero-SDK Gate
# ----------------------------------------------------------------------
echo ""
echo "------------------------------------------------------------------------"
echo " Gate 1: Build & Zero-SDK Gate"
echo "------------------------------------------------------------------------"

echo "==> Verifying ./gradlew check passes..."
./gradlew check

echo "==> Verifying neither :observed-app nor :traffic-generator runtime classpath contains io.opentelemetry..."
OBSERVED_APP_OTEL="$(./gradlew :observed-app:dependencies --configuration runtimeClasspath | grep -i "io.opentelemetry" || true)"
if [ -n "${OBSERVED_APP_OTEL}" ]; then
  echo "FAIL: :observed-app runtime classpath contains io.opentelemetry:"
  echo "${OBSERVED_APP_OTEL}"
  exit 1
fi

TRAFFIC_GEN_OTEL="$(./gradlew :traffic-generator:dependencies --configuration runtimeClasspath | grep -i "io.opentelemetry" || true)"
if [ -n "${TRAFFIC_GEN_OTEL}" ]; then
  echo "FAIL: :traffic-generator runtime classpath contains io.opentelemetry:"
  echo "${TRAFFIC_GEN_OTEL}"
  exit 1
fi
echo "Zero-SDK runtime classpath verified (0 io.opentelemetry dependencies)."

echo "==> Verifying observe-it/observed-app:0.1.0 image exists..."
docker image inspect observe-it/observed-app:0.1.0 >/dev/null 2>&1 || {
  echo "FAIL: Image observe-it/observed-app:0.1.0 not found"
  exit 1
}

echo "==> Verifying observe-it/traffic-generator:0.1.0 image exists..."
docker image inspect observe-it/traffic-generator:0.1.0 >/dev/null 2>&1 || {
  echo "FAIL: Image observe-it/traffic-generator:0.1.0 not found"
  exit 1
}
echo "Gate 1 PASSED: Build & Zero-SDK Gate"

# ----------------------------------------------------------------------
# Gate 2: Container & Bootstrap Health Gate
# ----------------------------------------------------------------------
echo ""
echo "------------------------------------------------------------------------"
echo " Gate 2: Container & Bootstrap Health Gate"
echo "------------------------------------------------------------------------"

wait_for_condition() {
  local description="$1"
  local timeout_seconds="$2"
  local check_command="$3"

  echo "==> Waiting for ${description} (up to ${timeout_seconds}s)..."
  local elapsed=0
  until eval "${check_command}" >/dev/null 2>&1; do
    if [ "${elapsed}" -ge "${timeout_seconds}" ]; then
      echo "FAIL: Timed out waiting for ${description} after ${timeout_seconds}s"
      exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "✔ ${description} is ready (${elapsed}s)."
}

wait_for_condition "opensearch container healthy" 60 \
  "[ \"\$(docker inspect opensearch --format '{{.State.Health.Status}}' 2>/dev/null || true)\" = 'healthy' ]"

wait_for_condition "opensearch-dashboards ready" 60 \
  "[ \"\$(curl -fsS http://localhost:5601/api/status 2>/dev/null | jq -r .status.overall.state || true)\" = 'green' ]"

wait_for_condition "otel-collector container running" 30 \
  "[ \"\$(docker inspect otel-collector --format '{{.State.Status}}' 2>/dev/null || true)\" = 'running' ]"

wait_for_condition "observed-app actuator health UP" 60 \
  "[ \"\$(curl -fsS http://localhost:8080/actuator/health 2>/dev/null | jq -r .status || true)\" = 'UP' ]"

wait_for_condition "traffic-generator actuator health UP" 60 \
  "[ \"\$(curl -fsS http://localhost:8081/actuator/health 2>/dev/null | jq -r .status || true)\" = 'UP' ]"

wait_for_condition "opensearch-bootstrap completed with exit code 0" 60 \
  "[ \"\$(docker inspect opensearch-bootstrap --format '{{.State.Status}}' 2>/dev/null || true)\" = 'exited' ] && [ \"\$(docker inspect opensearch-bootstrap --format '{{.State.ExitCode}}' 2>/dev/null || true)\" = '0' ]"
echo "opensearch-bootstrap exited with code 0."
echo "Gate 2 PASSED: Container & Bootstrap Health Gate"

# ----------------------------------------------------------------------
# Gate 3: Deterministic 7-Scenario Trigger Gate
# ----------------------------------------------------------------------
echo ""
echo "------------------------------------------------------------------------"
echo " Gate 3: Deterministic 7-Scenario Trigger Gate"
echo "------------------------------------------------------------------------"

SCENARIOS=(
  "HAPPY_PATH"
  "LOW_STOCK_WARNING"
  "OUT_OF_STOCK"
  "PAYMENT_DECLINED"
  "PAYMENT_GATEWAY_TIMEOUT"
  "BUYER_CANCELLATION"
  "INVALID_STATE_TRANSITION"
)

for scenario in "${SCENARIOS[@]}"; do
  echo "==> Triggering scenario: ${scenario}..."
  RESPONSE="$(curl -fsS -X POST "http://localhost:8081/api/traffic/trigger/${scenario}")"
  echo "    Response: ${RESPONSE}"
  SUCCESS="$(echo "${RESPONSE}" | jq -r .success)"
  if [ "${SUCCESS}" != "true" ]; then
    echo "FAIL: Scenario ${scenario} did not report success: true"
    exit 1
  fi
done

echo "==> Waiting 3s for Collector batch flush..."
sleep 3

echo "==> Refreshing OpenSearch index ss4o_logs-observe-it-local..."
curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_refresh" >/dev/null
echo "Gate 3 PASSED: Deterministic 7-Scenario Trigger Gate"

# ----------------------------------------------------------------------
# Gate 4: OpenSearch ss4o_logs-observe-it-local Schema & Telemetry Gate
# ----------------------------------------------------------------------
echo ""
echo "------------------------------------------------------------------------"
echo " Gate 4: OpenSearch ss4o_logs-observe-it-local Schema & Telemetry Gate"
echo "------------------------------------------------------------------------"

echo "==> Verifying _index_template/observe-it-ss4o-logs exists..."
TEMPLATE_COUNT="$(curl -fsS "http://localhost:9200/_index_template/observe-it-ss4o-logs" | jq '.index_templates | length')"
if [ "${TEMPLATE_COUNT}" -lt 1 ]; then
  echo "FAIL: _index_template/observe-it-ss4o-logs does not exist"
  exit 1
fi
echo "Index template observe-it-ss4o-logs exists."

echo "==> Verifying ss4o_logs-observe-it-local index health is green (1 shard, 0 replicas)..."
INDEX_HEALTH="$(curl -fsS "http://localhost:9200/_cluster/health/ss4o_logs-observe-it-local" | jq -r .status)"
if [ "${INDEX_HEALTH}" != "green" ]; then
  echo "FAIL: ss4o_logs-observe-it-local index health is '${INDEX_HEALTH}', expected 'green'"
  exit 1
fi

INDEX_SETTINGS="$(curl -fsS "http://localhost:9200/ss4o_logs-observe-it-local/_settings")"
SHARDS="$(echo "${INDEX_SETTINGS}" | jq -r '.[].settings.index.number_of_shards')"
REPLICAS="$(echo "${INDEX_SETTINGS}" | jq -r '.[].settings.index.number_of_replicas')"
if [ "${SHARDS}" != "1" ] || [ "${REPLICAS}" != "0" ]; then
  echo "FAIL: Expected 1 shard and 0 replicas, got shards=${SHARDS}, replicas=${REPLICAS}"
  exit 1
fi
echo "Index health is green (1 shard, 0 replicas)."

echo "==> Verifying documents exist for both observed-app and traffic-generator..."
OBSERVED_APP_DOCS="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_count" \
  -H "Content-Type: application/json" \
  -d '{"query":{"term":{"service":"observed-app"}}}' | jq -r .count)"
TRAFFIC_GEN_DOCS="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_count" \
  -H "Content-Type: application/json" \
  -d '{"query":{"term":{"service":"traffic-generator"}}}' | jq -r .count)"

if [ "${OBSERVED_APP_DOCS}" -le 0 ]; then
  echo "FAIL: No documents found for service: observed-app"
  exit 1
fi
if [ "${TRAFFIC_GEN_DOCS}" -le 0 ]; then
  echo "FAIL: No documents found for service: traffic-generator"
  exit 1
fi
echo "Found ${OBSERVED_APP_DOCS} observed-app documents and ${TRAFFIC_GEN_DOCS} traffic-generator documents."

echo "==> Verifying terms aggregation on attributes.checkout.scenario returns all 7 scenario keys..."
SCENARIO_KEYS="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_search" \
  -H "Content-Type: application/json" \
  -d '{"size":0,"aggs":{"scenarios":{"terms":{"field":"attributes.checkout.scenario","size":20}}}}' | \
  jq -r '.aggregations.scenarios.buckets[].key' | sort)"

for expected_scenario in "${SCENARIOS[@]}"; do
  if ! echo "${SCENARIO_KEYS}" | grep -qx "${expected_scenario}"; then
    echo "FAIL: Scenario key '${expected_scenario}' missing from aggregation buckets:"
    echo "${SCENARIO_KEYS}"
    exit 1
  fi
done
echo "All 7 scenario keys present in attributes.checkout.scenario aggregation."

echo "==> Verifying documents contain correlated order.id, customer.id, W3C traceId/spanId, thread.name, and code.namespace..."
CORRELATED_RESPONSE="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_search?size=10" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          {"exists": {"field": "attributes.order.id"}},
          {"exists": {"field": "attributes.customer.id"}},
          {"exists": {"field": "traceId"}},
          {"exists": {"field": "spanId"}},
          {"exists": {"field": "attributes.thread.name"}},
          {"exists": {"field": "attributes.code.namespace"}}
        ]
      }
    }
  }')"
CORRELATED_COUNT="$(echo "${CORRELATED_RESPONSE}" | jq -r '.hits.total.value')"

if [ "${CORRELATED_COUNT}" -le 0 ]; then
  echo "FAIL: No documents found with complete correlation attributes"
  exit 1
fi

echo "${CORRELATED_RESPONSE}" | jq -e '
  .hits.hits[] |
  select((._source.traceId | test("^[0-9a-f]{32}$")) and (._source.spanId | test("^[0-9a-f]{16}$")))
' >/dev/null || {
  echo "FAIL: Correlated documents do not conform to W3C traceId (32 hex) and spanId (16 hex) format"
  exit 1
}
echo "Found ${CORRELATED_COUNT} fully correlated documents with valid W3C traceId (32 hex) and spanId (16 hex)."

echo "==> Verifying WARN documents exist for LOW_STOCK_WARNING, OUT_OF_STOCK, PAYMENT_DECLINED, and INVALID_STATE_TRANSITION without stacktrace..."
WARN_SCENARIOS=("LOW_STOCK_WARNING" "OUT_OF_STOCK" "PAYMENT_DECLINED" "INVALID_STATE_TRANSITION")
for warn_scenario in "${WARN_SCENARIOS[@]}"; do
  WARN_COUNT="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_count" \
    -H "Content-Type: application/json" \
    -d "{
      \"query\": {
        \"bool\": {
          \"must\": [
            {\"term\": {\"level\": \"WARN\"}},
            {\"term\": {\"attributes.checkout.scenario\": \"${warn_scenario}\"}}
          ],
          \"must_not\": [
            {\"exists\": {\"field\": \"exception\"}}
          ]
        }
      }
    }" | jq -r .count)"

  if [ "${WARN_COUNT}" -le 0 ]; then
    echo "FAIL: No WARN documents without stacktrace for scenario ${warn_scenario}"
    exit 1
  fi

  WARN_WITH_STACKTRACE_COUNT="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_count" \
    -H "Content-Type: application/json" \
    -d "{
      \"query\": {
        \"bool\": {
          \"must\": [
            {\"term\": {\"level\": \"WARN\"}},
            {\"term\": {\"attributes.checkout.scenario\": \"${warn_scenario}\"}},
            {\"exists\": {\"field\": \"exception\"}}
          ]
        }
      }
    }" | jq -r .count)"

  if [ "${WARN_WITH_STACKTRACE_COUNT}" -ne 0 ]; then
    echo "FAIL: Found ${WARN_WITH_STACKTRACE_COUNT} WARN documents with unexpected stacktrace for scenario ${warn_scenario}"
    exit 1
  fi
  echo "✔ ${warn_scenario}: ${WARN_COUNT} WARN documents (0 with stacktrace)."
done

echo "==> Verifying ERROR documents exist for PAYMENT_GATEWAY_TIMEOUT with error.type, latency_ms >= 1000, and non-empty stacktrace..."
ERROR_RESPONSE="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_search?size=10" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          {"term": {"level": "ERROR"}},
          {"term": {"attributes.checkout.scenario": "PAYMENT_GATEWAY_TIMEOUT"}},
          {"term": {"attributes.error.type": "GATEWAY_TIMEOUT"}},
          {"range": {"attributes.payment.latency_ms": {"gte": 1000}}},
          {"exists": {"field": "exception"}}
        ]
      }
    }
  }')"
ERROR_COUNT="$(echo "${ERROR_RESPONSE}" | jq -r '.hits.total.value')"

if [ "${ERROR_COUNT}" -le 0 ]; then
  echo "FAIL: No ERROR documents for PAYMENT_GATEWAY_TIMEOUT matching criteria"
  exit 1
fi

echo "${ERROR_RESPONSE}" | jq -e '
  .hits.hits[] |
  select(._source.exception != null and (._source.exception | length > 0))
' >/dev/null || {
  echo "FAIL: PAYMENT_GATEWAY_TIMEOUT documents contain empty exception stacktraces"
  exit 1
}
echo "Found ${ERROR_COUNT} PAYMENT_GATEWAY_TIMEOUT ERROR documents with non-empty stacktraces."

echo "==> Verifying NO unexpected WARNs or ERRORs exist..."

assert_no_unexpected_logs() {
  local severity="$1"
  local allowed_scenarios_json="$2"
  local description="$3"

  local unexpected_count
  unexpected_count="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_search" \
    -H "Content-Type: application/json" \
    -d "{
      \"query\": {
        \"bool\": {
          \"must\": [
            {\"term\": {\"level\": \"${severity}\"}}
          ],
          \"must_not\": [
            {\"terms\": {\"attributes.checkout.scenario\": ${allowed_scenarios_json}}}
          ]
        }
      }
    }" | jq -r '.hits.total.value')"

  if [ "${unexpected_count}" -ne 0 ]; then
    echo "FAIL: Found ${unexpected_count} unexpected ${severity} log documents in OpenSearch!"
    curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_search?size=5" \
      -H "Content-Type: application/json" \
      -d "{
        \"query\": {
          \"bool\": {
            \"must\": [{\"term\": {\"level\": \"${severity}\"}}],
            \"must_not\": [{\"terms\": {\"attributes.checkout.scenario\": ${allowed_scenarios_json}}}]
          }
        }
      }" | jq '.hits.hits[] | {service: ._source.service, scenario: ._source.attributes["checkout.scenario"], message: ._source.message}'
    exit 1
  fi
  echo "✔ 0 unexpected ${severity} documents found (${description})."
}

assert_no_unexpected_logs "WARN" \
  '["LOW_STOCK_WARNING", "OUT_OF_STOCK", "PAYMENT_DECLINED", "INVALID_STATE_TRANSITION"]' \
  "allowed scenarios: LOW_STOCK_WARNING, OUT_OF_STOCK, PAYMENT_DECLINED, INVALID_STATE_TRANSITION"

assert_no_unexpected_logs "ERROR" \
  '["PAYMENT_GATEWAY_TIMEOUT"]' \
  "allowed scenario: PAYMENT_GATEWAY_TIMEOUT"

FATAL_COUNT="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_count" \
  -H "Content-Type: application/json" \
  -d '{"query":{"term":{"level":"FATAL"}}}' | jq -r .count)"

if [ "${FATAL_COUNT}" -ne 0 ]; then
  echo "FAIL: Found ${FATAL_COUNT} FATAL log documents in OpenSearch!"
  exit 1
fi
echo "✔ 0 FATAL documents found."

# Verify clean normalized fields (message, level, logger, env, service, version, exception)
NORMALIZED_MATCH_COUNT="$(curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_count" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          { "term": { "level": "WARN" } },
          { "term": { "env": "local" } },
          { "term": { "service": "observed-app" } },
          { "term": { "version": "0.1.0" } },
          { "wildcard": { "logger": "com.observeit.*" } },
          { "match_phrase": { "message": "Payment declined" } }
        ]
      }
    }
  }' | jq -r .count)"

if [ "${NORMALIZED_MATCH_COUNT}" -eq 0 ]; then
  echo "FAIL: Clean normalized fields (message, level, logger, env, service, version) did not return matching documents."
  exit 1
fi
echo "✔ Clean normalized fields (message, level, logger, env, service, version) verified (${NORMALIZED_MATCH_COUNT} matches)."
echo "Gate 4 PASSED: OpenSearch ss4o_logs-observe-it-local Schema & Telemetry Gate"

# ----------------------------------------------------------------------
# Gate 5: OpenSearch Dashboards Saved Objects Gate
# ----------------------------------------------------------------------
echo ""
echo "------------------------------------------------------------------------"
echo " Gate 5: OpenSearch Dashboards Saved Objects Gate"
echo "------------------------------------------------------------------------"

SAVED_OBJECTS="$(curl -fsS -H "osd-xsrf: true" \
  "http://localhost:5601/api/saved_objects/_find?type=index-pattern&type=search" | \
  jq -r '.saved_objects[] | "\(.type):\(.id)"')"

EXPECTED_SAVED_OBJECTS=(
  "index-pattern:ss4o_logs"
  "search:all-order-logs"
  "search:warnings-and-rejections"
  "search:gateway-timeouts-and-errors"
  "search:checkout-scenario-explorer"
)

for expected_obj in "${EXPECTED_SAVED_OBJECTS[@]}"; do
  if ! echo "${SAVED_OBJECTS}" | grep -qx "${expected_obj}"; then
    echo "FAIL: Expected saved object '${expected_obj}' not found in OpenSearch Dashboards."
    echo "Actual saved objects:"
    echo "${SAVED_OBJECTS}"
    exit 1
  fi
  echo "✔ Saved object present: ${expected_obj}"
done

INDEX_PATTERN_HAS_TIMESTAMP="$(curl -fsS -H "osd-xsrf: true" \
  "http://localhost:5601/api/saved_objects/index-pattern/ss4o_logs" | \
  jq -r '.attributes.fields // ""' | jq -r '[.[] | select(.name == "@timestamp")] | length')"

if [ "${INDEX_PATTERN_HAS_TIMESTAMP}" -ne 1 ]; then
  echo "FAIL: OpenSearch Dashboards index-pattern ss4o_logs does not have '@timestamp' in attributes.fields."
  exit 1
fi
echo "✔ Index pattern ss4o_logs has populated fields containing '@timestamp'."
echo "Gate 5 PASSED: OpenSearch Dashboards Saved Objects Gate"

echo ""
echo "========================================================================"
echo " ALL 5 GATES PASSED! Milestone 1 Verification Suite Succeeded."
echo "========================================================================"

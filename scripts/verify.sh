#!/usr/bin/env bash
set -euo pipefail

# End-to-end verification suite
# Enforces deterministic verification gates against the live Docker Compose environment.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

IMAGE_TAG="${OBSERVE_IT_IMAGE_TAG:-0.2.0}"
GHCR_PREFIX="${OBSERVE_IT_IMAGE_PREFIX:-ghcr.io/pawelbalcerek/observe-it}"

echo "========================================================================"
echo " Starting End-to-End Verification Suite (make verify)"
echo "========================================================================"

# ----------------------------------------------------------------------
# Gate 1: Build & API-Only OpenTelemetry Runtime Classpath Boundary Gate
# ----------------------------------------------------------------------
echo ""
echo "------------------------------------------------------------------------"
echo " Gate 1: Build & API-Only OpenTelemetry Runtime Classpath Boundary Gate"
echo "------------------------------------------------------------------------"

echo "==> Verifying ./gradlew check passes..."
./gradlew check

verify_otel_api_boundary() {
  local service="$1"
  echo "==> Verifying ${service} runtime classpath contains only OpenTelemetry API (zero SDK or instrumentation)..."
  local deps
  deps="$(./gradlew "${service}:dependencies" --configuration runtimeClasspath)"

  if ! echo "${deps}" | grep -q "io.opentelemetry:opentelemetry-api"; then
    echo "FAIL: ${service} runtime classpath does not contain io.opentelemetry:opentelemetry-api"
    exit 1
  fi

  local disallowed
  disallowed="$(echo "${deps}" | grep -E "io\.opentelemetry:opentelemetry-sdk|io\.opentelemetry\.instrumentation" || true)"
  if [ -n "${disallowed}" ]; then
    echo "FAIL: ${service} runtime classpath contains disallowed OpenTelemetry dependencies:"
    echo "${disallowed}"
    exit 1
  fi

  local unexpected
  unexpected="$(echo "${deps}" | grep -E "io\.opentelemetry(\.instrumentation)?:" | grep -vE "io\.opentelemetry:opentelemetry-(api|context|common)" || true)"
  if [ -n "${unexpected}" ]; then
    echo "FAIL: ${service} runtime classpath contains unexpected OpenTelemetry dependencies:"
    echo "${unexpected}"
    exit 1
  fi
}

verify_otel_api_boundary ":observed-app"
verify_otel_api_boundary ":traffic-generator"
echo "API-only OpenTelemetry runtime classpath boundary verified (io.opentelemetry:opentelemetry-api only, 0 SDK or instrumentation)."

echo "==> Verifying observe-it/observed-app:${IMAGE_TAG} and ${GHCR_PREFIX}/observed-app:${IMAGE_TAG} images exist..."
docker image inspect "observe-it/observed-app:${IMAGE_TAG}" >/dev/null 2>&1 || {
  echo "FAIL: Image observe-it/observed-app:${IMAGE_TAG} not found"
  exit 1
}
docker image inspect "${GHCR_PREFIX}/observed-app:${IMAGE_TAG}" >/dev/null 2>&1 || {
  echo "FAIL: Image ${GHCR_PREFIX}/observed-app:${IMAGE_TAG} not found"
  exit 1
}

echo "==> Verifying observe-it/traffic-generator:${IMAGE_TAG} and ${GHCR_PREFIX}/traffic-generator:${IMAGE_TAG} images exist..."
docker image inspect "observe-it/traffic-generator:${IMAGE_TAG}" >/dev/null 2>&1 || {
  echo "FAIL: Image observe-it/traffic-generator:${IMAGE_TAG} not found"
  exit 1
}
docker image inspect "${GHCR_PREFIX}/traffic-generator:${IMAGE_TAG}" >/dev/null 2>&1 || {
  echo "FAIL: Image ${GHCR_PREFIX}/traffic-generator:${IMAGE_TAG} not found"
  exit 1
}
echo "Gate 1 PASSED: Build & API-Only OpenTelemetry Runtime Classpath Boundary Gate"

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

wait_for_condition "opensearch container healthy" 120 \
  "[ \"\$(docker inspect opensearch --format '{{.State.Health.Status}}' 2>/dev/null || true)\" = 'healthy' ]"

wait_for_condition "opensearch-dashboards ready" 120 \
  "[ \"\$(curl -fsS http://localhost:5601/api/status 2>/dev/null | jq -r .status.overall.state || true)\" = 'green' ]"

wait_for_condition "otel-collector container running" 60 \
  "[ \"\$(docker inspect otel-collector --format '{{.State.Status}}' 2>/dev/null || true)\" = 'running' ]"

wait_for_condition "observed-app actuator health UP" 120 \
  "[ \"\$(curl -fsS http://localhost:8080/actuator/health 2>/dev/null | jq -r .status || true)\" = 'UP' ]"

wait_for_condition "traffic-generator actuator health UP" 120 \
  "[ \"\$(curl -fsS http://localhost:8081/actuator/health 2>/dev/null | jq -r .status || true)\" = 'UP' ]"

wait_for_condition "opensearch-bootstrap completed with exit code 0" 120 \
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

echo "==> Waiting 5s for Collector batch flush..."
sleep 5

echo "==> Refreshing OpenSearch indices ss4o_logs-observe-it-local and ss4o_metrics-observe-it-local..."
curl -fsS -X POST "http://localhost:9200/ss4o_logs-observe-it-local/_refresh" >/dev/null
curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_refresh" >/dev/null
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
          { "term": { "version": "'"${IMAGE_TAG}"'" } },
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
# Gate 5: OpenSearch ss4o_metrics-observe-it-local Schema & Telemetry Gate
# ----------------------------------------------------------------------
echo ""
echo "------------------------------------------------------------------------"
echo " Gate 5: OpenSearch ss4o_metrics-observe-it-local Schema & Telemetry Gate"
echo "------------------------------------------------------------------------"

echo "==> Verifying _index_template/observe-it-ss4o-metrics and _ingest/pipeline/observe-it-metrics-pipeline exist..."
METRICS_TEMPLATE_COUNT="$(curl -fsS "http://localhost:9200/_index_template/observe-it-ss4o-metrics" | jq '.index_templates | length')"
if [ "${METRICS_TEMPLATE_COUNT}" -lt 1 ]; then
  echo "FAIL: _index_template/observe-it-ss4o-metrics does not exist"
  exit 1
fi
METRICS_PIPELINE_COUNT="$(curl -fsS "http://localhost:9200/_ingest/pipeline/observe-it-metrics-pipeline" | jq 'keys | length')"
if [ "${METRICS_PIPELINE_COUNT}" -lt 1 ]; then
  echo "FAIL: _ingest/pipeline/observe-it-metrics-pipeline does not exist"
  exit 1
fi
echo "Index template observe-it-ss4o-metrics and pipeline observe-it-metrics-pipeline exist."

echo "==> Verifying ss4o_metrics-observe-it-local index health is green (1 shard, 0 replicas)..."
METRICS_INDEX_HEALTH="$(curl -fsS "http://localhost:9200/_cluster/health/ss4o_metrics-observe-it-local" | jq -r .status)"
if [ "${METRICS_INDEX_HEALTH}" != "green" ]; then
  echo "FAIL: ss4o_metrics-observe-it-local index health is '${METRICS_INDEX_HEALTH}', expected 'green'"
  exit 1
fi

METRICS_INDEX_SETTINGS="$(curl -fsS "http://localhost:9200/ss4o_metrics-observe-it-local/_settings")"
METRICS_SHARDS="$(echo "${METRICS_INDEX_SETTINGS}" | jq -r '.[].settings.index.number_of_shards')"
METRICS_REPLICAS="$(echo "${METRICS_INDEX_SETTINGS}" | jq -r '.[].settings.index.number_of_replicas')"
if [ "${METRICS_SHARDS}" != "1" ] || [ "${METRICS_REPLICAS}" != "0" ]; then
  echo "FAIL: Expected 1 shard and 0 replicas on ss4o_metrics-observe-it-local, got shards=${METRICS_SHARDS}, replicas=${METRICS_REPLICAS}"
  exit 1
fi
echo "Metrics index health is green (1 shard, 0 replicas)."

echo "==> Verifying normalized fields, 0 attributes.data_stream, and DELTA temporality..."
for svc in "observed-app" "traffic-generator"; do
  SVC_METRIC_DOCS="$(curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_count" \
    -H "Content-Type: application/json" \
    -d "{
      \"query\": {
        \"bool\": {
          \"must\": [
            {\"term\": {\"service\": \"${svc}\"}},
            {\"term\": {\"version\": \"${IMAGE_TAG}\"}},
            {\"term\": {\"env\": \"local\"}}
          ]
        }
      }
    }" | jq -r .count)"
  if [ "${SVC_METRIC_DOCS}" -le 0 ]; then
    echo "FAIL: No normalized metric documents found for service=${svc}, version=${IMAGE_TAG}, env=local"
    exit 1
  fi
  echo "✔ ${svc}: ${SVC_METRIC_DOCS} normalized metric documents."
done

DATA_STREAM_DOCS="$(curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_count" \
  -H "Content-Type: application/json" \
  -d '{"query":{"exists":{"field":"attributes.data_stream"}}}' | jq -r .count)"
if [ "${DATA_STREAM_DOCS}" -ne 0 ]; then
  echo "FAIL: Found ${DATA_STREAM_DOCS} metric documents with attributes.data_stream"
  exit 1
fi
echo "✔ 0 metric documents contain attributes.data_stream."

NON_DELTA_COUNT="$(curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_count" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          {
            "bool": {
              "should": [
                {"bool": {"must": [{"term": {"kind": "SUM"}}, {"term": {"monotonic": true}}]}},
                {"term": {"kind": "HISTOGRAM"}}
              ],
              "minimum_should_match": 1
            }
          }
        ],
        "must_not": [{"term": {"aggregationTemporality": "AGGREGATION_TEMPORALITY_DELTA"}}]
      }
    }
  }' | jq -r .count)"
if [ "${NON_DELTA_COUNT}" -ne 0 ]; then
  echo "FAIL: Found ${NON_DELTA_COUNT} monotonic SUM/HISTOGRAM documents without AGGREGATION_TEMPORALITY_DELTA"
  exit 1
fi
echo "✔ All monotonic SUM (counter) and HISTOGRAM documents use AGGREGATION_TEMPORALITY_DELTA."

assert_terms_keys_present() {
  local metric_name="$1"
  local field_name="$2"
  shift 2
  local expected_keys=("$@")

  local keys
  keys="$(curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_search" \
    -H "Content-Type: application/json" \
    -d "{
      \"size\": 0,
      \"query\": {\"term\": {\"name\": \"${metric_name}\"}},
      \"aggs\": {\"keys\": {\"terms\": {\"field\": \"${field_name}\", \"size\": 50}}}
    }" | jq -r '.aggregations.keys.buckets[].key')"

  for expected_key in "${expected_keys[@]}"; do
    if ! echo "${keys}" | grep -qx "${expected_key}"; then
      echo "FAIL: Metric '${metric_name}' missing ${field_name}='${expected_key}'. Actual keys:"
      echo "${keys}"
      exit 1
    fi
  done
  echo "✔ ${metric_name}: all expected ${field_name} keys present (${expected_keys[*]})."
}

assert_metric_agg_threshold() {
  local metric_name="$1"
  local agg_type="$2"
  local op="$3"
  local threshold="$4"

  local actual
  actual="$(curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_search" \
    -H "Content-Type: application/json" \
    -d "{
      \"size\": 0,
      \"query\": {\"term\": {\"name\": \"${metric_name}\"}},
      \"aggs\": {\"val\": {\"${agg_type}\": {\"field\": \"value\"}}}
    }" | jq -r '.aggregations.val.value // 0')"

  if [ "$(awk "BEGIN {print (${actual} ${op} ${threshold})}")" != "1" ]; then
    echo "FAIL: ${metric_name} ${agg_type}(value) is ${actual}, expected ${op} ${threshold}"
    exit 1
  fi
  echo "✔ ${metric_name} ${agg_type}(value)=${actual} (${op} ${threshold})."
}

echo "==> Verifying all 6 Observed App domain meters..."
assert_metric_agg_threshold "orders.created" "sum" ">=" "7"

assert_terms_keys_present "order.state.transitions" "attributes.order.state" \
  "RESERVED" "PAID" "SHIPPED" "FAILED" "CANCELLED"

assert_terms_keys_present "order.errors" "attributes.error.type" \
  "INVALID_STATE_TRANSITION" "OUT_OF_STOCK" "PAYMENT_DECLINED" "GATEWAY_TIMEOUT"

assert_terms_keys_present "order.inventory.reservations" "attributes.inventory.outcome" \
  "RESERVED" "LOW_STOCK_WARNING" "OUT_OF_STOCK"

assert_terms_keys_present "order.payment.duration" "attributes.payment.outcome" \
  "SUCCESS" "DECLINED" "TIMEOUT"

PAYMENT_TIMEOUT_RES="$(curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_search?size=5" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          {"term": {"name": "order.payment.duration"}},
          {"term": {"attributes.payment.outcome": "TIMEOUT"}},
          {"range": {"max": {"gte": 1000}}},
          {"nested": {"path": "buckets", "query": {"range": {"buckets.count": {"gt": 0}}}}}
        ]
      }
    }
  }')"
PAYMENT_TIMEOUT_COUNT="$(echo "${PAYMENT_TIMEOUT_RES}" | jq -r '.hits.total.value')"
if [ "${PAYMENT_TIMEOUT_COUNT}" -le 0 ]; then
  echo "FAIL: order.payment.duration TIMEOUT missing max >= 1000 or non-empty buckets"
  exit 1
fi
echo "✔ order.payment.duration TIMEOUT has max >= 1000 and non-empty buckets (${PAYMENT_TIMEOUT_COUNT} docs)."

assert_metric_agg_threshold "order.repository.size" "max" ">=" "7"

echo "==> Verifying all 4 Traffic Generator meters..."
assert_terms_keys_present "traffic.scenarios.executed" "attributes.checkout.scenario" "${SCENARIOS[@]}"
assert_terms_keys_present "traffic.scenario.duration" "attributes.checkout.scenario" "${SCENARIOS[@]}"

assert_metric_agg_threshold "traffic.target_rate" "max" ">" "0"
assert_metric_agg_threshold "traffic.workers.active" "max" ">" "0"

echo "==> Verifying auto-instrumented HTTP and JVM meters..."
for auto_meter in "http.server.request.duration" "http.client.request.duration" "jvm.thread.count" "jvm.cpu.recent_utilization"; do
  AUTO_COUNT="$(curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_count" \
    -H "Content-Type: application/json" \
    -d "{\"query\":{\"term\":{\"name\":\"${auto_meter}\"}}}" | jq -r .count)"
  if [ "${AUTO_COUNT}" -le 0 ]; then
    echo "FAIL: Auto-instrumented meter '${auto_meter}' has 0 documents"
    exit 1
  fi
  echo "✔ ${auto_meter}: ${AUTO_COUNT} documents."
done

JVM_MEM_MATCHES="$(curl -fsS -X POST "http://localhost:9200/ss4o_metrics-observe-it-local/_count" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          {"term": {"name": "jvm.memory.used"}},
          {"range": {"value@int": {"gt": 0}}},
          {"range": {"value": {"gt": 0}}}
        ]
      }
    }
  }' | jq -r .count)"
if [ "${JVM_MEM_MATCHES}" -le 0 ]; then
  echo "FAIL: jvm.memory.used has 0 documents with positive 64-bit value@int and value"
  exit 1
fi
echo "✔ jvm.memory.used: ${JVM_MEM_MATCHES} documents with positive value@int and value."
echo "Gate 5 PASSED: OpenSearch ss4o_metrics-observe-it-local Schema & Telemetry Gate"

# ----------------------------------------------------------------------
# Gate 6: OpenSearch Dashboards Saved Objects Gate
# ----------------------------------------------------------------------
echo ""
echo "------------------------------------------------------------------------"
echo " Gate 6: OpenSearch Dashboards Saved Objects Gate"
echo "------------------------------------------------------------------------"

SAVED_OBJECTS="$(curl -fsS -H "osd-xsrf: true" \
  "http://localhost:5601/api/saved_objects/_find?per_page=100&type=index-pattern&type=search&type=visualization&type=dashboard" | \
  jq -r '.saved_objects[] | "\(.type):\(.id)"')"

EXPECTED_SAVED_OBJECTS=(
  "index-pattern:ss4o_logs"
  "index-pattern:ss4o_metrics"
  "search:all-order-logs"
  "search:warnings-and-rejections"
  "search:gateway-timeouts-and-errors"
  "search:checkout-scenario-explorer"
  "search:all-metrics"
  "search:domain-order-metrics"
  "visualization:traffic-scenarios-by-outcome"
  "visualization:traffic-scenario-duration"
  "visualization:order-state-transitions"
  "visualization:order-errors-by-type"
  "visualization:order-payment-latency"
  "visualization:inventory-reservations-by-outcome"
  "visualization:http-server-requests-and-latency"
  "visualization:jvm-heap-memory-by-service"
  "visualization:runtime-gauges-kpi"
  "dashboard:observe-it-metrics-overview"
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

for pattern_id in "ss4o_logs" "ss4o_metrics"; do
  INDEX_PATTERN_HAS_TIMESTAMP="$(curl -fsS -H "osd-xsrf: true" \
    "http://localhost:5601/api/saved_objects/index-pattern/${pattern_id}" | \
    jq -r '.attributes.fields // ""' | jq -r '[.[] | select(.name == "@timestamp")] | length')"

  if [ "${INDEX_PATTERN_HAS_TIMESTAMP}" -ne 1 ]; then
    echo "FAIL: OpenSearch Dashboards index-pattern ${pattern_id} does not have '@timestamp' in attributes.fields."
    exit 1
  fi
  echo "✔ Index pattern ${pattern_id} has populated fields containing '@timestamp'."
done
echo "Gate 6 PASSED: OpenSearch Dashboards Saved Objects Gate"

echo ""
echo "========================================================================"
echo " ALL GATES PASSED! End-to-End Verification Suite Succeeded."
echo "========================================================================"

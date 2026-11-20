#!/usr/bin/env bash
set -euo pipefail

echo "==> Waiting for OpenSearch (http://opensearch:9200) to be available..."
until curl -fsS http://opensearch:9200/_cluster/health >/dev/null 2>&1; do
  sleep 1
done
echo "==> OpenSearch is available."

echo "==> Registering log ingest pipeline: observe-it-logs-pipeline..."
curl -fsS -X PUT "http://opensearch:9200/_ingest/pipeline/observe-it-logs-pipeline" \
  -H "Content-Type: application/json" \
  -d "@/bootstrap/ingest-pipeline.json"
echo ""
echo "==> Log ingest pipeline registered."

echo "==> Registering SS4O log index template: observe-it-ss4o-logs..."
curl -fsS -X PUT "http://opensearch:9200/_index_template/observe-it-ss4o-logs" \
  -H "Content-Type: application/json" \
  -d "@/bootstrap/index-template.json"
echo ""
echo "==> Log index template registered."

echo "==> Registering metrics ingest pipeline: observe-it-metrics-pipeline..."
curl -fsS -X PUT "http://opensearch:9200/_ingest/pipeline/observe-it-metrics-pipeline" \
  -H "Content-Type: application/json" \
  -d "@/bootstrap/metrics-ingest-pipeline.json"
echo ""
echo "==> Metrics ingest pipeline registered."

echo "==> Registering SS4O metrics index template: observe-it-ss4o-metrics..."
curl -fsS -X PUT "http://opensearch:9200/_index_template/observe-it-ss4o-metrics" \
  -H "Content-Type: application/json" \
  -d "@/bootstrap/metrics-index-template.json"
echo ""
echo "==> Metrics index template registered."

echo "==> Registering traces ingest pipeline: observe-it-traces-pipeline..."
curl -fsS -X PUT "http://opensearch:9200/_ingest/pipeline/observe-it-traces-pipeline" \
  -H "Content-Type: application/json" \
  -d "@/bootstrap/traces-ingest-pipeline.json"
echo ""
echo "==> Traces ingest pipeline registered."

echo "==> Registering SS4O traces index template: observe-it-ss4o-traces..."
curl -fsS -X PUT "http://opensearch:9200/_index_template/observe-it-ss4o-traces" \
  -H "Content-Type: application/json" \
  -d "@/bootstrap/traces-index-template.json"
echo ""
echo "==> Traces index template registered."

echo "==> Upserting OpenSearch SQL Prometheus datasource: observe_it_prometheus..."
PROMETHEUS_DATASOURCE_PAYLOAD='{
  "name": "observe_it_prometheus",
  "connector": "prometheus",
  "properties": {
    "prometheus.uri": "http://prometheus:9090"
  }
}'
PROMETHEUS_DS_STATUS="$(curl -s -o /dev/null -w "%{http_code}" "http://opensearch:9200/_plugins/_query/_datasources/observe_it_prometheus" || true)"
if [ "${PROMETHEUS_DS_STATUS}" = "200" ]; then
  curl -fsS -X PUT "http://opensearch:9200/_plugins/_query/_datasources" \
    -H "Content-Type: application/json" \
    -d "${PROMETHEUS_DATASOURCE_PAYLOAD}"
else
  curl -fsS -X POST "http://opensearch:9200/_plugins/_query/_datasources" \
    -H "Content-Type: application/json" \
    -d "${PROMETHEUS_DATASOURCE_PAYLOAD}"
fi
echo ""
echo "==> OpenSearch SQL Prometheus datasource upserted."

echo "==> Waiting for OpenSearch Dashboards (http://opensearch-dashboards:5601/api/status) to be available..."
until [ "$(curl -s -o /dev/null -w "%{http_code}" http://opensearch-dashboards:5601/api/status || true)" = "200" ]; do
  sleep 2
done
echo "==> OpenSearch Dashboards is available."

echo "==> Upserting Observability workspace: observe-it..."
WORKSPACE_GET_RESPONSE="$(curl -s -H "osd-xsrf: true" "http://opensearch-dashboards:5601/api/workspaces/observe-it" || true)"
if echo "${WORKSPACE_GET_RESPONSE}" | grep -q '"success":true'; then
  curl -fsS -X PUT "http://opensearch-dashboards:5601/api/workspaces/observe-it" \
    -H "osd-xsrf: true" \
    -H "Content-Type: application/json" \
    -d '{
      "attributes": {
        "name": "Observe-It",
        "description": "Observe-It local observability workspace",
        "features": ["use-case-observability"]
      },
      "settings": {}
    }'
else
  curl -fsS -X POST "http://opensearch-dashboards:5601/api/workspaces" \
    -H "osd-xsrf: true" \
    -H "Content-Type: application/json" \
    -d '{
      "attributes": {
        "id": "observe-it",
        "name": "Observe-It",
        "description": "Observe-It local observability workspace",
        "features": ["use-case-observability"]
      },
      "settings": {}
    }'
fi
echo ""
echo "==> Observability workspace upserted."

echo "==> Upserting Prometheus data-connection: observe-it-prometheus..."
curl -fsS -X POST "http://opensearch-dashboards:5601/api/saved_objects/data-connection/observe-it-prometheus?overwrite=true" \
  -H "osd-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "connectionId": "observe_it_prometheus",
      "type": "Prometheus"
    }
  }'
echo ""
echo "==> Associating data-connection observe-it-prometheus with workspace observe-it..."
curl -fsS -X POST "http://opensearch-dashboards:5601/api/workspaces/_associate" \
  -H "osd-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "workspaceId": "observe-it",
    "savedObjects": [
      { "type": "data-connection", "id": "observe-it-prometheus" }
    ]
  }'
echo ""
echo "==> Workspace-scoped Prometheus data-connection upserted."

echo "==> Importing Dashboards saved objects (saved-objects.ndjson) into workspace observe-it..."
curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/saved_objects/_import?overwrite=true" \
  -H "osd-xsrf: true" \
  --form file=@/bootstrap/saved-objects.ndjson
echo ""
echo "==> Saved objects imported."

echo "==> Setting workspace-scoped default index pattern to ss4o_logs..."
curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/opensearch-dashboards/settings" \
  -H "osd-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"changes":{"defaultIndex":"ss4o_logs"}}'
echo ""

echo "==> Upserting Traces -> Logs correlation: trace-to-logs-otel-v1-apm-span..."
curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/saved_objects/correlations/trace-to-logs-otel-v1-apm-span?overwrite=true" \
  -H "osd-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "title": "trace-to-logs_otel-v1-apm-span-*",
      "correlationType": "trace-to-logs-otel-v1-apm-span-*",
      "version": "1.0.0",
      "entities": [
        { "tracesDataset": { "id": "references[0].id" } },
        {
          "logsDataset": {
            "id": "references[1].id",
            "meta": {
              "logSpanIdField": "spanId",
              "logTraceIdField": "traceId",
              "logMessage": "message",
              "timestamp": "@timestamp"
            }
          }
        }
      ]
    },
    "references": [
      { "name": "entities[0].index", "type": "index-pattern", "id": "otel-v1-apm-span" },
      { "name": "entities[1].index", "type": "index-pattern", "id": "ss4o_logs" }
    ]
  }'
echo ""
echo "==> Traces -> Logs correlation upserted."

echo "==> Upserting APM Config correlation: apm-config-observe-it..."
curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/saved_objects/correlations/apm-config-observe-it?overwrite=true" \
  -H "osd-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "correlationType": "APM-Config-observe-it",
      "version": "1.0.0",
      "entities": [
        { "tracesDataset": { "id": "references[0].id" } },
        { "serviceMapDataset": { "id": "references[1].id" } },
        { "prometheusDataSource": { "id": "references[2].id" } },
        { "windowDuration": 60 }
      ]
    },
    "references": [
      { "name": "entities[0].index", "type": "index-pattern", "id": "otel-v1-apm-span" },
      { "name": "entities[1].index", "type": "index-pattern", "id": "otel-v2-apm-service-map" },
      { "name": "entities[2].dataConnection", "type": "data-connection", "id": "observe-it-prometheus" }
    ]
  }'
echo ""
echo "==> APM Config correlation upserted."
echo "==> Bootstrap completed successfully."

#!/usr/bin/env bash
set -euo pipefail

echo "==> Waiting for OpenSearch (http://opensearch:9200) to be available..."
until curl -fsS http://opensearch:9200/_cluster/health >/dev/null 2>&1; do
  sleep 1
done
echo "==> OpenSearch is available."

echo "==> Registering SS4O log index template: observe-it-ss4o-logs..."
curl -fsS -X PUT "http://opensearch:9200/_index_template/observe-it-ss4o-logs" \
  -H "Content-Type: application/json" \
  -d "@/bootstrap/index-template.json"
echo ""
echo "==> Index template registered."

echo "==> Waiting for OpenSearch Dashboards (http://opensearch-dashboards:5601/api/status) to be available..."
until [ "$(curl -s -o /dev/null -w "%{http_code}" http://opensearch-dashboards:5601/api/status || true)" = "200" ]; do
  sleep 2
done
echo "==> OpenSearch Dashboards is available."

echo "==> Importing Dashboards saved objects (saved-objects.ndjson)..."
curl -fsS -X POST "http://opensearch-dashboards:5601/api/saved_objects/_import?overwrite=true" \
  -H "osd-xsrf: true" \
  --form file=@/bootstrap/saved-objects.ndjson
echo ""
echo "==> Saved objects imported."

echo "==> Setting default index pattern to ss4o_logs..."
curl -fsS -X POST "http://opensearch-dashboards:5601/api/opensearch-dashboards/settings" \
  -H "osd-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"changes":{"defaultIndex":"ss4o_logs"}}' || \
curl -fsS -X POST "http://opensearch-dashboards:5601/api/kibana/settings" \
  -H "osd-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"changes":{"defaultIndex":"ss4o_logs"}}' || true
echo ""
echo "==> Bootstrap completed successfully."

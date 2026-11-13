# 0007. SS4O Metrics Delta Temporality and OpenSearch Schema Normalization

**Status:** Accepted

## Context

When the OpenTelemetry Java Agent exports JVM, HTTP, and custom domain metrics over OTLP to the OpenTelemetry
Collector's `opensearch` exporter in `ss4o` mode, three operational issues arise in OpenSearch and OpenSearch
Dashboards:

1. **Cumulative vs. delta aggregation**: By default, OpenTelemetry exports counters and histograms with `CUMULATIVE`
   temporality (monotonically increasing totals since process start). Querying cumulative counters in OpenSearch
   Dashboards bar charts requires computing per-bucket derivatives across process restarts, whereas `DELTA` temporality
   emits the exact change per export interval so time-bucket `sum` aggregations work directly.
2. **64-bit integer mapping overflow and split scalar fields**: The Collector's `ss4o` exporter serializes integer
   metric points under `value@int` and floating-point metric points under `value@double`. Without an explicit template,
   OpenSearch dynamic mapping can infer `value@int` as a 32-bit `integer`, which overflows on large JVM byte counters
   (such as `jvm.memory.limit` or `jvm.memory.used`), and splitting numeric values across `value@int` and `value@double`
   prevents a single OpenSearch Dashboards visualization from charting both integer and double metrics on a unified
   `value` field.
3. **Cross-signal resource field nesting**: Like SS4O logs ([ADR-0002](0002-otel-collector-ss4o-and-opensearch-ingest-pipeline.md)),
   raw SS4O metric documents store service metadata under `resource['service.name']`, `resource['service.version']`, and
   `resource['deployment.environment.name']` rather than top-level `service`, `version`, and `env` fields.

## Decision

We configure 1-second `DELTA` metric export on both JVM services and provision a dedicated OpenSearch ingest pipeline
and index template for `ss4o_metrics-*`:

1. **1-second `DELTA` temporality on both services**:
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) configures
   `OTEL_METRICS_EXPORTER=otlp`, `OTEL_METRIC_EXPORT_INTERVAL=1000`, and
   `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=delta` on both `observed-app` and `traffic-generator`, routed
   through the Collector's `metrics` pipeline (`receivers: [otlp]`, `processors: [memory_limiter, batch]`,
   `exporters: [opensearch]`) in
   [`docker/otel-collector/config.yaml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/otel-collector/config.yaml).
2. **Custom `observe-it-ss4o-metrics` index template**:
   [`docker/opensearch/metrics-index-template.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/metrics-index-template.json)
   registers template `observe-it-ss4o-metrics` for `ss4o_metrics-*` (priority 200, `default_pipeline:
   observe-it-metrics-pipeline`). It maps `value@int` as 64-bit `long`, `value` and `value@double` as `double`,
   histogram aggregates (`sum`, `min`, `max`, `explicitBoundsList`) as `double`, `count` and `bucketCountsList` as
   `long`, nested `buckets` / `positiveBuckets` / `negativeBuckets`, and explicit `keyword` / `long` mappings for domain
   (`attributes.order.*`, `attributes.inventory.*`, `attributes.payment.*`, `attributes.checkout.scenario`,
   `attributes.error.type`) and runtime (`attributes.http.*`, `attributes.jvm.*`) attributes.
3. **Cross-signal `observe-it-metrics-pipeline` ingest processor**:
   [`docker/opensearch/metrics-ingest-pipeline.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/metrics-ingest-pipeline.json)
   runs a Painless script that promotes `resource['service.name']` → `service` (with `serviceName` fallback),
   `resource['service.version']` → `version`, and `resource['deployment.environment.name']` → `env`, copies
   `value@double` or `value@int` into a unified `double` `value` field, and strips `attributes.data_stream`.
   [`docker/opensearch/bootstrap.sh`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/bootstrap.sh)
   registers both the pipeline and index template before `otel-collector` starts.

## Consequences

- **Good, because** counter and histogram intervals aggregate cleanly with simple `sum`, `avg`, and `max` bucket
  operations in OpenSearch Dashboards without derivative math or restart-reset handling.
- **Good, because** mapping `value@int` as 64-bit `long` prevents dynamic 32-bit `integer` overflow on large JVM byte
  gauges (such as `jvm.memory.limit`).
- **Good, because** top-level `service`, `version`, `env`, and unified `value` fields align `ss4o_metrics-*` with
  `ss4o_logs-*` for cross-signal filtering and single-field visualizations.
- **Bad, because** `DELTA` temporality requires summing over a time window rather than reading the latest cumulative
  point for counters, and a 1-second export interval increases Collector and OpenSearch write volume in local
  development.
- **Bad, because** normalization logic in `observe-it-metrics-pipeline` and field mappings in
  `observe-it-ss4o-metrics` are coupled to the Collector's `ss4o` JSON shape (`value@int`, `value@double`,
  `resource.*`) and must stay in sync across Collector upgrades.
- **Neutral, because** asynchronous gauges (`jvm.memory.used`, `order.repository.size`, `traffic.target_rate`,
  `traffic.workers.active`) still report instantaneous point-in-time values regardless of temporality preference.

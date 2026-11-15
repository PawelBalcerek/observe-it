# 0009. SS4O Traces Ingest Normalization and OpenSearch Dashboards Exploration

**Status:** Accepted

## Context

Exporting OTLP spans from **Observed App** and **Traffic Generator** through the OpenTelemetry Collector's `opensearch`
exporter in `ss4o` mode into the **SS4O Traces Index** (`ss4o_traces-*`) introduces three schema and exploration gaps
when querying traces in OpenSearch and OpenSearch Dashboards:

1. **Missing `@timestamp` and precomputed span duration fields**: Raw SS4O span documents record `startTime` and
   `endTime` (`date_nanos`) without a top-level `@timestamp` field or precomputed `durationInNanos` and `durationMs`
   fields. OpenSearch Dashboards index patterns, time-picker filters, and latency percentile/average visualizations
   expect `@timestamp` and numeric duration fields on every span document.
2. **Cross-signal resource field nesting**: Like SS4O logs
   ([ADR-0002](0002-otel-collector-ss4o-and-opensearch-ingest-pipeline.md)) and SS4O metrics
   ([ADR-0007](0007-ss4o-metrics-delta-temporality-and-opensearch-normalization.md)), raw SS4O span documents nest
   service metadata under `resource['service.name']`, `resource['service.version']`, and
   `resource['deployment.environment.name']` rather than top-level `service`, `serviceName`, `version`, and `env`
   fields.
3. **Dynamic mapping hazards on nested span events and attributes**: Without an explicit index template, OpenSearch
   dynamic mapping can mis-type numeric or dotted attributes (`attributes.order.*`, `attributes.payment.latency_ms`),
   `events.attributes.exception.*` (`stacktrace`, `message`, `type`), or `status.message`, and leaves
   `attributes.data_stream` cluttering every span document.

## Decision

We enable the Collector `traces` pipeline and provision a dedicated OpenSearch ingest pipeline, index template, and
pre-built Dashboards saved objects for `ss4o_traces-*`:

1. **OTLP trace export and Collector `traces` pipeline**:
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) sets
   `OTEL_TRACES_EXPORTER=otlp` and `OTEL_BSP_SCHEDULE_DELAY=1000` on both `observed-app` and `traffic-generator`,
   routing spans through the Collector's `traces` pipeline (`receivers: [otlp]`, `processors: [memory_limiter, batch]`,
   `exporters: [opensearch]`) in
   [`docker/otel-collector/config.yaml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/otel-collector/config.yaml)
   with `mapping.mode: ss4o`, `dataset: observe-it`, and `namespace: local`.
2. **Custom `observe-it-ss4o-traces` index template**:
   [`docker/opensearch/traces-index-template.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/traces-index-template.json)
   registers template `observe-it-ss4o-traces` for `ss4o_traces-*` (priority 200, `number_of_shards: 1`,
   `number_of_replicas: 0`, `default_pipeline: observe-it-traces-pipeline`). It maps `@timestamp`, `startTime`,
   `endTime`, and `events.time` as `date_nanos`; `durationInNanos` as `long`; `durationMs` as `double`; `traceId`,
   `spanId`, `parentSpanId`, `name`, `kind`, `service`, `serviceName`, `version`, `env`, and `status.code` as `keyword`;
   `status.message` and `events.attributes.exception.message` as `text` with `.keyword` subfields;
   `events.attributes.exception.stacktrace` as `text`; and explicit types for all domain and HTTP span attributes.
3. **`observe-it-traces-pipeline` ingest normalization**:
   [`docker/opensearch/traces-ingest-pipeline.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/traces-ingest-pipeline.json)
   executes a Painless script that sets `@timestamp` from `startTime`, computes `durationInNanos` (clamped to `>= 0`)
   and `durationMs = nanos / 1000000.0d`, promotes `resource['service.name']` → `service` and `serviceName`,
   `resource['service.version']` → `version`, and `resource['deployment.environment.name']` → `env`, and removes
   `attributes.data_stream`.
   [`docker/opensearch/bootstrap.sh`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/bootstrap.sh)
   registers the pipeline and template before `otel-collector` starts.
4. **Pre-provisioned OpenSearch Dashboards trace exploration objects**:
   [`docker/opensearch/saved-objects.ndjson`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/saved-objects.ndjson)
   provisions the `ss4o_traces` index pattern (`ss4o_traces-*`), four saved searches (`All Trace Spans`,
   `Checkout Scenario Traces`, `Domain Order Spans`, `Error & Timeout Spans`), seven visualizations, and the
   `Observe-It Traces Overview` dashboard, verified end-to-end by Gates 6 and 7 in
   [`scripts/verify.sh`](https://github.com/PawelBalcerek/observe-it/blob/main/scripts/verify.sh).

## Considered Options

1. **Adding OpenSearch Data Prepper or a dedicated trace backend (Jaeger / Tempo)**:
   Introducing Data Prepper or a separate trace store alongside OpenSearch was rejected for Milestone 3 because routing
   OTLP traces directly through the OpenTelemetry Collector `opensearch` exporter (`mode: ss4o`) keeps the local
   container topology minimal and stores logs, metrics, and traces in one OpenSearch cluster for cross-signal DQL
   correlation.
2. **Relying on OpenSearch dynamic mapping without `observe-it-traces-pipeline`**:
   Indexing raw Collector `ss4o` span documents without ingest normalization was rejected because OpenSearch Dashboards
   Discover and latency visualizations require `@timestamp`, `durationMs`, top-level `service`, and typed
   `events.attributes.exception.*` fields.

## Consequences

- **Good, because** `ss4o_traces-*` shares top-level `service`, `version`, `env`, and `@timestamp` conventions with
  `ss4o_logs-*` and `ss4o_metrics-*`, enabling seamless cross-signal pivoting via `traceId` and `attributes.order.id`.
- **Good, because** precomputed `durationInNanos` and `durationMs` power `Observe-It Traces Overview` latency panels and
  DQL waterfall sorting directly without scripted fields.
- **Bad, because** `observe-it-traces-pipeline` and `observe-it-ss4o-traces` are coupled to the Collector's `ss4o`
  span JSON field names (`startTime`, `endTime`, `resource.*`, `attributes.data_stream`) and must be kept in sync across
  Collector upgrades.
- **Neutral, because** a 1-second `OTEL_BSP_SCHEDULE_DELAY` trades slightly higher local batch export frequency for fast
  end-to-end verification in `make verify`.

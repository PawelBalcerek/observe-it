# 0012. Prometheus Metrics Store and OpenTelemetry Collector Temporality Split

**Status:** Accepted

## Context

[ADR-0007](0007-ss4o-metrics-delta-temporality-and-opensearch-normalization.md) configured both **Observed App** and
**Traffic Generator** to export 1-second `DELTA` metrics directly to OpenSearch (`ss4o_metrics-*`) so classic
OpenSearch Dashboards bar charts could aggregate interval counts with simple `sum` queries without derivative math.
Milestone 5 enables the OpenSearch Dashboards `3.9.0` **Observability Workspace**—specifically **Explore Metrics**
(PromQL) and **APM** RED KPIs—which introduces two conflicting temporality requirements:

1. **Prometheus and OpenSearch Dashboards Explore Metrics require `CUMULATIVE` temporality**: OpenSearch Dashboards
   `3.9.0` Explore Metrics and APM query a Prometheus-compatible time-series backend supporting PromQL (`rate()`,
   `increase()`, `histogram_quantile()`) and `/api/v1/metadata`. Prometheus's native OTLP HTTP receiver expects
   `CUMULATIVE` counters and histograms rather than `DELTA` points.
2. **Classic `Observe-It Metrics Overview` dashboards require `DELTA` points in `ss4o_metrics-*`**: Switching both JVM
   services to `CUMULATIVE` temporality without converting points before OpenSearch ingestion would break the existing
   **SS4O Metrics Index** visualizations that sum per-interval `value` fields across time buckets.

This decision amends the app-side `DELTA` temporality portion of
[ADR-0007](0007-ss4o-metrics-delta-temporality-and-opensearch-normalization.md) while keeping `ADR-0007`'s
`observe-it-ss4o-metrics` index template and `observe-it-metrics-pipeline` ingest normalization intact.

## Decision

We adopt Prometheus as the **Prometheus Metrics Store**, switch both JVM services to OpenTelemetry's default
`CUMULATIVE` temporality, and split the OpenTelemetry Collector metrics pipeline into two isolated branches:

1. **Prometheus `prom/prometheus:v3.16.0` as the Prometheus Metrics Store**:
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) adds a `prometheus`
   container (`prom/prometheus:v3.16.0`, `mem_limit: 256m`, host-bound `127.0.0.1:9090:9090`) configured with
   `--web.enable-otlp-receiver`, `--web.enable-remote-write-receiver` (used by Data Prepper in
   [ADR-0013](0013-data-prepper-apm-trace-fanout-and-service-map.md)), and a persistent `prometheus-data` volume capped
   by `--storage.tsdb.retention.time=1d` and `--storage.tsdb.retention.size=256MB`.
   [`docker/prometheus/prometheus.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/prometheus/prometheus.yml)
   configures `otlp.promote_resource_attributes` for `service.name`, `service.version`, and
   `deployment.environment.name` so those labels appear directly on every ingested time series without `target_info`
   joins.
2. **Default `CUMULATIVE` temporality on both JVM services**:
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) removes
   `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=delta` from `observed-app` and `traffic-generator` while keeping
   `OTEL_METRICS_EXPORTER=otlp` and `OTEL_METRIC_EXPORT_INTERVAL=1000`.
3. **Isolated Collector dual-metrics pipelines (`metrics/prometheus` and `metrics/opensearch`)**:
   [`docker/otel-collector/config.yaml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/otel-collector/config.yaml)
   splits incoming OTLP metrics from the shared `otlp` receiver into two independent pipelines:
   - `metrics/prometheus`: `[memory_limiter, filter/metrics, batch]` → stateless `otlp_http/prometheus` exporter
     targeting `http://prometheus:9090/api/v1/otlp`.
   - `metrics/opensearch`: `[memory_limiter, filter/metrics, cumulative_to_delta, batch]` → existing `opensearch`
     (`mapping.mode: ss4o`) exporter targeting `ss4o_metrics-observe-it-local`, with `cumulative_to_delta` configured
     using `initial_value: auto` and `max_staleness: 1h`.

## Considered Options

1. **VictoriaMetrics instead of Prometheus**:
   Using VictoriaMetrics as the PromQL backend was rejected because OpenSearch Dashboards `3.9.0` Explore Metrics
   relies on strict PromQL semantics and authoritative `/api/v1/metadata` responses, and `prom/prometheus:v3.x` fits
   comfortably within the `256m` memory budget while matching the canonical OpenTelemetry + OpenSearch reference stack.
2. **Keeping app-side `DELTA` export and converting `DELTA` to `CUMULATIVE` in the Collector (`deltatocumulative`)**:
   Converting `DELTA` to `CUMULATIVE` in the Collector was rejected because `deltatocumulative` holds unbounded
   long-lived cumulative state in Collector memory and is less mature than `cumulative_to_delta`, whereas exporting
   `CUMULATIVE` from the services keeps the Prometheus hot path stateless and aligns with OpenTelemetry's SDK default.
3. **Replacing `ss4o_metrics-*` entirely with Prometheus**:
   Dropping `ss4o_metrics-*` and `Observe-It Metrics Overview` was rejected because Milestone 5 preserves all 34 classic
   OpenSearch Dashboards saved objects and `ss4o_*` workflows alongside the new **Observability Workspace**.

## Consequences

- **Good, because** both `observed-app` and `traffic-generator` use OpenTelemetry's standard `CUMULATIVE` temporality
  default while the `metrics/prometheus` branch remains completely stateless inside the Collector.
- **Good, because** OpenSearch Dashboards `3.9.0` **Explore Metrics** and **APM** gain strict PromQL and
  `/api/v1/metadata` support with promoted `service_name`, `service_version`, and `deployment_environment_name` labels.
- **Good, because** `cumulative_to_delta` preserves 100% compatibility with the existing **SS4O Metrics Index**
  (`ss4o_metrics-*`), `observe-it-metrics-pipeline`, and classic `Observe-It Metrics Overview` dashboard.
- **Bad, because** running Prometheus adds `256m` to the local Docker Compose memory footprint and introduces a second
  metrics store (`prometheus-data` volume) alongside OpenSearch.
- **Bad, because** `cumulative_to_delta` (`beta` stability) maintains per-series previous-point state in Collector memory
  up to `max_staleness: 1h` and drops the first point after a Collector restart under `initial_value: auto`.
- **Neutral, because** `filter/metrics` runs identically in both branches so `/actuator/health` and MongoDB admin probe
  noise remain excluded from both **Prometheus Metrics Store** and **SS4O Metrics Index**.

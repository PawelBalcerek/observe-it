# 0013. Traces-Only Data Prepper, OTel Collector Trace Fan-Out, and APM Service Map

**Status:** Accepted

## Context

[ADR-0009](0009-ss4o-traces-ingest-normalization-and-dashboards-exploration.md) routes OTLP spans from the
OpenTelemetry Collector directly into the **SS4O Traces Index** (`ss4o_traces-*`) to power five classic Discover saved
searches and the `Observe-It Traces Overview` dashboard. Enabling the OpenSearch Dashboards `3.9.0` **Observability
Workspace**—including **Explore Traces**, one-click **Traces → Logs** correlation, and **APM** (Services, Operations,
Dependencies, and Service Map)—exposes three capabilities that the Collector's `opensearch` (`ss4o`) exporter cannot
provide on its own:

1. **APM span enrichment (`otel-v1-apm-span-*`)**: OpenSearch Dashboards `3.9.0` trace exploration and APM drill-downs
   expect OpenSearch Data Prepper's `otel_traces` schema (`otel-v1-apm-span-*`), including stateful `traceGroup` and
   `traceGroupFields` enrichment computed across root spans.
2. **Service map topology and database dependency synthesis (`otel-v2-apm-service-map*`)**: The APM Services and
   Service Map views require aggregated service-to-service and dependency edges (`traffic-generator` → `observed-app` →
   `mongodb`) synthesized over sliding time windows by Data Prepper's `otel_apm_service_map` processor.
3. **Span-derived APM RED metrics in Prometheus**: APM service and operation KPI cards query delta RED metrics
   (`request`, `error`, `fault`, `latency_seconds_bucket` under `namespace="span_derived"`) remote-written to the
   **Prometheus Metrics Store**.

Replacing `ss4o_traces-*` outright would break 15 existing classic saved objects (`Observe-It Traces Overview`, five
saved searches, and eight visualizations), so [ADR-0009](0009-ss4o-traces-ingest-normalization-and-dashboards-exploration.md)
remains intact alongside the new APM pipeline.

## Decision

We introduce a traces-only OpenSearch Data Prepper container fed by an OpenTelemetry Collector trace fan-out while
keeping all log and metric ingestion on the Collector:

1. **Collector trace fan-out (`traces/opensearch` and `traces/dataprepper`)**:
   [`docker/otel-collector/config.yaml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/otel-collector/config.yaml)
   keeps `otel-collector:4317` as the single OTLP ingress point and noise filter (`filter/traces` drops
   `/actuator/health` and MongoDB `hello`, `isMaster`, and `listDatabases` admin commands) and fans spans out to two
   isolated pipelines:
   - `traces/opensearch`: `[memory_limiter, filter/traces, batch]` → existing `opensearch` (`mapping.mode: ss4o`)
     exporter → `ss4o_traces-observe-it-local`.
   - `traces/dataprepper`: `[memory_limiter, filter/traces, batch]` → `otlp/dataprepper` gRPC exporter →
     `data-prepper:21890`.
2. **Traces-only Data Prepper `2.17.0` container**:
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) adds
   `opensearchproject/data-prepper:2.17.0` (`mem_limit: 768m`, `JAVA_OPTS="-Xms256m -Xmx384m"`, internal-only on
   `observe-it-net` with no host port, healthcheck against `http://localhost:4900/list`).
   [`docker/data-prepper/data-prepper-config.yaml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/data-prepper/data-prepper-config.yaml)
   enables the `otel_apm_service_map` processor and `prometheus` sink plugins.
3. **APM span, service map, and RED metric pipelines**:
   [`docker/data-prepper/pipelines.yaml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/data-prepper/pipelines.yaml)
   accepts OTLP traces on `:21890` and fans them into two sub-pipelines:
   - `traces-raw-pipeline`: runs `otel_traces` and writes to OpenSearch (`index_type: trace-analytics-plain-raw`),
     populating the **APM Span Index** (`otel-v1-apm-span-*`).
   - `service-map-pipeline`: runs `otel_apm_service_map` (`window_duration: 60s`, ephemeral container-local
     `db_path: "/tmp/otel-apm-service-map/"`, `group_by_attributes: [telemetry.sdk.language]`,
     `dependency_nodes.enabled: true`), routing `SERVICE_MAP` events to the **APM Service Map Index**
     (`otel-v2-apm-service-map*`) and `METRIC` events to `service-metrics-prometheus-pipeline`.
   - `service-metrics-prometheus-pipeline`: runs `delete_entries` to strip `/attributes/randomKey` and `randomKey`
     (preventing Prometheus series cardinality explosion) before remote-writing `span_derived` RED metrics (`request`,
     `error`, `fault`, `latency_seconds_bucket`) to `http://prometheus:9090/api/v1/write`.

## Considered Options

1. **OpenTelemetry Collector-only pipeline with `servicegraph` / `spanmetrics` connectors**:
   Emulating APM spans and service maps purely in the OpenTelemetry Collector was rejected because OpenSearch
   Dashboards `3.9.0` APM expects Data Prepper's exact `otel-v1-apm-span-*` (`traceGroup` / `traceGroupFields`),
   `otel-v2-apm-service-map*` (`sourceNode` / `targetNode` with synthesized `database` dependency nodes), and
   `span_derived` RED metric schemas.
2. **Routing logs and metrics through Data Prepper as well**:
   Migrating logs and metrics from the OpenTelemetry Collector to Data Prepper was rejected because the Collector
   already handles `ss4o_logs-*`, `metrics/prometheus`, and `metrics/opensearch` cleanly with lower memory overhead,
   and keeping Data Prepper traces-only minimizes its heap footprint (`-Xms256m -Xmx384m` inside `mem_limit: 768m`).
3. **Replacing `ss4o_traces-*` instead of fanning out traces**:
   Dropping the `traces/opensearch` branch was rejected because it would break all 15 existing `ss4o_traces-*` classic
   saved objects and `Observe-It Traces Overview` dashboard documented in
   [ADR-0009](0009-ss4o-traces-ingest-normalization-and-dashboards-exploration.md).

## Consequences

- **Good, because** OpenSearch Dashboards `3.9.0` **Explore Traces**, **Traces → Logs**, **APM Services**, and **Service
  Map** work out of the box—including synthesized `mongodb` database dependency nodes and `PAYMENT_GATEWAY_TIMEOUT`
  fault KPIs.
- **Good, because** keeping `traces/opensearch` alongside `traces/dataprepper` leaves
  [ADR-0009](0009-ss4o-traces-ingest-normalization-and-dashboards-exploration.md) and all classic `ss4o_traces-*`
  dashboards and saved searches completely intact.
- **Good, because** stripping `randomKey` before Prometheus remote-write prevents unbounded label cardinality on
  `span_derived` RED metrics.
- **Bad, because** adding Data Prepper increases the local Docker Compose memory budget by `768m` (from `3.50 GB` to
  `4.25 GB` baseline across 8 continuous containers) and stores trace spans twice in OpenSearch (`ss4o_traces-*` and
  `otel-v1-apm-span-*`).
- **Bad, because** `otel_apm_service_map` and the `prometheus` sink are currently enabled under Data Prepper's
  `experimental.enabled_plugins` block and may evolve across Data Prepper upgrades.
- **Neutral, because** Data Prepper manages its own default index templates (resulting in `yellow` health on a
  single-node OpenSearch cluster due to 1 default replica) and uses an ephemeral container-local `db_path` for 60-second
  sliding windows, so no additional Docker volume is required.

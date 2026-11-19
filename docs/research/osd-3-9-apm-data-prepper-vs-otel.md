# OpenSearch 3.9 APM & Service Map Requirements: Data Prepper vs. OTel Collector

> Research note for [issue #162](https://github.com/PawelBalcerek/observe-it/issues/162) (parent map
> [#160](https://github.com/PawelBalcerek/observe-it/issues/160), blocking
> [#165](https://github.com/PawelBalcerek/observe-it/issues/165)). All findings are verified against primary upstream
> source code and official documentation for OpenSearch Dashboards `3.9.0`, OpenSearch Data Prepper, and OpenTelemetry
> Collector Contrib `0.164.0`.

## Executive Summary

OpenSearch Dashboards `3.9.0` splits trace observability into two distinct features with very different ingestion
requirements:

1. **Discover Traces** (`OpenSearch-Dashboards` `src/plugins/explore/`) **does NOT require Data Prepper**.
   Observe-It's existing `ss4o_traces-*` schema already satisfies every required span field validated by Discover
   Traces, though auto-detection looks for `otel-v1-apm-span*` (so `ss4o_traces-*` requires a manually configured
   `traces` dataset). Alternatively, OpenTelemetry Collector Contrib `0.164.0`'s `opensearch` exporter supports
   `mapping.mode: otel-v1`, which writes directly to `otel-v1-apm-span` without Data Prepper.
2. **APM (Services, Service Details & Application/Service Map)** (`dashboards-observability` `3.9`
   `public/components/apm/`) **strictly requires Data Prepper** (specifically the `otel_apm_service_map` processor)
   **plus a Prometheus-compatible TSDB**. OpenTelemetry Collector Contrib `0.164.0`'s `spanmetrics` and `service_graph`
   connectors **cannot** satisfy APM because:
   - APM topology and service lists are queried via PPL against an OpenSearch index (`otel-v2-apm-service-map*`)
     containing `NodeOperationDetail` documents (`sourceNode`, `targetNode`, `sourceOperation`, `targetOperation`,
     `nodeConnectionHash`, `operationConnectionHash`). No OTel Collector component writes this OpenSearch schema.
   - APM RED charts and KPIs execute hardcoded PromQL queries (`sum_over_time(...)`) against delta-temporality metrics
     named `request`, `error`, `fault`, and `latency_seconds_bucket` with labels `namespace="span_derived"`,
     `environment`, `service`, `operation`, `remoteService`, `remoteEnvironment`, and `remoteOperation`. Both
     `spanmetrics` and `service_graph` emit incompatible metric names, labels, and cumulative counter semantics.

## 1. OpenSearch Trace Index Schema Requirements

### 1.1 Discover Traces (`OpenSearch-Dashboards` `3.9.0`)

In `OpenSearch-Dashboards` `3.9.0`, Discover Traces separates **auto-detection** from **runtime span rendering**:

- **Zero-click auto-detection**
  ([`auto_detect_trace_data.ts`](https://github.com/opensearch-project/OpenSearch-Dashboards/blob/3.9.0/src/plugins/explore/public/utils/auto_detect_trace_data.ts))
  only probes the index pattern `otel-v1-apm-span*` and checks for `spanId`, `traceId`, and a valid date time field
  (`startTime` → `endTime`). Custom index patterns such as `ss4o_traces-*` skip auto-detection and must be registered
  manually as a `traces`-signal `index-pattern` saved object
  ([Discover Traces documentation](https://observability.opensearch.org/docs/investigate/discover-traces)).
- **Runtime span validation**
  ([`trace_field_validation.ts`](https://github.com/opensearch-project/OpenSearch-Dashboards/blob/3.9.0/src/plugins/explore/public/utils/trace_field_validation.ts)
  and
  [`trace_field_constants.ts`](https://github.com/opensearch-project/OpenSearch-Dashboards/blob/3.9.0/src/plugins/explore/public/utils/trace_field_constants.ts))
  validates the following fields on span documents:

| Logical Field | Accepted Paths in OSD `3.9.0`                                         | Required | Observe-It `ss4o_traces-*` Status                                            |
| ------------- | --------------------------------------------------------------------- | -------- | ---------------------------------------------------------------------------- |
| Trace ID      | `traceId`, `trace_id`, `traceID`                                      | Yes      | Present (`traceId`, `keyword`)                                               |
| Span ID       | `spanId`, `span_id`, `spanID`                                         | Yes      | Present (`spanId`, `keyword`)                                                |
| Parent Span   | `parentSpanId`, `parent_span_id`, `parentSpanID`                      | Yes      | Present (`parentSpanId`, `keyword`; empty/null allowed for root spans)       |
| Service Name  | `resource.attributes.service.name`, `serviceName`, `name`             | Yes      | Present (`serviceName`, `keyword`, promoted by `observe-it-traces-pipeline`) |
| Operation     | `name`, `operationName`, `operation_name`, `span.name`                | Yes      | Present (`name`, `keyword`)                                                  |
| Start Time    | `startTimeUnixNano`, `startTime`                                      | Yes      | Present (`startTime`, `date_nanos`)                                          |
| End Time      | `endTimeUnixNano`, `endTime`                                          | Yes      | Present (`endTime`, `date_nanos`)                                            |
| Status Code   | `status.code`, `statusCode`, `status_code`, `otel.status_code`        | Optional | Present (`status.code`, `keyword` string vs. `integer` in `otel-v1`)         |
| Duration      | `durationInNanos`, `duration_in_nanos`, `duration`, `durationNanos`   | Optional | Present (`durationInNanos`, `long`, computed by ingest pipeline)             |

Note that trace-map service click filtering hardcodes `SERVICE_NAME_FILTER_FIELD = 'serviceName'`
([`trace_field_constants.ts`](https://github.com/opensearch-project/OpenSearch-Dashboards/blob/3.9.0/src/plugins/explore/public/utils/trace_field_constants.ts)),
and `TracePPLService` queries `source = <dataset.title> | where traceId = "..."`
([`ppl_request_trace.tsx`](https://github.com/opensearch-project/OpenSearch-Dashboards/blob/3.9.0/src/plugins/explore/public/application/pages/traces/trace_details/server/ppl_request_trace.tsx)).
Observe-It's current
[`traces-index-template.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/traces-index-template.json)
and
[`traces-ingest-pipeline.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/traces-ingest-pipeline.json)
already populate every required field.

### 1.2 Differences Between `ss4o_traces-*` and Data Prepper `otel-v1-apm-span-*`

Data Prepper's `otel_traces` processor writes spans using `index_type: trace-analytics-plain-raw` against
[`otel-v1-apm-span-index-standard-template.json`](https://github.com/opensearch-project/data-prepper/blob/main/data-prepper-plugins/opensearch/src/main/resources/index-template/otel-v1-apm-span-index-standard-template.json):

| Schema Aspect                     | Current `ss4o_traces-*` (`observe-it`)                   | Data Prepper `otel-v1-apm-span-*`                                                  |
| --------------------------------- | -------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Index pattern                     | `ss4o_traces-*`                                          | `otel-v1-apm-span-*` (auto-detected by Discover Traces & APM)                      |
| Time fields                       | `@timestamp`, `startTime`, `endTime` (`date_nanos`)      | `@timestamp`, `time`, `startTime`, `endTime` (`date_nanos`)                        |
| `status.code`                     | `keyword` (`"STATUS_CODE_UNSET"`, `"STATUS_CODE_ERROR"`) | `integer` (`0`, `1`, `2`)                                                          |
| `traceGroup` & `traceGroupFields` | Not present                                              | Populated statefully by `otel_traces` (`endTime`, `durationInNanos`, `statusCode`) |
| `resource` nesting                | `resource.service.name`                                  | `resource.attributes.service.name`                                                 |
| `events` / `links`                | `object`                                                 | `nested`                                                                           |
| Derived environment               | Top-level `env` (from `deployment.environment.name`)     | `attributes.derived.environment` (matches APM `environment` label)                 |
| Custom convenience fields         | `service`, `version`, `env`, `durationMs`                | Not present                                                                        |

### 1.3 APM & Service Map OpenSearch Indices (`dashboards-observability` `3.9`)

The APM plugin (`dashboards-observability`, branch `3.9`,
[`constants.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/public/components/apm/common/constants.ts)
and
[`apm_auto_detect.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/public/components/apm/setup_wizard/utils/apm_auto_detect.ts))
validates three OpenSearch index patterns:

1. **Traces**: `otel-v1-apm-span*` — required fields `traceId`, `spanId`, `serviceName`; preferred time field
   `endTime` (fallback `startTime`).
2. **Correlated logs** (optional): `logs-otel-v1*` — required fields `traceId`, `spanId`, `time`; time field `time`,
   service field `resource.attributes.service.name`.
3. **Service Map v2 (mandatory for Services & Application Map)**: `otel-v2-apm-service-map*` — required fields:
   - `sourceNode` (`keyAttributes.name`, `keyAttributes.environment`, `groupByAttributes`, `type`)
   - `targetNode` (`keyAttributes.name`, `keyAttributes.environment`, `groupByAttributes`, `dependencyAttributes`,
     `type` — `null` for leaf services)
   - `sourceOperation` (`name`, `attributes`)
   - `targetOperation` (`name`, `attributes`)
   - `nodeConnectionHash` (deduplication key for service list and topology map)
   - `operationConnectionHash` (deduplication key for operations and dependencies tables)
   - `timestamp` (time field)

Every service discovery, topology map, operation list, and dependency list query in
[`ppl_queries.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/public/components/apm/query_services/query_requests/ppl_queries.ts)
reads from `otel-v2-apm-service-map*` via PPL (for example,
`source=otel-v2-apm-service-map* | where timestamp >= '...' | dedup nodeConnectionHash | fields`
`sourceNode.keyAttributes, targetNode.keyAttributes, ...`).

## 2. Exact Prometheus RED Metrics & Labels Required by APM `3.9.0`

OpenSearch Dashboards `3.9.0` APM queries a direct-query Prometheus data source for all RED (Rate, Errors, Duration)
KPIs, sparklines, service health colors, and dependency latency breakdowns.

From
[`constants.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/public/components/apm/common/constants.ts),
[`promql_queries.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/public/components/apm/query_services/query_requests/promql_queries.ts),
and Data Prepper's
[`otel-apm-service-map-processor/README.md`](https://github.com/opensearch-project/data-prepper/blob/main/data-prepper-plugins/otel-apm-service-map-processor/README.md)
+
[`ApmServiceMapMetricsUtil.java`](https://github.com/opensearch-project/data-prepper/blob/main/data-prepper-plugins/otel-apm-service-map-processor/src/main/java/org/opensearch/dataprepper/plugins/processor/otel_apm_service_map/utils/ApmServiceMapMetricsUtil.java):

### 2.1 Required Metric Names

The APM setup wizard checks `APM_RED_REQUIRED_METRICS = ['request', 'fault', 'latency_seconds_bucket']`, and PromQL
queries evaluate all four metrics:

| Prometheus Metric Name   | Data Prepper Source Metric | Type / Temporality                                       | Meaning                                                                          |
| ------------------------ | -------------------------- | -------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `request`                | `request` (unit `1`)       | Delta monotonic sum (per-window)                         | Request count in each evaluation window                                          |
| `error`                  | `error` (unit `1`)         | Delta monotonic sum (per-window)                         | Client error count (HTTP `4xx`)                                                  |
| `fault`                  | `fault` (unit `1`)         | Delta monotonic sum (per-window)                         | Server fault count (HTTP `5xx` or OTel `ERROR` status)                           |
| `latency_seconds_bucket` | `latency` (unit `s`)       | Delta histogram (15 explicit buckets: `0.0` to `10.0` s) | Request latency distribution (queried with `histogram_quantile` * `1000` for ms) |

> [!IMPORTANT]
> APM's PromQL queries aggregate counts with `sum_over_time(request{...}[<range>])` and
> `sum_over_time(latency_seconds_bucket{...}[<range>])` rather than `rate()` or `increase()`, because Data Prepper
> emits **delta-temporality** per-window values. Feeding cumulative counters into these queries produces inflated
> totals and wrong percentiles.

### 2.2 Required Labels

| Label                           | Scope          | Usage in APM `3.9.0` PromQL                                                                                          |
| ------------------------------- | -------------- | -------------------------------------------------------------------------------------------------------------------- |
| `namespace`                     | All series     | Hardcoded filter `namespace="span_derived"` on every APM query                                                       |
| `environment`                   | All series     | Derived from `deployment.environment.name` (e.g. `local`) or fallback `generic:default`                              |
| `service`                       | All series     | Service name (`resource.attributes["service.name"]`)                                                                 |
| `operation`                     | All series     | Span operation name (or parent `SERVER` operation for `CLIENT` edges)                                                |
| `remoteService`                 | All series     | **Empty (`remoteService=""`)** selects `SERVER` (node) metrics; non-empty selects `CLIENT` (edge/dependency) metrics |
| `remoteEnvironment`             | `CLIENT` spans | Downstream service environment (or `generic:default` for brokers)                                                    |
| `remoteOperation`               | `CLIENT` spans | Downstream operation name                                                                                            |
| `service_map_processor_host_id` | All series     | Emitted by Data Prepper to avoid multi-instance series collisions                                                    |
| `spanKind`                      | Messaging only | `"PRODUCER"` or `"CONSUMER"` on messaging dependency series                                                          |
| `<group_by_attributes>`         | Optional       | e.g. `telemetry_sdk_language` for Topology Map **Group By** queries                                                  |

## 3. OTel Collector Contrib `0.164.0` vs. Data Prepper

### 3.1 What OTel Collector Contrib `0.164.0` Can Do

1. **`opensearch` exporter (`mapping.mode: ss4o` or `otel-v1`)**
   ([`exporter/opensearchexporter/README.md`](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/README.md)
   and
   [`otelv1_model.go`](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/otelv1_model.go)):
   - `ss4o` mode (current Observe-It setup) already satisfies **Discover Traces** when paired with a manually created
     `traces` dataset.
   - `otel-v1` mode (available in `0.164.0`, with `manage_index_template: true`) writes spans directly to
     `otel-v1-apm-span` with integer `status.code`, `serviceName`, `durationInNanos`, and `resource.attributes.*` —
     enabling Discover Traces auto-detection without Data Prepper. However, because `opensearchexporter` is stateless,
     it does not statefully backfill `traceGroup` / `traceGroupFields` onto child spans across batches.
2. **`spanmetrics` connector**
   ([`connector/spanmetricsconnector/README.md`](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/connector/spanmetricsconnector/README.md)):
   - Emits `traces.span.metrics.calls` (`calls_total`) and `traces.span.metrics.duration` with labels `service.name`,
     `span.name`, `span.kind`, `status.code`.
   - Does **not** split HTTP `4xx` (`error`) vs. `5xx` (`fault`), does **not** set `namespace="span_derived"` or
     `remoteService=""`, and exports cumulative counters incompatible with APM's `sum_over_time(...)` queries.
3. **`service_graph` connector** (renamed from `servicegraph`)
   ([`connector/servicegraphconnector/README.md`](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/connector/servicegraphconnector/README.md)):
   - Emits `traces_service_graph_request_total`, `traces_service_graph_request_failed_total`,
     `traces_service_graph_request_server`, and `traces_service_graph_request_client` with labels `client`, `server`,
     and `connection_type`.
   - Emits **Prometheus metrics only** — it cannot write `NodeOperationDetail` topology documents to OpenSearch
     (`otel-v2-apm-service-map*`), nor do its metric names/labels match APM's PromQL contract.

### 3.2 Why Data Prepper Is Strictly Required for APM & Service Map

| Requirement for OSD `3.9.0` APM                                                                                            | OTel Collector Contrib `0.164.0` Only                                                                | OTel Collector + Data Prepper + Prometheus                                                       |
| -------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Discover Traces (span table, waterfall, log correlation)                                                                   | Yes (`ss4o` manual dataset, or `otel-v1` auto-detect)                                                | Yes (`otel-v1-apm-span*` auto-detect + stateful `traceGroup`)                                    |
| `otel-v2-apm-service-map*` topology documents                                                                              | **No** (no exporter/connector produces this schema)                                                  | **Yes** (`otel_apm_service_map` processor)                                                       |
| Synthesized DB/external dependency nodes (e.g. MongoDB)                                                                    | **No** (`service_graph` only emits Prometheus metrics)                                               | **Yes** (`dependency_nodes.enabled: true` synthesizes `database` nodes from `db.*` client spans) |
| APM RED metrics (`request`, `error`, `fault`, `latency_seconds_bucket` with `namespace="span_derived"`, delta temporality) | **No** (`spanmetrics` & `service_graph` emit incompatible names, labels, and cumulative temporality) | **Yes** (`otel_apm_service_map` → `prometheus` remote-write sink)                                |

### 3.3 Data Prepper Footprint & Operational Considerations

- **RAM footprint**: Data Prepper is a JVM service (`org.opensearch.dataprepper.DataPrepperExecute`, accepting
  `JAVA_OPTS` in
  [`data-prepper-jdk-arm64.sh`](https://github.com/opensearch-project/data-prepper/blob/main/release/archives/linux/data-prepper-jdk-arm64.sh))
  plus off-heap MapDB files (`db_path`) for the three sliding span windows used by `otel_apm_service_map`.
  - OpenSearch's official reference stack
    ([`opensearch-project/observability-stack/.env`](https://github.com/opensearch-project/observability-stack/blob/main/.env))
    allocates `DATA_PREPPER_MEMORY_LIMIT=1G` alongside `PROMETHEUS_MEMORY_LIMIT=500M`.
  - For Observe-It's low-volume local sandbox (currently ~3.3 GB total across 6 containers in
    [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml)), Data Prepper
    (`512m–1g`) plus Prometheus (`256m–512m`) adds **~0.75–1.5 GB RAM** and **2 containers** to the stack.
- **Cardinality gotcha**: In
  [`observability-stack/docker-compose/data-prepper/pipelines.template.yaml`](https://github.com/opensearch-project/observability-stack/blob/main/docker-compose/data-prepper/pipelines.template.yaml),
  the `service-metrics-cortex-pipeline` explicitly runs a `delete_entries` processor to strip `/attributes/randomKey`
  and `randomKey` before remote-writing to Prometheus/Cortex to prevent per-event UUID cardinality explosion.

## 4. Impact on Existing `ss4o_traces-*` Saved Objects

In
[`docker/opensearch/saved-objects.ndjson`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/saved-objects.ndjson),
**15 saved objects** depend on the `ss4o_traces` index pattern (`ss4o_traces-*`):

- **1 index pattern**: `ss4o_traces` (`ss4o_traces-*`, time field `@timestamp`)
- **5 saved searches**: `all-trace-spans`, `checkout-scenario-traces`, `domain-order-spans`, `error-and-timeout-spans`,
  `mongodb-client-spans`
- **8 visualizations**: `trace-spans-kpi`, `spans-by-service-and-kind`, `scenario-trace-duration`,
  `domain-span-latency-by-operation`, `http-server-span-latency-by-route`, `error-spans-by-operation`,
  `top-operations-latency-table`, `mongodb-span-latency-by-operation`
- **1 dashboard**: `observe-it-traces-overview` (`Observe-It Traces Overview`)

### Migration Impact by Topology Decision (Input for `#165`)

1. **Keep `ss4o_traces-*` (Discover Traces only, no Data Prepper / no APM)**:
   - **Zero impact** on all 15 existing saved objects.
   - Discover Traces works by creating a `traces`-signal dataset on `ss4o_traces-*` (either via bootstrap API or UI).
   - APM Services and Application Map remain unavailable.
2. **Switch trace ingestion to `otel-v1-apm-span-*` (with Data Prepper for full APM & Service Map)**:
   - **Breaking change for all 15 `ss4o_traces` saved objects** unless migrated or dual-indexed.
   - Migrating those saved objects to `otel-v1-apm-span-*` requires updating:
     - Index pattern title (`ss4o_traces-*` → `otel-v1-apm-span-*`)
     - `durationMs` → `durationInNanos` (or a runtime/scripted field / ingest pipeline enrichment)
     - `status.code: "STATUS_CODE_ERROR"` → `status.code: 2`
     - `service` → `serviceName`
   - Alternatively, legacy `Observe-It Traces Overview` can be retired or kept only if dual-writing (not recommended
     due to double storage/indexing overhead).

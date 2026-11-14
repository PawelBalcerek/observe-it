# OTel Collector SS4O traces export, OpenSearch schema, and Dashboards trace exploration

Research for wayfinder ticket `#117` ("OTel Collector ss4o trace export, OpenSearch `ss4o_traces-*` schema, and
Dashboards trace exploration"). Throwaway branch (`research/ss4o-traces-dashboards`), never merged. Facts below come
from primary sources at the pinned versions in [docker-compose.yml](../../docker-compose.yml)
(`otel/opentelemetry-collector-contrib:0.164.0`, `opensearchproject/opensearch:3.9.0`,
`opensearchproject/opensearch-dashboards:3.9.0`) unless marked **`[unverified]`** or **`[design suggestion]`**.

## 1. How the OTel Collector `opensearch` exporter (`v0.164.0`) writes traces in `ss4o` mode

In `exporter/opensearchexporter` at tag `v0.164.0`, **traces** (like logs) carry **alpha** stability (`metrics` is
`development`), per `metadata.yaml` and `README.md` in
<https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/v0.164.0/exporter/opensearchexporter>.

### 1.1 Index naming

In `newSSOTracesExporter` (`sso_trace_exporter.go`), when `mapping.mode` is `ss4o` (the default, explicitly set in
[docker/otel-collector/config.yaml](../../docker/otel-collector/config.yaml)):

- `defaultPrefix` is `"ss4o_traces"`, and `indexResolver.getDefaultIndexName()` joins `ss4o_traces`, `dataset`, and
  `namespace` with `-` (`index_resolver.go`).
- With Observe-It's current settings (`dataset: observe-it`, `namespace: local`), spans are indexed into
  **`ss4o_traces-observe-it-local`** unless overridden by `traces_index` or `traces_index_time_format`.
- Each span is sent as one bulk document using `bulk_action` (default `"create"`), optionally targeting `pipeline` if
  configured (`trace_bulk_indexer.go`, `config.go`).
- In `otel-v1` mode (`mapping.mode: otel-v1`), `defaultPrefix` becomes `"otel-v1-apm-span"` with empty dataset and
  namespace (`otel-v1-apm-span`). Setting non-default `dataset` or `namespace` (such as `observe-it` / `local`) alongside
  `mode: otel-v1` fails exporter validation in `Config.Validate()` (`errOTelV1DatasetNamespaceUnused` in `config.go`).

### 1.2 Exact SS4O span document (`ssoSpan`)

`encodeModel.encodeTrace` (`encoder.go`) populates `ssoSpan` (`sso_model.go`) as follows:

```json
{
  "attributes": {
    "http.request.method": "POST",
    "http.response.status_code": 201,
    "data_stream": {
      "dataset": "observe-it",
      "namespace": "local",
      "type": "span"
    }
  },
  "droppedAttributesCount": 0,
  "droppedEventsCount": 0,
  "droppedLinksCount": 0,
  "startTime": "2026-01-01T12:00:00.100000000Z",
  "endTime": "2026-01-01T12:00:00.145000000Z",
  "@timestamp": "0001-01-01T00:00:00Z",
  "events": [
    {
      "attributes": { "exception.type": "java.lang.IllegalStateException" },
      "droppedAttributesCount": 0,
      "name": "exception",
      "@timestamp": "2026-01-01T12:00:00.120000000Z"
    }
  ],
  "instrumentationScope": {
    "name": "io.opentelemetry.spring-webmvc-6.0",
    "version": "2.x",
    "schemaUrl": "https://opentelemetry.io/schemas/1.24.0",
    "droppedAttributesCount": 0,
    "attributes": {}
  },
  "kind": "Server",
  "links": [
    {
      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
      "spanId": "00f067aa0ba902b7",
      "traceState": "",
      "droppedAttributesCount": 0
    }
  ],
  "name": "POST /api/orders",
  "parentSpanId": "",
  "resource": {
    "deployment.environment.name": "local",
    "service.name": "observed-app",
    "service.version": "0.1.0"
  },
  "spanId": "a2fb4a1d1a96d312",
  "status": {
    "code": "Ok",
    "message": ""
  },
  "traceId": "5b8aa5a2d2c872e8321cf37308d69df2",
  "traceState": ""
}
```

Field-by-field serialization rules verified in `sso_model.go`, `encoder.go`, `trace_bulk_indexer.go`, and
`pdata/ptrace` (`span_kind.go`, `status_code.go` at `pdata/v1.70.0`):

| JSON field                       | Go source / value in `v0.164.0`                                           | Notes & serialization quirks                                                              |
| -------------------------------- | ------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `traceId`                        | `span.TraceID().String()`                                                 | 32-char lowercase hex string, always present                                              |
| `spanId`                         | `span.SpanID().String()`                                                  | 16-char lowercase hex string, always present                                              |
| `parentSpanId`                   | `span.ParentSpanID().String()`                                            | Always present (no `omitempty`); `""` on root spans                                       |
| `name`                           | `span.Name()`                                                             | Span operation name, always present                                                       |
| `kind`                           | `span.Kind().String()`                                                    | Emits `"Unspecified"`, `"Internal"`, `"Server"`, `"Client"`, `"Producer"`, `"Consumer"`   |
| `traceState`                     | `span.TraceState().AsRaw()`                                               | Always present (no `omitempty`); `""` when empty                                          |
| `startTime`, `endTime`           | `span.StartTimestamp().AsTime()`, `span.EndTimestamp().AsTime()`          | `time.Time` RFC 3339 nanosecond strings                                                   |
| `@timestamp`                     | `ssoSpan.Timestamp` (`time.Time`, no `omitempty`)                         | **Never assigned in `encodeTrace`**; serializes as `"0001-01-01T00:00:00Z"`               |
| `durationInNanos`                | **Not emitted**                                                           | Absent from `ssoSpan` (`encodeTraceOTelV1` emits it; `encodeTrace` does not)              |
| `serviceName`                    | **Not emitted**                                                           | Absent from `ssoSpan`; `service.name` stays inside `resource`                             |
| `traceGroup`, `traceGroupFields` | **Not emitted**                                                           | Absent from `ssoSpan` (emitted only in `encodeTraceOTelV1` on root spans)                 |
| `status.code`                    | `span.Status().Code().String()`                                           | **String** `"Unset"`, `"Ok"`, `"Error"` (or `""`), not integer `0` / `1` / `2`            |
| `status.message`                 | `span.Status().Message()`                                                 | Always present under `status`; `""` when unset                                            |
| `attributes`                     | `span.Attributes().AsRaw()` (`omitempty`)                                 | Typed JSON values; dotted keys stay literal dotted JSON keys                              |
| `attributes.data_stream`         | `{dataset, namespace, type: "span"}`                                      | Injected by `encodeTrace` whenever `dataset` or `namespace` is non-empty                  |
| `resource`                       | `attributesToMapString(resource.Attributes())` (`omitempty`)              | Flattened `map[string]string` (`v.AsString()` on every resource attribute)                |
| `instrumentationScope`           | `name`, `version`, `schemaUrl`, `droppedAttributesCount`, `attributes`    | `schemaUrl` comes from `ScopeSpans.SchemaUrl()`, tag `omitzero`; no top-level `schemaUrl` |
| `events[]`                       | `name`, `attributes` (no `omitempty`), `droppedAttributesCount`, time     | Emits `@timestamp` when `ts.Unix() != 0`, otherwise `observedTimestamp: now`              |
| `links[]`                        | `traceId`, `spanId`, `traceState`, `attributes`, `droppedAttributesCount` | Omitted when span has no links; individual subfields use `omitempty`                      |
| `dropped*Counts`                 | `droppedAttributesCount`, `droppedEventsCount`, `droppedLinksCount`       | `uint32`, always serialized (`0` when none)                                               |

### 1.3 Key consequences and bugs for Observe-It

1. **`@timestamp` is broken out of the box in `v0.164.0` `ss4o` mode.** Unlike `encodeLogSSO` (`sso.Timestamp = &ts`),
   `encodeTrace` leaves `sso.Timestamp` as Go's zero `time.Time{}` (`"0001-01-01T00:00:00Z"`). Any time-bounded search or
   dashboard panel using `@timestamp` will miss every span unless an ingest pipeline copies `startTime` to `@timestamp`
   or dashboards query `startTime`.
2. **Span duration is not precomputed.** Queries or visualizations wanting latency (`durationInNanos` or `durationMs`)
   must derive it at ingest time from `startTime` and `endTime` (or via runtime scripts).
3. **`status.code` is a string (`"Unset"` / `"Ok"` / `"Error"`), whereas `kind` is PascalCase (`"Server"`).** Both differ
   from `opensearch-catalog` `traces-1.0.1.schema` (`"SPAN_KIND_SERVER"`) and Data Prepper (`status.code` integer `0`/`1`/`2`).
4. **Unlike `ss4o_metrics`, there is no top-level `serviceName`, `service`, `version`, or `env`.** Those attributes exist
   only as string values under `resource["service.name"]`, `resource["service.version"]`, and
   `resource["deployment.environment.name"]`.

## 2. Index template and ingest normalization requirements

### 2.1 The Collector does not install a template for `ss4o_traces-*`

- Setting `mapping.manage_index_template: true` is **invalid in `ss4o` mode**: `Config.Validate()` (`config.go`) rejects
  it with `errManageIndexTemplateInvalidMode` (`"mapping.manage_index_template is only supported with mapping.mode \"otel-v1\""`).
- `index_template_manager.go` embeds only two templates: `otel-v1-apm-span-index-template` and
  `otel-v1-logs-index-template`. Neither covers `ss4o_traces-*`.
- Therefore, **`opensearch-bootstrap` must install an explicit `_index_template` (and ingest pipeline)** before
  `otel-collector` sends spans — matching Observe-It's existing setup in
  [docker/opensearch/bootstrap.sh](../../docker/opensearch/bootstrap.sh) and
  [docker-compose.yml](../../docker-compose.yml) (`otel-collector` depends on `opensearch-bootstrap` completing).

### 2.2 Gaps in the official `opensearch-catalog` `traces-1.0.1.mapping`

The official OpenSearch Observability Catalog template
`schema/observability/traces/1.0.1/traces-1.0.1.mapping`
(<https://github.com/opensearch-project/opensearch-catalog/blob/main/schema/observability/traces/1.0.1/traces-1.0.1.mapping>)
targets `"ss4o_traces-*-*"` (matching `ss4o_traces-observe-it-local`), but **using it unmodified causes ingestion
failures and query gaps with Collector `v0.164.0`**:

| Aspect                         | `traces-1.0.1.mapping` behavior                      | Impact with Collector `v0.164.0` `ssoSpan`                                                       |
| ------------------------------ | ---------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `status.code`                  | Mapped as `long`                                     | **Fatal mismatch**: Collector emits `"Ok"` / `"Error"` / `"Unset"`; OpenSearch rejects documents |
| `@timestamp`                   | **Not mapped**                                       | Dynamic mapping guesses `date` (ms, not `date_nanos`); Collector writes `"0001-01-01T00:00:00Z"` |
| `durationInNanos` / `duration` | **Not mapped**                                       | Absent unless added by an ingest pipeline                                                        |
| `attributes.*`, `resource.*`   | **No dynamic template**                              | String attributes become `text` + `.keyword`; numeric attributes risk type clashes               |
| `service`, `version`, `env`    | Only `serviceName` (`keyword`) mapped; not populated | Collector populates none of them; Observe-It log/metric queries use `service`, `version`, `env`  |
| `events`, `links`              | Mapped as `"type": "nested"`                         | Discover / standard Dashboards aggregations do not easily inspect `nested` fields                |
| `attributes.data_stream`       | Not stripped                                         | Leaves synthetic `data_stream.{dataset,namespace,type}` noise on every span                      |

### 2.3 Recommended Observe-It template (`observe-it-ss4o-traces`) and pipeline (`observe-it-traces-pipeline`)

Following [docker/opensearch/index-template.json](../../docker/opensearch/index-template.json) and
[docker/opensearch/metrics-index-template.json](../../docker/opensearch/metrics-index-template.json), `opensearch-bootstrap`
should install:

1. **Composable index template `observe-it-ss4o-traces` (`_index_template/observe-it-ss4o-traces`)** **`[design suggestion]`**:
   - `index_patterns`: `["ss4o_traces-*"]`, `priority`: `200`, `number_of_shards`: `1`, `number_of_replicas`: `0`,
     `default_pipeline`: `"observe-it-traces-pipeline"` (requires no change to `docker/otel-collector/config.yaml`).
   - `dynamic_templates`: `strings_as_keywords` (`match_mapping_type: "string"`, `type: "keyword"`, `ignore_above: 1024`),
     matching `ss4o_logs-*` and `ss4o_metrics-*`.
   - Top-level `date_nanos` fields: `@timestamp`, `startTime`, `endTime`, `events.@timestamp`, `events.observedTimestamp`.
   - Top-level `keyword` fields: `traceId`, `spanId`, `parentSpanId`, `name`, `kind`, `traceState`, `service`,
     `serviceName`, `version`, `env`, `status.code`, `status.message`, `instrumentationScope.name`,
     `instrumentationScope.version`, `instrumentationScope.schemaUrl`, `links.traceId`, `links.spanId`, `links.traceState`,
     `events.name`.
   - Numeric fields: `durationInNanos`, `droppedAttributesCount`, `droppedEventsCount`, `droppedLinksCount`,
     `events.droppedAttributesCount`, `links.droppedAttributesCount`, `instrumentationScope.droppedAttributesCount`
     (`long`); `durationMs` (`double`).
   - Explicit `attributes.*` mappings for numeric and domain attributes so types never drift across services
     (for example `attributes.http.response.status_code`: `long`, `attributes.server.port`: `long`,
     `attributes.order.item_count`: `long`, `attributes.order.total_amount`: `double`, plus `keyword` fields for
     `attributes.checkout.scenario`, `attributes.order.id`, `attributes.order.state`). Exact Java Agent span attributes
     emitted by the Observed App (`observed-app`) remain **`[unverified]`** until live capture.
   - **Design trade-off (`events` / `links`)**: Decide in the prototype whether `events` and `links` should use `"type":
     "object"` (flatter queries and table visibility in Discover) instead of `"type": "nested"` in `traces-1.0.1.mapping`.

2. **Ingest pipeline `observe-it-traces-pipeline` (`_ingest/pipeline/observe-it-traces-pipeline`)** **`[design suggestion]`**:
   - Copy `startTime` to `@timestamp` (`ctx['@timestamp'] = ctx.startTime`) when `ctx.startTime != null` to repair the
     `v0.164.0` `"0001-01-01T00:00:00Z"` bug and unify the global timepicker across logs, metrics, and traces.
   - Compute `durationInNanos` (`long`) and `durationMs` (`double`) from `startTime` and `endTime` using Painless
     timestamp parsing (**`[unverified]`** in live OpenSearch `3.9.0` Painless sandbox — confirm allowed `Instant` /
     `ZonedDateTime` methods for nanosecond-precision ISO strings).
   - Promote `resource['service.name']` -> `service` (and `serviceName`), `resource['service.version']` -> `version`,
     and `resource['deployment.environment.name']` -> `env`, matching
     [docker/opensearch/metrics-ingest-pipeline.json](../../docker/opensearch/metrics-ingest-pipeline.json).
   - Remove `attributes.data_stream` (`ctx.attributes.remove('data_stream')`).
   - Keep `status.code` as `"Unset"` / `"Ok"` / `"Error"` (or also derive an integer field only if needed).

## 3. OpenSearch Dashboards `3.9.0`: Trace Analytics vs. Explore vs. Saved Objects

### 3.1 Observability Trace Analytics (`dashboards-observability` `3.9.0.0`)

Primary-source inspection of `opensearch-project/dashboards-observability` at tag `3.9.0.0` shows that **the Trace
Analytics plugin cannot display raw or minimally normalized `ss4o_traces-*` indices out of the box**:

1. **Only two modes exist: `jaeger` and `data_prepper`.**
   - `isValidTraceAnalyticsMode` (`public/components/trace_analytics/home.tsx`) and `modes` accept only `'jaeger'` and
     `'data_prepper'`. There is no `ss4o` mode.
2. **Default index settings target Data Prepper indices.**
   - `common/constants/trace_analytics.ts` and `server/plugin_helper/register_settings.ts` configure:
     - `observability:traceAnalyticsSpanIndices` = `'otel-v1-apm-span-*,opensearch_dashboards_sample_data_otel_spans*'`
       (description: *"Configure span indices that adhere to Data Prepper schema, to be used by the trace analytics plugin"*).
     - `observability:traceAnalyticsServiceIndices` = `'otel-v1-apm-service-map*,opensearch_dashboards_sample_data_otel_service_map*'`.
     - `observability:traceAnalyticsCorrelatedLogsIndices` = `'ss4o_logs-*,opensearch_dashboards_sample_data_otel_logs*'`,
       with `DEFAULT_CORRELATED_LOGS_FIELD_MAPPINGS` expecting `"time"` and `"serviceName"` (Observe-It's `ss4o_logs-*`
       uses `@timestamp` and `service`, though this setting can be overridden).
3. **Hard schema and service-map dependencies in `data_prepper` mode:**
   - **Service-map index requirement**: `getServiceMapQuery` (`services_queries.ts`) queries
     `observability:traceAnalyticsServiceIndices` for `serviceName`, `target.{resource,serviceName,domain}`, and
     `destination.{resource,serviceName,domain}`. In `services_request_handler.ts` (`handleServicesRequest`), if
     `serviceObject` returned by `handleServiceMapRequest` is empty, **`setItems([])` clears the Services table**.
     `TracesContent` (`traces_content.tsx`) also invokes `handleServiceMapRequest`, surfacing a `'Failed to retrieve
     service nodes'` error notification when service-map indices are missing (**`[unverified]`** exact UI behavior in
     live 3.9.0).
   - **Root-span `traceGroup` and `traceGroupFields` requirement**: `dashboard_queries.ts` (`getDashboardQuery`,
     `getLatencyTrendQuery`) and `traces_queries.ts` (`getTracesQuery`) group traces by `terms: { field: 'traceGroup' }`
     and run Painless scripts reading `traceGroupFields.durationInNanos`, `traceGroupFields.endTime`, and filtering on
     `'traceGroupFields.statusCode': '2'`. None of these exist in Collector `ss4o` spans.
   - **Span schema differences**:
     - `traces_queries.ts` (`getCustomIndicesTracesQuery`) expects `durationInNanos`, numeric `status.code`, `traceGroup`,
       and filters `entry_spans` by `kind: 'SPAN_KIND_SERVER'` (whereas Collector `v0.164.0` emits `"Server"`).
     - `traces_request_handler.ts` (`hitsToSpanDetailData`) reads `hit._source.durationInNanos`, `hit._source.serviceName`,
       and checks `hit._source.status?.code === 2 || hit._source['status.code'] === 2` for errors; `handlePayloadRequest`
       parses `hit._source.startTime` with `parseIsoToNano`.
     - `traces_custom_indices_table.tsx` renders `status.code === 2` as `"Yes"` error and divides `durationInNanos` by
       `1000000`.
     - `trace_view_helpers.tsx` (`getOverviewFields`) prefers `traceGroupFields.{durationInNanos,endTime,statusCode}`
       (falling back to min `startTime` / max `endTime` across spans) and checks numeric status `2`.
4. **Upstream confirmation**: `open-telemetry/opentelemetry-collector-contrib#38680`
   (<https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/38680>) confirms that Trace Analytics
   expects Data Prepper's span + service-map schemas rather than Collector `ss4o_traces-*`.
5. **What about `mapping.mode: otel-v1` in the Collector?** Even switching traces to `otel-v1` (`encodeTraceOTelV1` in
   `encoder.go`) emits `durationInNanos`, `serviceName`, numeric `status.code`, `traceGroup`, and `traceGroupFields`,
   **but** still emits `kind: "Server"` (from `pdata.SpanKind.String()`), requires a separate exporter without
   `dataset`/`namespace`, diverges from `ss4o_logs-*` / `ss4o_metrics-*`, and **still produces no `otel-v1-apm-service-map*`
   documents** (which Data Prepper generates). That makes full Trace Analytics (Dashboard, Services, Service Map)
   non-functional without Data Prepper (explicitly out of scope in map `#115`) or synthetic service-map generation.

### 3.2 Experimental `Explore > Discover Traces` and `APM` (`OpenSearch-Dashboards` `3.9.0`)

`OpenSearch-Dashboards` `3.9.0` ships `Explore` (`src/plugins/explore/public/application/pages/traces/README.md`), and
`dashboards-observability` `3.9.0.0` registers `APM` (`Services`, `Topology Map`, enabled when `Discover Traces` is on):

- **Disabled by default**: In `OpenSearch-Dashboards` `3.9.0` `config/opensearch_dashboards.yml`, lines 431–443 leave
  `data_source.enabled: true`, `workspace.enabled: true`, `explore.enabled: true`, and
  `explore.discoverTraces.enabled: true` **commented out**.
- **Bootstrap complexity**: Enabling `Discover Traces` requires Workspaces (`workspace.enabled: true`) and Data Sources
  (`data_source.enabled: true`), registering a `data-source` saved object, and POSTing workspace-scoped index patterns
  to `/w/<workspace_id>/api/saved_objects/index-pattern/<id>` with `"signalType": "traces"` and a `dataSource` reference
  — instead of Observe-It's simple non-workspace `POST /api/saved_objects/_import` flow. Whether `Discover Traces` even
  recognizes Collector `ss4o_traces-*` field names without Data Prepper `otel-v1` schema is **`[unverified]`**.

### 3.3 Standard Saved Objects (`saved-objects.ndjson`) — recommended path

Observe-It already bootstraps `ss4o_logs` and `ss4o_metrics` via `POST /api/saved_objects/_import?overwrite=true`
([docker/opensearch/saved-objects.ndjson](../../docker/opensearch/saved-objects.ndjson)). Extending that exact mechanism
to the SS4O Traces Index (`ss4o_traces-*`) works without Data Prepper or experimental workspace flags **`[schema fields
verified; exact Dashboards 3.9.0 visualization/Rison JSON unverified]`**:

- **Index pattern (`ss4o_traces`)**: `title: "ss4o_traces-*"`, `timeFieldName: "@timestamp"` (matching the SS4O Log
  Index and SS4O Metrics Index once `observe-it-traces-pipeline` sets `@timestamp = startTime`).
- **Discover saved searches (`type: "search"`)**:
  - `All Trace Spans` (columns: `service`, `name`, `kind`, `status.code`, `durationMs`, `traceId`, `spanId`,
    `parentSpanId`, `attributes.checkout.scenario`, `attributes.order.id` — covering `Checkout Scenario` and `Order` ID)
  - `Root Spans — Requests` (`parentSpanId: ""`)
  - `Error & Slow Spans` (`status.code: "Error"` or high `durationMs`)
- **Visualizations (`type: "visualization"`) & `Observe-It Traces Overview` dashboard**:
  - Span / trace throughput (`histogram` / `line` over `@timestamp` split by `service` or `status.code`)
  - Latency metrics & distribution (`metric` / `line` / `area` using `avg`, `max`, or percentiles of `durationMs`, split
    by `name` or `attributes.checkout.scenario`)
  - Error count & error breakdown (`metric` / `pie` / `histogram` filtered by `status.code: "Error"`)
  - Top slow operations table (`table` grouped by `service` + `name` showing count, avg/p95 `durationMs`, error count)
- **Cross-signal correlation (`logs <-> traces <-> metrics`)**:
  - Filtering by `traceId`, `service`, `attributes.checkout.scenario`, or `attributes.order.id` works consistently across
    `ss4o_traces-*`, `ss4o_logs-*`, and `ss4o_metrics-*`.
  - Adding a `fieldFormatMap` URL template on `ss4o_logs` (`traceId` -> Discover `ss4o_traces` search URL filtered by
    `traceId`) could provide one-click log-to-trace jump links (**`[unverified]`** exact 3.9.0 Rison URL and
    `fieldFormatMap` format should be exported from live Dashboards 3.9.0).

## 4. Decision summary and `wayfinder:prototype` recommendation

| Option                                                | Works without Data Prepper? | Matches `ss4o_*` Observe-It conventions? | Verdict                                                              |
| ----------------------------------------------------- | --------------------------- | ---------------------------------------- | -------------------------------------------------------------------- |
| Collector `ss4o` + template/pipeline + Saved Objects  | Yes                         | Yes (`service`, `env`, `@timestamp`)     | **Recommended**: simplest, aligns with logs/metrics & `#115` scope   |
| Collector `ss4o` pointed at Trace Analytics plugin    | **No**                      | N/A                                      | Broken (`traceGroup*`, numeric `status.code`, `service-map` missing) |
| Collector `otel-v1` pointed at Trace Analytics plugin | **Partial / No**            | No (`otel-v1-apm-span`, no `service`)    | No `otel-v1-apm-service-map*`; `Services` & main views break         |
| Experimental `workspace` + `explore.discoverTraces`   | **`[unverified]`**          | Needs workspace + data-source bootstrap  | Heavy config change; out of alignment with current `saved-objects`   |

### Prototype recommendation (`wayfinder:prototype`)

**Yes — recommend a short `wayfinder:prototype` ticket before `#119` grilling / implementation**, scoped strictly to
verifying live container behavior against `opensearch:3.9.0`, `opensearch-dashboards:3.9.0`, and
`otel/opentelemetry-collector-contrib:0.164.0`:

1. Capture real OTel Java Agent + `@WithSpan` span JSON emitted by `v0.164.0` (`ss4o` mode) to verify exact
   `attributes.*` keys and numeric types (`http.response.status_code`, DB/messaging/manual attributes).
2. Test `observe-it-traces-pipeline` Painless logic in OpenSearch `3.9.0` to confirm `@timestamp = startTime` and
   nanosecond/millisecond `durationInNanos` + `durationMs` calculation work inside the Painless sandbox without errors.
3. Validate `events` mapping (`object` vs. `nested`) and export tested `Observe-It Traces Overview` + Discover saved
   search / visualization NDJSON (plus optional `traceId` URL formatter) from live Dashboards `3.9.0`.
4. Confirm experimentally whether Trace Analytics `TracesCustomIndicesTable` has any fallback value or fails outright
   without `otel-v1-apm-service-map*` as predicted from source.

## 5. Primary sources

- Local repo configuration: [docker-compose.yml](../../docker-compose.yml),
  [docker/otel-collector/config.yaml](../../docker/otel-collector/config.yaml),
  [docker/opensearch/bootstrap.sh](../../docker/opensearch/bootstrap.sh),
  [docker/opensearch/index-template.json](../../docker/opensearch/index-template.json),
  [docker/opensearch/ingest-pipeline.json](../../docker/opensearch/ingest-pipeline.json),
  [docker/opensearch/metrics-index-template.json](../../docker/opensearch/metrics-index-template.json),
  [docker/opensearch/metrics-ingest-pipeline.json](../../docker/opensearch/metrics-ingest-pipeline.json),
  [docker/opensearch/saved-objects.ndjson](../../docker/opensearch/saved-objects.ndjson)
- OTel Collector Contrib `v0.164.0` `opensearchexporter`:
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/README.md>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/metadata.yaml>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/config.go>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/sso_trace_exporter.go>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/sso_model.go>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/otelv1_model.go>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/encoder.go>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/index_resolver.go>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/index_template_manager.go>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/opensearchexporter/trace_bulk_indexer.go>
  - <https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/38680>
- OpenTelemetry Collector `pdata/v1.70.0`:
  - <https://github.com/open-telemetry/opentelemetry-collector/blob/pdata/v1.70.0/pdata/ptrace/span_kind.go>
  - <https://github.com/open-telemetry/opentelemetry-collector/blob/pdata/v1.70.0/pdata/ptrace/status_code.go>
- OpenSearch Catalog SS4O traces schema:
  - <https://github.com/opensearch-project/opensearch-catalog/blob/main/schema/observability/traces/1.0.1/traces-1.0.1.mapping>
  - <https://github.com/opensearch-project/opensearch-catalog/blob/main/schema/observability/traces/1.0.1/traces-1.0.1.schema>
  - <https://github.com/opensearch-project/opensearch-catalog/tree/main/docs/schema/observability/traces>
- OpenSearch Dashboards `3.9.0` & `dashboards-observability` `3.9.0.0`:
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/common/constants/trace_analytics.ts>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/common/constants/apm.ts>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/server/plugin_helper/register_settings.ts>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/home.tsx>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/components/traces/traces_content.tsx>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/components/traces/traces_custom_indices_table.tsx>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/components/traces/trace_view_helpers.tsx>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/requests/traces_request_handler.ts>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/requests/services_request_handler.ts>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/requests/queries/traces_queries.ts>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/requests/queries/dashboard_queries.ts>
  - <https://github.com/opensearch-project/dashboards-observability/blob/3.9.0.0/public/components/trace_analytics/requests/queries/services_queries.ts>
  - <https://github.com/opensearch-project/OpenSearch-Dashboards/blob/3.9.0/config/opensearch_dashboards.yml>
  - <https://github.com/opensearch-project/OpenSearch-Dashboards/blob/3.9.0/src/plugins/explore/public/application/pages/traces/README.md>

# OTel Collector SS4O metrics export, OpenSearch schema, Dashboards import

Research for wayfinder ticket "OTel Collector ss4o metrics export, OpenSearch ss4o_metrics-* schema, and Dashboards
visualizations". Throwaway branch, never merged. Facts below were read from primary sources at the pinned versions
unless tagged **[unverified]**.

## 1. What the `opensearch` exporter writes in `ss4o` mode

- Metrics are supported in `ss4o` mode at **development** stability (logs and traces are alpha).
  Source: `exporter/opensearchexporter/metadata.yaml` and README at `v0.164.0` in
  <https://github.com/open-telemetry/opentelemetry-collector-contrib>.
- README: each metric data point is exported as **one document**. Gauge, sum, histogram, exponential histogram and
  summary are all supported.
- Index name: `ss4o_metrics-<dataset>-<namespace>` (`defaultPrefix := "ss4o_metrics"` in `sso_metric_exporter.go`), so
  `dataset: observe-it` + `namespace: local` gives `ss4o_metrics-observe-it-local`.
- The exporter does **not** install the SS4O metrics index template in `ss4o` mode. `manage_index_template` is only
  valid for `otel-v1` mode. The README says to install the template before indexing, otherwise dynamic mapping can
  reject later documents.
- Document fields (`metrics_model.go`, `metrics_encoder.go`):

| Field | Notes |
| --- | --- |
| `name`, `description`, `unit` | Metric metadata |
| `kind` | `GAUGE`, `SUM`, `HISTOGRAM`, `EXPONENTIAL_HISTOGRAM`, `SUMMARY` |
| `aggregationTemporality`, `monotonic` | Sum only. Values `AGGREGATION_TEMPORALITY_DELTA` / `_CUMULATIVE` |
| `startTime`, `@timestamp` | Data point start and time |
| `observedTimestamp` | Exporter wall clock at encode time |
| `serviceName` | Copied from resource attribute `service.name` |
| `value@int` / `value@double` | Gauge and sum values, typed by the data point |
| `count`, `sum`, `min`, `max` | Histogram aggregates (pointers, omitted when absent) |
| `buckets[]` | Explicit histogram: `{count, min, max}` per bucket, outer buckets capped at ±MaxFloat32 |
| `bucketCountsList`, `explicitBoundsList`, `bucketCount`, `explicitBoundsCount` | Raw histogram arrays and counts |
| `positiveBuckets[]`, `negativeBuckets[]`, `positiveOffset`, `negativeOffset`, `zeroCount`, `scale` | Exponential histogram |
| `quantiles[]`, `quantileValuesCount` | Summary |
| `attributes` | **Data point attributes** as a map (`dp.Attributes().AsRaw()`), plus `attributes.data_stream` |
| `resource` | **Resource attributes** as a flat `map[string]string` |
| `instrumentationScope.{name,version,schemaUrl,attributes}` | Scope |
| `schemaUrl`, `exemplars[]` | Optional |

- Consequences for Observe-It:
  - `deployment.environment.name`, `service.version` and similar live under `resource.*` as **string values**, while
    `service.name` is also hoisted to top-level `serviceName`.
  - Data point attributes (`checkout.scenario`, `order.state`, `http.route`, `http.response.status_code`) live under
    `attributes.*`. Dotted keys are written as literal dotted JSON keys; OpenSearch expands them to object paths when
    mapping.
  - `http.response.status_code` is an integer attribute, so under dynamic mapping it becomes `long`, while the other
    attributes become `text` with a `.keyword` subfield.
  - Gauges and counters carry no `sum`/`count`. Histograms carry no `value@*`.

## 2. Index template

- Official SS4O metrics mapping: `schema/observability/metrics/1.0.1/metrics-1.0.1.mapping` in
  <https://github.com/opensearch-project/opensearch-catalog>. A data stream variant
  (`metrics-datastream-1.0.1.mapping`) also exists.
- Index pattern in the template: `ss4o_metrics-*-*`, which matches `ss4o_metrics-observe-it-local`.
- Mapped explicitly: `serviceName`, `name`, `unit`, `kind`, `aggregationTemporality` as `keyword`; `monotonic`
  `boolean`; `@timestamp` / `startTime` `date`; `observedTimestamp` `date_nanos`; `value@int` `integer`;
  `value@double` `double`; `buckets`, `positiveBuckets`, `negativeBuckets` `nested`; `count`, `bucketCountsList`
  `long`; `sum`, `min`, `max` `float`; `explicitBoundsList` `float`.
- **Not mapped:** `attributes.*` and `resource.*`. There is only a dynamic template for `exemplar.attributes.*` and
  `instrumentationScope.attributes.*` (keyword). Without additions, attribute strings become `text` + `.keyword`.
  Recommended addition (design choice, not from the catalog): dynamic templates mapping `attributes.*` and
  `resource.*` strings to `keyword`. Keep `http.response.status_code` consistently one type, because a type clash
  rejects documents.
- Known issue from the exporter README: the catalog maps `value@int` as 32-bit `integer`, but OTel ints are 64-bit,
  so change it to `long` in our copy. The README also notes Data Prepper's reference template already uses `long`.
- `sum` is mapped as `float` (32-bit) in the catalog template. Large histogram sums lose precision. Consider `double`
  in our copy **[design suggestion]**.
- An ingest pipeline (`pipeline` exporter option exists) is not required to get the document shape above. It would
  only be needed to reshape documents.

## 3. Dashboards 3.9.0 saved-object import

Source: OpenSearch documentation "Saved Objects APIs"
(<https://github.com/opensearch-project/documentation-website> `_dashboards/management/saved-objects-api.md`).

- Endpoint is served by Dashboards (port 5601), not OpenSearch: `POST /api/saved_objects/_import`.
- Required header `osd-xsrf: true` (not `kbn-xsrf`). Body is multipart form field `file`.
- File must end in `.ndjson`. Default limits: 25 MB, 10000 objects.
- `overwrite=true` and `createNewCopies=true` are mutually exclusive. For deterministic IDs on a repeatable
  bootstrap, use fixed IDs with `overwrite=true`.
- Import into the same or newer Dashboards version than the export.
- With the Security plugin, add auth and the `securitytenant` header if multi-tenancy is on.
- Object shapes (index-pattern with `title: ss4o_metrics-*` and `timeFieldName: @timestamp`, `visualization` with
  `visState` JSON string and an index-pattern reference, `dashboard` with `panelsJSON` and visualization references)
  are standard saved-object JSON **[unverified against 3.9.0]**. The cleanest way to get exact shapes is to build the
  objects in a running 3.9.0 Dashboards and export them with `_export`.
- Whether TSVB, VisBuilder or standard visualizations best suit histogram buckets is **not researched**. It needs a
  hands-on prototype.

## Open items for the map

1. Template ownership: who installs the template (compose init script, like for the log index?) and the `long` /
   `double` / keyword-attributes tweaks.
2. Visualization type choice and exact NDJSON, via prototype against a running Dashboards 3.9.0.
3. Histogram handling: explicit buckets are expanded to `buckets[]` (nested), which Dashboards visualizations cannot
   aggregate easily. `sum`/`count` give averages, while percentiles need the bucket arrays.
4. `otel-v1` mode as an alternative: it has an auto-installed template and a `value` double field, but uses
   `otel-v1-metrics` index naming, which conflicts with the `ss4o_metrics-observe-it-local` destination.

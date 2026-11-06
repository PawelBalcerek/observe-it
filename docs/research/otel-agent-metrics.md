# OTel Java Agent metrics: JVM, HTTP, Micrometer bridge, histograms

Research for wayfinder ticket "OTel Java Agent JVM, HTTP, and custom metrics instrumentation". Throwaway branch, never
merged. Facts were read from primary sources on their `main` branches (fetched 2026-11-06), so version drift against
the agent bundled in Paketo `opentelemetry:2.31.0` is possible. The agent version in that buildpack was **not**
checked. Items tagged **[unverified]** were not read from a source.

## 1. Out-of-the-box JVM and HTTP metrics

### JVM (runtime-telemetry)

Source: `instrumentation/runtime-telemetry/library/README.md` in
<https://github.com/open-telemetry/opentelemetry-java-instrumentation> and the semantic conventions page
`docs/runtime/jvm-metrics.md` in <https://github.com/open-telemetry/semantic-conventions>.

Enabled by default ("Stable Metrics (enabled by default)"):

| Metric | Instrument | Unit |
| --- | --- | --- |
| `jvm.memory.used`, `jvm.memory.committed`, `jvm.memory.limit`, `jvm.memory.used_after_last_gc` | UpDownCounter | `By` |
| `jvm.gc.duration` | Histogram | `s` |
| `jvm.thread.count` | UpDownCounter | `{thread}` |
| `jvm.class.loaded`, `jvm.class.unloaded` | Counter | `{class}` |
| `jvm.class.count` | UpDownCounter | `{class}` |
| `jvm.cpu.time` | Counter | `s` |
| `jvm.cpu.recent_utilization` | Gauge | `1` |

- `jvm.cpu.count` is stable in the semantic conventions but is **not** in the agent's default-enabled list. It only
  appears in the JFR-capable list. There is no `jvm.threads.*` or `jvm.gc.pause`: the names are `jvm.thread.count` and
  `jvm.gc.duration`.
- Attributes depend on the GC and memory pools: memory metrics carry `jvm.memory.pool.name` and `jvm.memory.type`
  (`heap` / `non_heap`). `jvm.gc.duration` carries `jvm.gc.name` and `jvm.gc.action`, and for JMX-sourced points also
  `jvm.gc.cause`. `jvm.thread.count` carries `jvm.thread.daemon` and `jvm.thread.state`
  (README says the full attribute set is GC dependent).
- Experimental metrics (`jvm.buffer.*`, `jvm.file_descriptor.*`, `jvm.memory.init`, `jvm.system.cpu.utilization`) need
  `otel.instrumentation.runtime-telemetry.emit-experimental-metrics=true`.

### HTTP

Source: `docs/http/http-metrics.md` in semantic-conventions.

| Metric | Instrument | Unit | Attributes |
| --- | --- | --- | --- |
| `http.server.request.duration` | Histogram | `s` | `http.request.method`, `url.scheme` (required); `http.response.status_code`, `http.route`, `error.type`, `network.protocol.name` (conditional); `network.protocol.version` (recommended); `server.address`, `server.port` (opt-in) |
| `http.client.request.duration` | Histogram | `s` | `http.request.method`, `server.address`, `server.port` (required); `http.response.status_code`, `error.type`, `network.protocol.name`; `network.protocol.version` (recommended) |

- Both are stable. The spec says they SHOULD carry the explicit bucket advice
  `[0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10]`.
- That the agent's Spring MVC / Tomcat server instrumentation and the HTTP client instrumentation of the libraries we
  use emit exactly these, with `http.route` populated, is **[unverified]**: it needs a smoke test against the real app.

## 2. Micrometer bridge vs `opentelemetry-api`

Sources: `docs/supported-libraries.md`, `instrumentation/micrometer/micrometer-1.5/library/README.md` and
`.../javaagent/README.md` in the instrumentation repo; `DoubleHistogramBuilder.java` in
<https://github.com/open-telemetry/opentelemetry-java>.

**Micrometer bridge**

- Disabled by default. Enable with `otel.instrumentation.micrometer.enabled=true`
  (`OTEL_INSTRUMENTATION_MICROMETER_ENABLED`). The docs warn it "might create high number of metrics".
- Meter names pass through unchanged, tags become attributes, scope is `io.opentelemetry.micrometer-1.5`.
- Mapping: `Counter` is a synchronous double counter; `Gauge` an asynchronous double gauge; `Timer` and
  `DistributionSummary` a synchronous double histogram; `LongTaskTimer` is two up-down counters `<name>.active` and
  `<name>.duration`.
- Units: base units are used verbatim (not UCUM). `Timer` base time unit defaults to seconds
  (`otel.instrumentation.micrometer.base-time-unit`, default `s`).
- Histograms: only Micrometer **service level objectives** become explicit bucket boundaries advice.
  `publishPercentileHistogram()`, `minimumExpectedValue` and `maximumExpectedValue` do not.
- Other agent settings: `otel.instrumentation.micrometer.prometheus-mode.enabled` (default `false`, renames
  instruments like Prometheus) and `otel.instrumentation.micrometer.experimental.histogram-gauges.enabled`
  (default `false`).
- Reading values back (`Counter#count()` and friends) is not supported through the bridge, so meant to run alongside
  another registry. This matters for tests that assert on Micrometer meters.

**`opentelemetry-api` in the application**

- `DoubleHistogramBuilder#setExplicitBucketBoundariesAdvice(List<Double>)` gives per-instrument bucket control
  (`@since 1.32.0`). Counters and gauges map to `LongCounter` / observable gauges.
- No-op without the agent or SDK **[from API design, not checked in a source here]**; the version of `opentelemetry-api`
  we compile against must not be newer than what the agent bundles **[unverified]**.

Trade-off summary: the bridge keeps Micrometer APIs and `@Timed`/Observation working with no code change but pulls in
all other Micrometer meters and its names are Micrometer style. `opentelemetry-api` gives native names, units, attributes
and bucket advice, at the cost of a compile dependency and hand-written timing code.

## 3. Duplicate metrics with Actuator

Source: `SpringBootActuatorInstrumentationModule.java` and `metadata.yaml` in
`instrumentation/spring/spring-boot-actuator-autoconfigure-2.0`.

- The agent ships an Actuator integration module that "configures the OpenTelemetry Micrometer bridge to receive metrics
  from Spring Boot Actuator". It is **disabled by default** (`defaultEnabled()` returns `false`), with the in-code
  comment "produces a lot of metrics that are already captured - e.g. JVM memory usage".
- It registers under the instrumentation name `micrometer` ("Share the micrometer selector so both registry integration
  paths use one setting"). So setting `otel.instrumentation.micrometer.enabled=true` also turns on the Actuator hookup,
  and Boot's auto-configured Micrometer meters are forwarded to OTel in addition to the agent's native JVM and HTTP
  metrics. Overlap is therefore real, and the agent's own comment says so for JVM memory.
- Control on the Spring side: Spring Boot filters meters by ID prefix with `management.metrics.enable.<prefix>=false`
  (config property `management.metrics.enable`, Spring Boot Actuator metrics reference). Which prefixes cover Boot's
  JVM, process, system and HTTP server meters (`jvm`, `process`, `system`, `http`) is **[unverified]**.
- Spring Boot's own OTLP registry (`management.otlp.metrics.export.*`) is a separate path. By default it exports to a
  local OTLP consumer if the registry is on the classpath. Keep it off to avoid a second export path
  (`management.otlp.metrics.export.enabled=false` **[unverified]**).
- Cleanest option for Milestone 2: leave the bridge **off** and emit domain metrics with `opentelemetry-api`. The
  agent's native JVM and HTTP metrics then arrive exactly once.

## 4. Histogram buckets and export interval

Sources: SDK environment variable spec (`specification/configuration/sdk-environment-variables.md` in
<https://github.com/open-telemetry/opentelemetry-specification>), OTLP exporter configuration on opentelemetry.io.

- `OTEL_METRIC_EXPORT_INTERVAL`: milliseconds between export attempts, default `60000`.
  `OTEL_METRIC_EXPORT_TIMEOUT`: default `30000`.
- `OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION`: `explicit_bucket_histogram` (default) or
  `base2_exponential_bucket_histogram`.
- `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE`: `cumulative` (default), `delta` or `lowmemory`.
- Custom boundaries for one instrument: `setExplicitBucketBoundariesAdvice` in code. For agent-created metrics, runtime
  telemetry README documents **Views** in declarative configuration (`meter_provider.views` with `selector` and
  `stream`), which is how to drop or reshape `jvm.*` metrics. Whether views can be used with env-var-only configuration
  is **[unverified]**; it likely needs a config file via the experimental declarative config.

## Implications for the map

1. The exponential-histogram option interacts with the SS4O exporter: it supports `ExponentialHistogram` documents
   (`positiveBuckets` etc.), but Dashboards visualization of those is harder than explicit buckets. Default to explicit.
2. `http.response.status_code` arrives as an int attribute, `http.route` as a string, as assumed in the SS4O template
   research.
3. Set `OTEL_METRIC_EXPORT_INTERVAL` lower than 60000 for demos and `make verify` (suggestion; not a researched fact).

## Open items

1. Which agent version the Paketo buildpack `2.31.0` ships and whether it matches `main` for the facts above.
2. Real-app smoke test of default HTTP server metrics and `http.route`.
3. Spring Boot 4.1 meter-prefix names to disable if the bridge is enabled.

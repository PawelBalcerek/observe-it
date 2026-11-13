# Metrics & OpenSearch Dashboards in Kotlin & Spring Boot

## Auto-Instrumented Runtime & HTTP Metrics vs. Custom Domain Metrics

Production JVM services emit two complementary layers of metrics: infrastructure telemetry captured automatically by
runtime instrumentation and business telemetry emitted explicitly by domain code. Understanding where each layer ends
prevents both redundant manual instrumentation and blind spots in business observability.

A zero-code instrumentation agent attaches bytecode hooks to standard runtime subsystems—garbage collection, heap and
non-heap memory pools, thread pools, CPU utilization, HTTP server request latencies, and outbound HTTP client calls.
Because those signals follow standardized semantic conventions, operations teams get golden signals (latency, traffic,
errors, and saturation) across every service without writing boilerplate timers or gauges.

Runtime instrumentation cannot, however, infer business semantics. An HTTP `200 OK` response does not distinguish a
completed request from a soft business rejection, nor does an HTTP route reveal which downstream resource was exhausted,
which external gateway timed out, or how many items sit in an in-memory store. Custom domain metrics fill that gap—and
isolating them behind a pure domain port (`MetricsPort`) keeps business services free of vendor SDKs while an
infrastructure adapter translates domain calls into `io.opentelemetry.api.OpenTelemetry` meters.

### External Sources & Ecosystem Practice

- [OpenTelemetry Java Agent Supported Libraries](https://opentelemetry.io/docs/zero-code/java/agent/supported-libraries/)
  — Runtime, JVM, and HTTP server/client metrics emitted automatically by the Java Agent.
- [OpenTelemetry Semantic Conventions for HTTP Metrics](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/)
  — Standardized `http.server.request.duration` and `http.client.request.duration` instrument definitions.
- [OpenTelemetry Semantic Conventions for JVM Metrics](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/)
  — Standardized `jvm.memory.used`, `jvm.gc.duration`, `jvm.thread.count`, and `jvm.cpu.recent_utilization` gauges.

### In Observe-It

In Observe-It, the Paketo-injected OpenTelemetry Java Agent emits JVM (`jvm.memory.used`, `jvm.gc.duration`,
`jvm.thread.count`) and HTTP (`http.server.request.duration`, `http.client.request.duration`) metrics automatically when
`OTEL_METRICS_EXPORTER=otlp` is set in
[`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml).

Custom domain metrics are defined behind pure Kotlin interfaces with zero OpenTelemetry imports and implemented by
infrastructure adapters backed by `io.opentelemetry:opentelemetry-api` (see
[ADR-0006](../adr/0006-otel-api-domain-metrics-port-and-in-memory-testing.md)):

- **Observed App**:
  [`OrderMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderMetrics.kt)
  in `order.domain` is implemented by
  [`OtelOrderMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/infrastructure/OtelOrderMetrics.kt)
  under instrumentation scope `com.observeit.observedapp.order`.
- **Traffic Generator**:
  [`TrafficMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/TrafficMetrics.kt)
  in `traffic.domain` is implemented by
  [`OtelTrafficMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/infrastructure/OtelTrafficMetrics.kt)
  under instrumentation scope `com.observeit.trafficgenerator.traffic`.

Each service exposes an
[`OpenTelemetryConfiguration`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/infrastructure/OpenTelemetryConfiguration.kt)
bean (`@ConditionalOnMissingBean`) returning `GlobalOpenTelemetry.get()`, which the Java Agent populates at container
runtime.

## Designing Domain Metric Instruments & Cardinality Boundaries

Selecting the right OpenTelemetry instrument type—counter, histogram, or asynchronous gauge—determines which
aggregations remain mathematically valid in downstream dashboards:

- **Monotonic Counter (`LongCounter`)**: Records cumulative or delta event counts that only increase (such as requests
  processed, state transitions, or categorized errors). Use counters for throughput rates and error ratios.
- **Histogram (`DoubleHistogram`)**: Records statistical distributions of measurements (such as external gateway
  latency or end-to-end operation duration) with explicit bucket boundaries (`setExplicitBucketBoundariesAdvice`) so
  percentile and max/sum/count queries avoid default bucket mismatches.
- **Asynchronous Gauge (`DoubleGauge` / `LongGauge`)**: Samples current point-in-time state via a callback on each
  collection cycle (such as queue depth, active worker count, or configured rate).

Equally critical is **attribute cardinality control**. Every unique combination of metric name and attribute key-value
pairs creates a distinct time series in memory and in the storage index. High-cardinality identifiers (`request.id`,
`entity.id`, `user.id`, `trace_id`) belong in structured logs and traces—never in metric attributes. Metric attributes
must be bounded enums or small finite sets (operation outcomes, state names, currency codes, or finite product SKUs).

### External Sources & Ecosystem Practice

- [OpenTelemetry Metrics API Specification](https://opentelemetry.io/docs/specs/otel/metrics/api/) — Instrument
  selection (`Counter`, `Histogram`, `Gauge`), UCUM unit conventions, and explicit bucket boundary advice.
- [OpenTelemetry Attribute Naming & Cardinality Guidance](https://opentelemetry.io/docs/specs/semconv/general/metrics/)
  — Keeping metric dimensions bounded and avoiding unbounded identifiers on metric points.

### In Observe-It

Observe-It defines ten custom domain meters across both services using UCUM unit strings from
[`OrderTelemetry`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderTelemetry.kt)
and
[`TrafficTelemetry`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/TrafficTelemetry.kt):

| Service             | Metric Name                    | Instrument  | Unit            | Bounded Attributes                                        |
| :------------------ | :----------------------------- | :---------- | :-------------- | :-------------------------------------------------------- |
| `observed-app`      | `orders.created`               | Counter     | `{order}`       | `order.currency`                                          |
| `observed-app`      | `order.state.transitions`      | Counter     | `{transition}`  | `order.previous_state`, `order.state`                     |
| `observed-app`      | `order.errors`                 | Counter     | `{error}`       | `error.type`                                              |
| `observed-app`      | `order.inventory.reservations` | Counter     | `{reservation}` | `inventory.sku`, `inventory.outcome`                      |
| `observed-app`      | `order.payment.duration`       | Histogram   | `ms`            | `payment.method`, `payment.outcome`                       |
| `observed-app`      | `order.repository.size`        | Async Gauge | `{order}`       | *(none)*                                                  |
| `traffic-generator` | `traffic.scenarios.executed`   | Counter     | `{scenario}`    | `checkout.scenario`, `order.state`                        |
| `traffic-generator` | `traffic.scenario.duration`    | Histogram   | `ms`            | `checkout.scenario`                                       |
| `traffic-generator` | `traffic.target_rate`          | Async Gauge | `{order}/s`     | *(none)*                                                  |
| `traffic-generator` | `traffic.workers.active`       | Async Gauge | `{worker}`      | *(none)*                                                  |

Unbounded identifiers (`order.id`, `customer.id`) are strictly excluded from metric attributes and remain confined to
structured logs ([Structured Logging in Kotlin & Spring Boot](./structured-logging.md)). Histograms declare explicit
bucket boundaries (`PAYMENT_DURATION_BUCKETS` from `10.0` to `5000.0` ms in `OtelOrderMetrics`;
`SCENARIO_DURATION_BUCKETS` from `50.0` to `10000.0` ms in `OtelTrafficMetrics`).

## Delta Temporality, SS4O Metric Normalization & 64-Bit Field Mappings

OpenTelemetry metrics support two aggregation temporalities: `CUMULATIVE` (running totals since process startup) and
`DELTA` (the change since the previous export interval). While Prometheus-style pull scrapers expect `CUMULATIVE`
counters, search-index backends such as OpenSearch aggregate time-series documents over `@timestamp` buckets. With
`DELTA` temporality, summing `value` across a 1-minute histogram bucket yields the exact event count in that window
without derivative math or reset-detection scripts across container restarts.

When an OTLP exporter writes metric points into a search index, two schema pitfalls require explicit index templates and
ingest normalization:

1. **Integer width overflow**: Exporters that store integer and floating-point measurements in separate fields risk
   dynamic 32-bit `integer` inference if the first indexed document is small, causing large 64-bit byte gauges (such as
   JVM memory limits) to fail indexing. Mapping integer value fields explicitly as 64-bit `long` prevents overflow.
2. **Split scalar fields and nested resource metadata**: Visualizations cannot aggregate a single metric field when
   integer counters and floating-point gauges write to different keys. An ingest pipeline script unifies both into one
   floating-point scalar field and promotes nested service metadata into top-level query fields matching the log schema.

### External Sources & Ecosystem Practice

- [OpenTelemetry Metrics Data Model — Temporality](https://opentelemetry.io/docs/specs/otel/metrics/data-model/#temporality)
  — Trade-offs between `DELTA` and `CUMULATIVE` aggregation temporality.
- [OpenSearch Simple Schema for Observability (SS4O)](https://docs.opensearch.org/latest/observing-your-data/ss4o/)
  — SS4O metric document fields (`name`, `kind`, `unit`, `value@int`, `value@double`, `sum`, `count`, `min`, `max`).

### In Observe-It

Both `observed-app` and `traffic-generator` export `DELTA` metrics every second in
[`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml):

```yaml
- OTEL_METRICS_EXPORTER=otlp
- OTEL_METRIC_EXPORT_INTERVAL=1000
- OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=delta
```

The OpenTelemetry Collector routes metrics through its `metrics` pipeline (`receivers: [otlp]`,
`processors: [memory_limiter, batch]`, `exporters: [opensearch]`) to the `ss4o_metrics-observe-it-local` **SS4O
Metrics Index**. At index time,
[`docker/opensearch/metrics-index-template.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/metrics-index-template.json)
enforces `value@int: long`, `value: double`, `value@double: double`, `count: long`, `sum/min/max: double`, and
attaches `observe-it-metrics-pipeline`
([`docker/opensearch/metrics-ingest-pipeline.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/metrics-ingest-pipeline.json))
as the `default_pipeline`. That Painless script promotes `resource['service.name']`, `resource['service.version']`, and
`resource['deployment.environment.name']` to top-level `service`, `version`, and `env` (with `serviceName` fallback),
unifies `value@double` / `value@int` into `value`, and strips `attributes.data_stream`.

For full architectural rationale, see
[ADR-0007: SS4O Metrics Delta Temporality and OpenSearch Schema Normalization](../adr/0007-ss4o-metrics-delta-temporality-and-opensearch-normalization.md).

## In-Memory Metric Verification in Unit & Integration Tests

Testing metrics by mocking meter builders or counter interfaces couples tests to OpenTelemetry SDK plumbing and misses
bugs in unit strings, attribute keys, or callback registration. Instead, OpenTelemetry provides an in-memory metric
reader (`InMemoryMetricReader`) that wires into a real `OpenTelemetrySdk` inside unit and integration tests.

Configuring the reader with delta temporality (`InMemoryMetricReader.createDelta()`) and draining collected points
before and after each test keeps assertions isolated without restarting the Spring application context (`@DirtiesContext`).
Tests assert exact metric names, UCUM units, point values (or histogram `count` and `sum`), and attribute maps through
a reusable test fixture.

### External Sources & Ecosystem Practice

- [OpenTelemetry Java SDK Testing (`opentelemetry-sdk-testing`)](https://opentelemetry.io/docs/languages/java/testing/)
  — Using `InMemoryMetricReader` and `OpenTelemetrySdk` for deterministic in-memory metric assertions.

### In Observe-It

Both `observed-app` and `traffic-generator` ship a `MetricCapture` JUnit 5 extension in `testFixtures` (for example,
[`observed-app/.../test/MetricCapture.kt`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/testFixtures/kotlin/com/observeit/observedapp/test/MetricCapture.kt))
backed by `InMemoryMetricReader.createDelta()`.

- **Unit tests**:
  [`OtelOrderMetricsTest`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/test/kotlin/com/observeit/observedapp/order/infrastructure/OtelOrderMetricsTest.kt)
  and
  [`OtelTrafficMetricsTest`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/test/kotlin/com/observeit/trafficgenerator/traffic/infrastructure/OtelTrafficMetricsTest.kt)
  register `@RegisterExtension val metricCapture = MetricCapture()` and verify counters, histograms, and gauges with
  `assertCounterIncremented`, `assertHistogramRecorded`, and `assertGaugeValue`:

  ```kotlin
  metricCapture.assertCounterIncremented(
      name = "orders.created",
      unit = "{order}",
      delta = 1L,
      attributes = mapOf("order.currency" to "USD"),
  )
  ```

- **Integration tests**:
  [`TestMetricsConfiguration`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/integration/kotlin/com/observeit/observedapp/test/TestMetricsConfiguration.kt)
  provides a `@Primary` `OpenTelemetry` bean backed by `MetricCapture`, and
  [`IntegrationTest`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/integration/kotlin/com/observeit/observedapp/IntegrationTest.kt)
  calls `metricCapture.drainAndReset()` in `@BeforeEach` / `@AfterEach` so cached Spring Boot contexts stay isolated
  across all seven **Checkout Scenario** integration tests.

# 0011. OpenTelemetry MongoDB Client Telemetry, Stable Database Semconv, and Noise Suppression

**Status:** Accepted

## Context

Persisting **Order** aggregates in MongoDB ([ADR-0010](0010-mongodb-order-persistence-and-ttl-retention.md)) activates
the OpenTelemetry Java Agent's `io.opentelemetry.mongo-4.0` bytecode instrumentation, exposing four database telemetry
and OpenSearch schema challenges:

1. **Legacy `V0_STABLE` defaults vs. OpenSearch mapping collisions under `database/dup`**:
   By default, the Java Agent emits legacy `V0` database attributes (`db.system`, `db.operation`, `db.statement`) and
   omits the stable `db.client.operation.duration` histogram metric. Opting into dual emission
   (`OTEL_SEMCONV_STABILITY_OPT_IN=database/dup`) breaks OpenSearch indexing because `attributes.db.system` and
   `attributes.db.operation` (scalar `keyword` fields in `V0`) collide with `attributes.db.system.name` and
   `attributes.db.operation.name` (nested JSON objects in stable `V1`).
2. **Redundant Spring Data proxy spans**:
   Leaving `io.opentelemetry.spring-data-1.8` enabled can emit extra `SpanKind.INTERNAL` spans around Spring Data
   operations alongside the **Observed App**'s explicit `order.<action>` domain spans.
3. **High-frequency background telemetry noise**:
   `OtelOrderMetrics` samples `order.repository.size` every 1 second via `orderRepository.count()` on the background
   `PeriodicMetricReader-1` thread, and Docker Compose health checks poll `/actuator/health` (which invokes
   `MongoHealthIndicator`) every 10 seconds. Left unfiltered, these background calls flood the **SS4O Traces Index** and
   **SS4O Metrics Index** with 3,600 unparented root `count orders` spans per hour plus `/actuator/health`, `hello`,
   `isMaster`, and `listDatabases` probe noise.
4. **Long sanitized BSON query strings**:
   Sanitized multi-item **Order** BSON commands stored in `attributes.db.query.text` can exceed OpenSearch's default
   `ignore_above: 1024` dynamic keyword limit, causing long queries to be silently skipped during indexing.

## Decision

We opt into stable OpenTelemetry database semantic conventions, suppress background gauge and health-probe telemetry at
the appropriate layer, map stable `attributes.db.*` fields explicitly in OpenSearch, and add dedicated MongoDB
Dashboards saved objects:

1. **Stable database semantic conventions without Spring Data spans**:
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) sets
   `OTEL_SEMCONV_STABILITY_OPT_IN=database` and `OTEL_INSTRUMENTATION_SPRING_DATA_ENABLED=false` on `observed-app`. The
   MongoDB driver instrumentation emits `SpanKind.CLIENT` spans (`find orders`, `update orders`) parented directly under
   `order.<action>` domain spans with stable `db.system.name`, `db.namespace`, `db.collection.name`,
   `db.operation.name`, and sanitized `db.query.text` attributes, plus the `db.client.operation.duration` histogram
   metric.
2. **In-process suppression of 1 Hz gauge callback queries**:
   [`OtelOrderMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/infrastructure/OtelOrderMetrics.kt)
   wraps `orderRepository.count()` inside the `order.repository.size` observable gauge callback with
   `InstrumentationUtil.suppressInstrumentation { ... }` (`io.opentelemetry.api.impl.InstrumentationUtil` from
   `io.opentelemetry:opentelemetry-api:1.65.0`, permitted by `VerifyOtelApiBoundaryTask`). The Java Agent bridges this
   call so the background `count` command emits zero `count orders` spans and zero `db.client.operation.duration`
   datapoints.
3. **OpenTelemetry Collector health-probe filtering**:
   [`docker/otel-collector/config.yaml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/otel-collector/config.yaml)
   wires `filter/traces` (`trace_conditions`) and `filter/metrics` (`metric_conditions`) between `memory_limiter` and
   `batch` to drop `/actuator/health` HTTP spans and metrics as well as MongoDB `hello`, `isMaster`, and `listDatabases`
   admin commands before export to OpenSearch.
4. **Explicit SS4O OpenSearch mappings for `attributes.db.*` and `attributes.network.peer.*`**:
   [`docker/opensearch/traces-index-template.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/traces-index-template.json)
   maps `attributes.db.system.name`, `namespace`, `collection.name`, `operation.name`, `query.summary`, and
   `response.status_code` as `keyword`, `operation.batch.size` as `long`, and `attributes.db.query.text` as `keyword`
   with `ignore_above: 8192`.
   [`docker/opensearch/metrics-index-template.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/metrics-index-template.json)
   maps `attributes.db.*` (`system.name`, `namespace`, `collection.name`, `operation.name`, `response.status_code`) and
   `attributes.network.peer.*` (`address` as `keyword`, `port` as `long`).
5. **OpenSearch Dashboards MongoDB exploration objects**:
   [`docker/opensearch/saved-objects.ndjson`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/saved-objects.ndjson)
   updates the `ss4o_traces` and `ss4o_metrics` index patterns and adds `MongoDB Client Spans`
   (`search:mongodb-client-spans`), `MongoDB Client Span Latency by Operation (ms)`
   (`visualization:mongodb-span-latency-by-operation` on `Observe-It Traces Overview`), and `MongoDB Operation Duration
   (s)` (`visualization:mongodb-operation-duration` on `Observe-It Metrics Overview`), bringing the total provisioned
   saved objects to 34.

## Considered Options

1. **Default `V0_STABLE` database semantic conventions**:
   Leaving `OTEL_SEMCONV_STABILITY_OPT_IN` unset was rejected because `V0_STABLE` omits the
   `db.client.operation.duration` histogram metric and emits deprecated `db.system`, `db.operation`, and `db.statement`
   attributes.
2. **Dual emission (`OTEL_SEMCONV_STABILITY_OPT_IN=database/dup`)**:
   Dual emission was rejected because OpenSearch rejects documents where a dotted path is both a scalar `keyword`
   (`attributes.db.system`, `attributes.db.operation`) and a JSON object (`attributes.db.system.name`,
   `attributes.db.operation.name`).
3. **Dropping `count orders` in the Collector instead of in-process suppression**:
   Relying solely on Collector `filter` rules for the 1 Hz gauge callback was rejected because it still incurs
   per-second span and metric allocation, serialization, and OTLP transport overhead inside the **Observed App** JVM.
4. **Disabling Spring Boot Actuator `MongoHealthIndicator`**:
   Turning off `management.health.mongodb.enabled` was rejected because `/actuator/health` should genuinely verify
   MongoDB connectivity for container readiness while the Collector filters out routine health-probe telemetry.

## Consequences

- **Good, because** every `order.<action>` span parents clean `find orders` and `update orders` `CLIENT` spans with
  stable `db.*` attributes and sanitized `db.query.text`, accompanied by `db.client.operation.duration` histograms.
- **Good, because** the **SS4O Traces Index** and **SS4O Metrics Index** stay free of 1 Hz `count orders` and 10-second
  health-probe noise without disabling real MongoDB health checks.
- **Bad, because** `InstrumentationUtil.suppressInstrumentation` relies on an `io.opentelemetry.api.impl` internal API
  class that could change across major OpenTelemetry API upgrades.
- **Neutral, because** raising `ignore_above` to `8192` on `attributes.db.query.text` indexes full multi-item **Order**
  BSON commands at a modest per-document term-index storage cost.

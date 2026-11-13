# 0006. OpenTelemetry API Domain Metrics Port Boundary and In-Memory Testing

**Status:** Accepted

## Context

In Milestone 1 ([ADR-0001](0001-zero-code-otel-javaagent-and-logback-bridge.md)), both the **Observed App** and
**Traffic Generator** maintained zero `io.opentelemetry.*` dependencies on their compile and runtime classpaths because
structured logging used standard SLF4J 2 and Logback APIs bridged by the OpenTelemetry Java Agent.

Milestone 2 introduces custom domain metrics—counters for **Order** creation, **Order State** transitions, errors,
inventory reservations, and **Checkout Scenario** executions; histograms for payment and scenario latency; and
asynchronous gauges for repository size and worker throughput. Unlike SLF4J 2 for logging, no domain-neutral JVM metrics
API bridges seamlessly to OpenTelemetry semantic conventions without either adopting Micrometer or depending on
`io.opentelemetry:opentelemetry-api`. At the same time, domain services (`OrderService`, `CheckoutScenarioRunner`) must
remain decoupled from OpenTelemetry SDKs, and unit and integration tests must assert exact metric names, units,
values, and attributes deterministically in memory without spinning up an OTLP collector.

## Decision

We introduce `io.opentelemetry:opentelemetry-api` as the sole OpenTelemetry compile-time dependency, isolated behind
pure `domain` metric ports, and verify all emitted metrics in memory using `opentelemetry-sdk-testing`:

1. **API-only compile dependency in shared convention plugin**:
   [`build-logic/conventions/src/main/kotlin/conventions/spring.gradle.kts`](https://github.com/PawelBalcerek/observe-it/blob/main/build-logic/conventions/src/main/kotlin/conventions/spring.gradle.kts)
   adds `io.opentelemetry:opentelemetry-api` to `implementation` and `io.opentelemetry:opentelemetry-sdk-testing` to
   `testFixturesApi`. Application runtime classpaths still exclude `io.opentelemetry:opentelemetry-sdk` and
   `io.opentelemetry.instrumentation:*`.
2. **Pure `domain` ports and `infrastructure` OpenTelemetry adapters**:
   - In `observed-app`,
     [`OrderMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderMetrics.kt)
     is a pure Kotlin interface in the `order.domain` package with zero OpenTelemetry imports, implemented by
     [`OtelOrderMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/infrastructure/OtelOrderMetrics.kt)
     in `order.infrastructure`.
   - In `traffic-generator`,
     [`TrafficMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/TrafficMetrics.kt)
     is a pure Kotlin interface in `traffic.domain`, implemented by
     [`OtelTrafficMetrics`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/infrastructure/OtelTrafficMetrics.kt)
     in `traffic.infrastructure`.
   - Each service provides an
     [`OpenTelemetryConfiguration`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/infrastructure/OpenTelemetryConfiguration.kt)
     bean (`@ConditionalOnMissingBean`) returning `GlobalOpenTelemetry.get()`, which the Paketo-injected Java Agent
     populates automatically at container runtime without relying on Micrometer or the Java Agent Micrometer bridge.
3. **Classpath boundary verification moved to E2E Gate 1**:
   The Milestone 1 `ClasspathVerificationTest` unit tests (which asserted zero `io.opentelemetry.*` classes) were
   removed and replaced by Gate 1 in
   [`scripts/verify.sh`](https://github.com/PawelBalcerek/observe-it/blob/main/scripts/verify.sh), which inspects
   `runtimeClasspath` for `:observed-app` and `:traffic-generator` to confirm `io.opentelemetry:opentelemetry-api` is
   present while `opentelemetry-sdk` and `opentelemetry.instrumentation` artifacts remain absent.
4. **In-memory metric verification via `MetricCapture`**:
   Both modules provide a `MetricCapture` JUnit 5 extension in `testFixtures` backed by
   `InMemoryMetricReader.createDelta()` and `OpenTelemetrySdk`. Integration tests wire `MetricCapture` into the Spring
   context via `TestMetricsConfiguration` (`@Primary` `OpenTelemetry` bean) and drain state between tests without
   `@DirtiesContext`.

## Considered Options

1. **Micrometer (`MeterRegistry`) with the OpenTelemetry Java Agent Micrometer bridge**:
   Using Micrometer as the domain metrics facade and relying on the Java Agent's Micrometer bridge was not chosen for
   Milestone 2 (deferred rather than permanently ruled out). Micrometer uses different instrument semantics (e.g.,
   `Timer` vs. OpenTelemetry `DoubleHistogram` with explicit bucket boundaries) and adds an extra translation layer
   between application code and OTLP metric payloads. Using `opentelemetry-api` directly behind domain ports keeps
   metric names, UCUM units, and explicit histogram bucket boundaries (`setExplicitBucketBoundariesAdvice`) identical
   between tests and production, and Micrometer integration can still be revisited in a future ADR if needed.
2. **Pulling `opentelemetry-sdk` directly into `implementation`**:
   Configuring `OpenTelemetrySdk` inside application code was rejected because it duplicates the SDK already managed
   by the OpenTelemetry Java Agent and couples application builds to exporter dependencies.

## Consequences

- **Good, because** domain services (`OrderService`, `CheckoutScenarioRunner`) remain 100% free of OpenTelemetry imports
  behind `OrderMetrics` and `TrafficMetrics`.
- **Good, because** `OtelOrderMetrics` and `OtelTrafficMetrics` are verified deterministically in unit and black-box
  integration tests via `MetricCapture` (`InMemoryMetricReader.createDelta()`) without spinning up an OTLP collector
  or using `@DirtiesContext`.
- **Good, because** the OpenTelemetry Java Agent continues to own SDK lifecycle, batching, and OTLP export at runtime.
- **Bad, because** both services now carry `io.opentelemetry:opentelemetry-api` (and its transitive
  `opentelemetry-context` / `opentelemetry-common` artifacts) on `runtimeClasspath`.
- **Bad, because** any test context loading `OtelOrderMetrics` or `OtelTrafficMetrics` without the Java Agent must
  supply `TestMetricsConfiguration` so `GlobalOpenTelemetry.get()` does not fall back to a no-op instance.
- **Neutral, because** Gate 1 in `scripts/verify.sh` replaces the Milestone 1 `ClasspathVerificationTest` unit tests to
  enforce the API-only runtime classpath boundary.

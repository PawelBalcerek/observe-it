# 0008. OpenTelemetry Distributed Tracing and Pure Domain Span Ports

**Status:** Accepted

## Context

In Milestone 1 ([ADR-0001](0001-zero-code-otel-javaagent-and-logback-bridge.md)), both the **Observed App** and
**Traffic Generator** relied solely on the Paketo-injected OpenTelemetry Java Agent for HTTP and Logback bridging.
Milestone 2 ([ADR-0006](0006-otel-api-domain-metrics-port-and-in-memory-testing.md)) introduced
`io.opentelemetry:opentelemetry-api` as the sole OpenTelemetry compile-time dependency behind pure `domain` metric
ports (`OrderMetrics`, `TrafficMetrics`) and verified emitted metrics in memory via `MetricCapture`.

Milestone 3 adds distributed tracing across both services: end-to-end W3C Trace Context (`traceparent`) propagation from
**Traffic Generator** **Checkout Scenario** executions into **Observed App** HTTP endpoints, custom domain spans and
lifecycle span events, consistent span error/exception discipline, dual enrichment of SLF4J `MDC` and active spans,
and unified in-memory verification of metrics and traces without spinning up an OTLP collector.

## Decision

We extend the pure `domain` port boundary and `opentelemetry-api`-only runtime constraint to distributed tracing, and
unify in-memory telemetry testing in `TestOpenTelemetryConfiguration`:

1. **Pure `domain` tracing ports and `infrastructure` OpenTelemetry adapters**:
   - In `observed-app`,
     [`OrderTracer`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderTracer.kt)
     and `OrderReservationSpan` define pure Kotlin tracing contracts in `order.domain` with zero OpenTelemetry imports,
     implemented by
     [`OtelOrderTracer`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/infrastructure/OtelOrderTracer.kt)
     in `order.infrastructure`. It emits `INTERNAL` spans (`order.create`, `order.get`, `order.reserve`, `order.pay`,
     `order.ship`, `order.cancel`), a `CLIENT` span (`payment.process`) for the simulated payment gateway call, and
     structured span events (`inventory.reservation` and `order.state.transitioned`).
   - In `traffic-generator`,
     [`TrafficTracer`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/TrafficTracer.kt)
     and `ScenarioSpan` define the pure domain port in `traffic.domain`, implemented by
     [`OtelTrafficTracer`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/infrastructure/OtelTrafficTracer.kt)
     to wrap each **Checkout Scenario** execution in an `INTERNAL` `checkout.scenario` span.
2. **Span status and exception discipline**:
   Business-rule 4xx rejections (`OutOfStockException`, `PaymentDeclinedException`, `OrderNotFoundException`,
   `InvalidOrderStateTransitionException`) record `error.type` (and outcome attributes) on the span while leaving
   span status `Unset` with no exception event. Only unexpected infrastructure failures—`PaymentGatewayTimeoutException`
   (`504` in `OtelOrderTracer`) and uncaught exceptions in `OtelTrafficTracer` (or `ScenarioSpan.recordTimeout()`)—set
   `StatusCode.ERROR` and call `span.recordException(exception)`.
3. **Dual correlation enrichment and parent-span propagation**:
   [`CorrelationContext`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/correlation/CorrelationContext.kt)
   dual-enriches SLF4J `MDC` and `Span.current()` with `checkout.scenario`, `order.id`, and `customer.id` on the
   request's virtual thread. `OtelOrderTracer.inSpan` also copies `checkout.scenario` onto newly started domain spans and
   back-populates `customer.id` onto the parent HTTP `SERVER` span when available.
4. **W3C `traceparent` propagation**:
   At container runtime, the OpenTelemetry Java Agent automatically injects and extracts W3C `traceparent` headers
   across `RestClient` and Spring WebMVC. In `traffic-generator` integration tests backed by `MockRestServiceServer`,
   [`TestObservedAppClientConfiguration`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/integration/kotlin/com/observeit/trafficgenerator/traffic/TestObservedAppClientConfiguration.kt)
   registers a `RestClient` request interceptor that calls `openTelemetry.propagators.textMapPropagator.inject(...)`
   so `TrafficTriggerIntegrationTest` verifies W3C `traceparent` propagation deterministically.
5. **Unified `TestOpenTelemetryConfiguration` and `SpanCapture`**:
   Both modules provide a `SpanCapture` JUnit 5 extension in `testFixtures` backed by `InMemorySpanExporter`,
   `SimpleSpanProcessor`, and `W3CTraceContextPropagator`.
   [`TestOpenTelemetryConfiguration`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/integration/kotlin/com/observeit/observedapp/test/TestOpenTelemetryConfiguration.kt)
   replaces `TestMetricsConfiguration` with a single `@Primary` `OpenTelemetrySdk` bean combining
   `MetricCapture.meterProvider`, `SpanCapture.tracerProvider`, and `SpanCapture.propagators`, reset between tests in
   `IntegrationTest`.

## Considered Options

1. **OpenTelemetry `@WithSpan` annotations directly on `OrderService` and `CheckoutScenarioRunner`**:
   Using `@WithSpan` annotations in `domain` classes was rejected because it introduces `opentelemetry-instrumentation`
   annotation dependencies into the domain layer and cannot express conditional `StatusCode.ERROR` discipline,
   domain-specific span events (`inventory.reservation`, `order.state.transitioned`), or parent-span `customer.id`
   back-propagation cleanly.
2. **Keeping `TestMetricsConfiguration` separate from a new `TestTracingConfiguration`**:
   Registering two separate `@Primary` `OpenTelemetry` test configurations was rejected because Spring Boot wires a
   single `OpenTelemetry` instance into both `OtelOrderMetrics` / `OtelTrafficMetrics` and `OtelOrderTracer` /
   `OtelTrafficTracer`; a unified `TestOpenTelemetryConfiguration` lets integration tests assert logs, metrics, and
   traces together in one Spring context.
3. **Manual W3C `traceparent` header injection in production `RestClient` configuration**:
   Adding explicit `TextMapPropagator.inject` interceptors to production `RestClient` beans was deferred because the
   OpenTelemetry Java Agent already instruments `RestClient` and Spring WebMVC out of the box, leaving manual injection
   scoped strictly to `TestObservedAppClientConfiguration` for `MockRestServiceServer`.

## Consequences

- **Good, because** `OrderService` and `CheckoutScenarioRunner` remain completely free of OpenTelemetry imports behind
  `OrderTracer` and `TrafficTracer`.
- **Good, because** 4xx business rejections and 504 infrastructure timeouts are cleanly separated in trace error rates
  without polluting `StatusCode.ERROR` alerts with expected domain rejections.
- **Good, because** `SpanCapture` and `MetricCapture` share one in-memory `OpenTelemetrySdk` in integration tests,
  enabling single-test assertions across logs, metrics, spans, events, and W3C `traceparent` headers.
- **Bad, because** any new domain attribute or span event added to `OtelOrderTracer` or `OtelTrafficTracer` requires
  keeping `OrderTracer` / `TrafficTracer` port signatures in sync with `domain` callers.
- **Neutral, because** Gate 1 in `scripts/verify.sh` continues to enforce that only `io.opentelemetry:opentelemetry-api`
  (and never `opentelemetry-sdk` or `opentelemetry.instrumentation`) appears on application `runtimeClasspath`.

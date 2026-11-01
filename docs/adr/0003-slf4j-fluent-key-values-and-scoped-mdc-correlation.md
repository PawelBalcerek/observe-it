# 0003. SLF4J 2 Fluent Key-Value API and Scoped MDC Correlation

**Status:** Accepted

## Context

Structured logs in Observe-It carry two distinct categories of metadata:

1. **Ambient correlation identifiers** (`checkout.scenario`, `order.id`, `customer.id`) that apply to every log
   statement emitted during an HTTP request or **Checkout Scenario** execution, including unhandled exception
   handling in Spring Web controller advice.
2. **Event-specific domain attributes** (`order.state`, `order.previous_state`, `order.total_amount`, `inventory.sku`,
   `inventory.requested_quantity`, `payment.method`, `payment.latency_ms`, `error.type`) that describe a single state
   transition or operational event and often carry numeric types (`Double`, `Long`) required for range queries and
   metric aggregations in the **SS4O Log Index**.

Placing event-specific attributes into the Mapped Diagnostic Context (MDC) coerces all values to `String` (losing
numeric types in OpenSearch) and risks attribute leakage across sequential log statements on the same thread.
Conversely, manually passing correlation identifiers into every `.addKeyValue()` call produces repetitive boilerplate
and misses framework and controller advice error logs.

## Decision

We divide telemetry attribution between scoped MDC correlation and SLF4J 2 fluent key-value pairs:

1. **Scoped MDC correlation via `CorrelationContext`**:
   - In the **Observed App**,
     [`MdcCorrelationFilter`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/correlation/api/MdcCorrelationFilter.kt)
     (`@Order(Ordered.HIGHEST_PRECEDENCE)`) extracts `X-Checkout-Scenario` into MDC on request entry and guarantees
     cleanup via
     [`CorrelationContext.clear()`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/correlation/CorrelationContext.kt)
     in a `finally` block;
     [`OrderMdcInterceptor`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/api/OrderMdcInterceptor.kt)
     populates `order.id` from `{orderId}` URI template variables before controller and exception-handler execution;
     and
     [`OrderService`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderService.kt)
     binds `order.id` and `customer.id` upon **Order** creation and lookup.
   - In the **Traffic Generator**,
     [`CheckoutScenarioRunner`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/CheckoutScenarioRunner.kt)
     scopes `checkout.scenario`, `customer.id`, and `order.id` in `CorrelationContext` across each virtual-thread
     scenario execution (`try` / `finally`) and propagates `X-Checkout-Scenario` over HTTP via
     [`RestObservedAppClient`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/infrastructure/RestObservedAppClient.kt).
2. **SLF4J 2 fluent logging for per-event typed attributes**:
   Domain services and API advice
   ([`OrderService`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderService.kt),
   [`OrderControllerAdvice`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/api/OrderControllerAdvice.kt),
   [`CheckoutScenarioRunner`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/CheckoutScenarioRunner.kt))
   attach per-event attributes using the fluent SLF4J 2 API (`.atInfo()`, `.atWarn()`, `.atError()`) with canonical
   dotted keys from
   [`OrderTelemetry`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderTelemetry.kt),
   preserving native `Double` and `Long` values (`order.total_amount`, `inventory.requested_quantity`,
   `payment.latency_ms`).
3. **In-memory testability via `LogCapture`**:
   The shared test fixture
   [`LogCapture`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/testFixtures/kotlin/com/observeit/observedapp/test/LogCapture.kt)
   captures Logback `ILoggingEvent` instances in memory, allowing unit and integration tests to assert
   `keyValues: Map<String, Any?>` (`event.keyValuePairs`) and `mdc: Map<String, String>` (`event.mdcPropertyMap`)
   independently without invoking the OpenTelemetry Java Agent.

## Considered Options

1. **Storing all structured attributes in MDC**:
   Placing all attributes into MDC was rejected because MDC coerces all values to `String`, breaking numeric `double`
   and `long` mappings in OpenSearch and requiring manual attribute eviction after every individual log statement to
   prevent cross-event pollution.
2. **Passing correlation identifiers manually via `.addKeyValue()`**:
   Supplying `order.id`, `customer.id`, and `checkout.scenario` manually on every log call was rejected because it
   duplicates boilerplate across every service method and fails to correlate log events emitted by framework filters,
   interceptors, or unhandled exception handlers.
3. **Unstructured string-interpolated logs**:
   Relying on traditional message string interpolation (`logger.info("Order {} paid in {}ms", id, latency)`) was
   rejected because unstructured text requires fragile regex/grok parsing and cannot be queried or aggregated by field
   in OpenSearch.

## Consequences

- **Positive**: Clear guideline for developers (request/scenario-wide correlation belongs in `CorrelationContext` /
  MDC; single-event attributes and typed numbers belong in `.addKeyValue()`); both metadata streams merge seamlessly
  into `attributes.*` in the **SS4O Log Index** via the OpenTelemetry Logback bridge while remaining independently
  testable in memory via `LogCapture`.
- **Trade-offs / Constraints**: MDC is thread-local, requiring virtual-thread tasks and servlet filters to strictly
  clear `CorrelationContext` in `finally` blocks; any asynchronous child tasks executed within a request would require
  explicit MDC propagation.

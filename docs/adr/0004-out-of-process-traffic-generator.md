# 0004. Out-of-Process Traffic Generator Service

**Status:** Accepted

## Context

An observability reference system is instructive only when telemetry flows continuously across realistic normal,
warning, and failure scenarios (`INFO`, `WARN`, `ERROR`), and when developers can interactively trigger specific
workflows or adjust workload intensity while observing dashboards in OpenSearch Dashboards.

Generating synthetic workload through an in-process `@Scheduled` loop inside the **Observed App** would bypass the HTTP
servlet filter chain
([`MdcCorrelationFilter`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/correlation/api/MdcCorrelationFilter.kt),
[`OrderMdcInterceptor`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/api/OrderMdcInterceptor.kt),
[`OrderControllerAdvice`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/api/OrderControllerAdvice.kt)),
entangle synthetic load generation with core application logic, and preclude demonstrating cross-service telemetry
correlation (`service: traffic-generator` → `service: observed-app`) and distributed trace context propagation across
network boundaries.

## Decision

We implement a dedicated, out-of-process **Traffic Generator** Spring Boot service driving the **Observed App** over
HTTP:

1. **Dedicated `traffic-generator` service**:
   Deployed as an independent container in
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) sharing the same
   Kotlin, Gradle convention plugin, and Paketo OpenTelemetry Java Agent configuration
   (`OTEL_SERVICE_NAME=traffic-generator`), interacting with the **Observed App** strictly over HTTP via
   [`RestObservedAppClient`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/infrastructure/RestObservedAppClient.kt).
2. **Weighted continuous background workload on Java 25 virtual threads**:
   [`TrafficWorkerCoordinator`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/infrastructure/TrafficWorkerCoordinator.kt)
   manages virtual-thread workers (`Thread.ofVirtual()`) tied to Spring's `SmartLifecycle` and coordinated by a
   lock-free `AtomicReference<CoordinatorState>` state machine. Workers sample from the 7
   [`CheckoutScenario`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/CheckoutScenario.kt)
   paths (`HAPPY_PATH`, `LOW_STOCK_WARNING`, `OUT_OF_STOCK`, `PAYMENT_DECLINED`, `PAYMENT_GATEWAY_TIMEOUT`,
   `BUYER_CANCELLATION`, `INVALID_STATE_TRANSITION`) using discrete inverse-transform sampling via
   [`WeightedScenarioSelector`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/WeightedScenarioSelector.kt)
   and execute multi-step **Order** lifecycles via
   [`CheckoutScenarioRunner`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/domain/CheckoutScenarioRunner.kt)
   with realistic inter-step timing jitter via
   [`JitterStepPacer`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/infrastructure/JitterStepPacer.kt).
3. **Interactive runtime control REST API**:
   [`TrafficController`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/main/kotlin/com/observeit/trafficgenerator/traffic/api/TrafficController.kt)
   exposes REST endpoints (`/api/traffic/trigger/{scenario}` for deterministic single-scenario execution used in
   [`scripts/verify-m1.sh`](https://github.com/PawelBalcerek/observe-it/blob/main/scripts/verify-m1.sh),
   `/api/traffic/pause`, `/api/traffic/resume`, `/api/traffic/rate`, and `/api/traffic/status`), allowing developers to
   control traffic dynamically without restarting containers.

## Consequences

- **Positive**: Emits authentic client-to-server HTTP traffic and multi-service telemetry (`service: traffic-generator`
  and `service: observed-app` correlated by `order.id`, `customer.id`, and `checkout.scenario` in the
  **SS4O Log Index**); reuses Gradle convention plugins, testing fixtures, and OpenTelemetry Java Agent provisioning
  across both services; establishes a clean foundation for Milestone 3 distributed trace propagation across process
  boundaries.
- **Trade-offs & Future Evolution**: Requires running a second Spring Boot JVM container (`mem_limit: 512m` in
  `docker-compose.yml`) alongside the **Observed App**. Because the **Traffic Generator** interacts with the
  **Observed App** solely over its public REST API, dedicated load-testing engines (such as k6 or Gatling) can be
  introduced alongside or in place of it in a future ADR if high-throughput stress testing becomes necessary.

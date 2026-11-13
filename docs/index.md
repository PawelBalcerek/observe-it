# Observe-It Documentation

Observe-It is an open-source observability reference system and local sandbox for Kotlin/Spring Boot backend
applications using OpenTelemetry and OpenSearch. Rather than a toy "hello world" or a tutorial that duplicates vendor
documentation, it pairs a realistic multi-service system—an **Observed App** processing customer **Order** workflows and
a continuous **Traffic Generator** driving normal, warning, and failure **Checkout Scenario** paths across every **Order
State**—with OpenTelemetry instrumentation and both an **SS4O Log Index** and an **SS4O Metrics Index** in OpenSearch.

## System at a Glance

```mermaid
flowchart LR
  TG["Traffic Generator (:8081)"] -->|"HTTP REST + X-Checkout-Scenario"| OA["Observed App (:8080)"]
  TG -.->|"OTLP gRPC (Java Agent)"| OC["OpenTelemetry Collector (:4317)"]
  OA -.->|"OTLP gRPC (Java Agent)"| OC
  OC -->|"SS4O Logs & Metrics Pipelines"| OS["OpenSearch (:9200)"]
  OSD["OpenSearch Dashboards (:5601)"] -->|"Discover & Dashboards"| OS
```

## Quick Start

Prerequisites: JDK 25 (or Gradle toolchain auto-provisioning) and Docker with Compose (~3 GB RAM).

Build the container images (with the Paketo OpenTelemetry Java Agent baked in) and start the stack:

```bash
make up
```

Or run the underlying Gradle and Docker Compose commands directly:

```bash
./gradlew bootBuildImage
docker compose up -d
```

Once all containers are healthy, open OpenSearch Dashboards at `http://localhost:5601`, navigate to **Discover** to
inspect live structured logs in `ss4o_logs-*` (`All Order Logs`, `Warnings & Business Rejections`, `Gateway Timeouts &
Stack Traces`, `Checkout Scenario Explorer`) and metrics in `ss4o_metrics-*` (`All Metrics`, `Domain Order Metrics`), or
open **Dashboards** → `Observe-It Metrics Overview`. For a step-by-step walkthrough on triggering scenarios and
exploring telemetry, follow the [Checkout Scenarios & Dashboards Walkthrough](./guides/exploring-telemetry.md).

To stop the stack and remove volumes:

```bash
make reset
```

## Guided Reading Path

### Narrative Guides

Explore living narrative and operational guides in the [Guides Overview](./guides/index.md):

- [Observability Stack & Telemetry Pipeline](./guides/observability-stack.md) — Container topology, Paketo
  OpenTelemetry Java Agent buildpack, OpenTelemetry Collector pipeline, and OpenSearch bootstrap.
- [Structured Logging in Kotlin & Spring Boot](./guides/structured-logging.md) — SLF4J 2 fluent key-value logging,
  scoped MDC correlation, Logback OTLP bridge, SS4O document normalization, and in-memory unit testing.
- [Metrics & OpenSearch Dashboards in Kotlin & Spring Boot](./guides/metrics-and-dashboards.md) — Auto-instrumented
  JVM/HTTP metrics vs. domain metric ports, cardinality boundaries, DELTA temporality, SS4O metrics normalization, and
  `MetricCapture` tests.
- [Checkout Scenarios & Dashboards Walkthrough](./guides/exploring-telemetry.md) — Seven Checkout Scenario paths,
  interactive Traffic Generator REST API, and OpenSearch Dashboards / DQL investigation workflows.

### Architecture Decision Records

Architecture Decision Records (ADRs) capture point-in-time, append-only architectural choices and their rationale,
trade-offs, and consequences. See the [ADR Index](./adr/index.md) for more.

## Observability Pillars & Roadmap

| Milestone       | Pillar               | Status   | Scope & Capabilities                                                                                                                                                                                                                                                                                                |
| :-------------- | :------------------- | :------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Milestone 1** | Structured Logging   | Complete | SLF4J 2 fluent `.addKeyValue()` API, scoped MDC correlation (`checkout.scenario`, `order.id`, `customer.id`), zero-code OpenTelemetry Java Agent log bridge, OpenTelemetry Collector `ss4o` export, OpenSearch `observe-it-logs-pipeline` normalization, and pre-bootstrapped OpenSearch Dashboards saved searches. |
| **Milestone 2** | Metrics & Dashboards | Current  | Auto-instrumented JVM/HTTP metrics, pure `domain` metric ports (`OrderMetrics`, `TrafficMetrics`) over `opentelemetry-api`, 1-second `DELTA` OTLP export, `observe-it-metrics-pipeline` and `observe-it-ss4o-metrics`, `MetricCapture` in-memory tests, and the `Observe-It Metrics Overview` dashboard.            |
| **Milestone 3** | Distributed Tracing  | Planned  | End-to-end W3C Trace Context propagation across the **Traffic Generator** and **Observed App**, custom domain spans, and cross-signal trace-to-log correlation.                                                                                                                                                     |

## Official Upstream References

Observe-It focuses on how these tools fit together in a production-style Kotlin/Spring Boot system, delegating vendor
fundamentals to canonical upstream documentation alongside core repository entry points:

- [OpenTelemetry Java Zero-Code Instrumentation](https://opentelemetry.io/docs/zero-code/java/agent/) — Java Agent
  configuration, system properties, and supported libraries.
- [OpenTelemetry Collector Documentation](https://opentelemetry.io/docs/collector/) — Receivers, processors
  (`memory_limiter`, `batch`), and exporters.
- [Simple Schema for Observability (SS4O)](https://docs.opensearch.org/latest/observing-your-data/ss4o/) — OpenSearch
  schema conventions for `ss4o_logs-*` indices.
- [OpenSearch Dashboards Query Language (DQL)](https://docs.opensearch.org/latest/dashboards/dql/) — Filtering and
  querying structured fields in Discover.
- [SLF4J 2 Fluent Logging API](https://www.slf4j.org/manual.html#fluent) — Structured `.atInfo().addKeyValue(...)`
  logging on the JVM.
- [Observe-It Repository README](https://github.com/PawelBalcerek/observe-it/blob/main/README.md) — Self-contained
  repository front door, 5-minute Quick Start, and codebase navigation map.
- [Contributing & Git Workflow](https://github.com/PawelBalcerek/observe-it/blob/main/CONTRIBUTING.md) — Issue-first
  workflow, branch conventions, and quality gates.

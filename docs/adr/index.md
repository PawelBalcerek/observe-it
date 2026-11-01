# Architecture Decision Records

Architecture Decision Records (ADRs) capture point-in-time architectural commitments and their rationale, trade-offs,
and consequences. In Observe-It, ADRs are append-only: when an architectural choice evolves, we record a new ADR that
supersedes the earlier one rather than rewriting historical records. In contrast, narrative guides in `docs/guides/`
are living documents updated in place to reflect the current state of `main`.

## What Qualifies as an ADR

We evaluate candidate architecture decisions against a 3-part test:

1. **Hard to reverse**: Architectural commitments that require substantial effort, migrations, or breaking changes to
   undo once built.
2. **Surprising without context**: Choices that might seem unintuitive, non-standard, or needlessly constrained to an
   outside observer without understanding the operational and design constraints.
3. **Result of a real trade-off**: Deliberate balance between competing architectural attributes (such as developer
   ergonomics vs. vendor coupling, or local sandbox resource footprints vs. production parity).

## Decision Log

| ADR                                                                      | Title                                                                     | Status   | Summary                                                                                                                                              |
| :----------------------------------------------------------------------- | :------------------------------------------------------------------------ | :------- | :--------------------------------------------------------------------------------------------------------------------------------------------------- |
| [ADR-0001](./0001-zero-code-otel-javaagent-and-logback-bridge.md)        | Zero-Code OpenTelemetry Java Agent and Logback OTLP Bridge                | Accepted | Attach the OpenTelemetry Java Agent dynamically via Paketo buildpacks with zero compile-time dependencies, bridging SLF4J 2 and MDC events to OTLP.  |
| [ADR-0002](./0002-otel-collector-ss4o-and-opensearch-ingest-pipeline.md) | Direct OpenTelemetry Collector ss4o Export and OpenSearch Ingest Pipeline | Accepted | Export OTLP logs directly from the Collector to OpenSearch in ss4o mode and normalize top-level fields using an OpenSearch Painless ingest pipeline. |
| [ADR-0003](./0003-slf4j-fluent-key-values-and-scoped-mdc-correlation.md) | SLF4J 2 Fluent Key-Value API and Scoped MDC Correlation                   | Accepted | Separate per-event domain attributes via fluent SLF4J 2 key-value pairs from ambient request and scenario correlation in MDC scopes.                 |
| [ADR-0004](./0004-out-of-process-traffic-generator.md)                   | Out-of-Process Traffic Generator Service                                  | Accepted | Drive synthetic Checkout Scenario traffic across the Observed App from an independent virtual-thread Spring Boot service with interactive controls.  |
| [ADR-0005](./0005-bounded-in-memory-order-repository.md)                 | Bounded Lock-Free In-Memory Order Repository                              | Accepted | Store Order state in an in-memory ConcurrentHashMap with FIFO queue eviction capped at 10,000 orders to eliminate database overhead in Milestone 1.  |

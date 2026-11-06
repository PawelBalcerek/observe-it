# Narrative Guides

Observe-It provides living narrative and operational guides explaining both the foundational production patterns of
modern telemetry pipelines and how Observe-It implements them locally using Kotlin, Spring Boot, OpenTelemetry, and
OpenSearch.

Unlike append-only [Architecture Decision Records](../adr/index.md), narrative guides are living documents updated in
place as the codebase evolves so they always reflect the current state of `main`.

## Guide Authoring Structure

Every guide follows a structured 3-step progression:

1. **General Pattern First (Domain-Agnostic)**: Explains the transferable engineering or observability pattern first—the
   problem it solves, how it functions in production architectures, and general rules of thumb.
2. **External Sources & Ecosystem Practice**: Cites authoritative upstream specifications, official manuals, or
   reference architectures demonstrating ecosystem consensus.
3. **In Observe-It**: Walks through Observe-It's concrete reference implementation using the **Order** domain,
   reproducible configuration, and targeted test suites.

## Guides Inventory

| Guide                                                                   | Pillar             | Status  | Summary                                                                                                                                                |
| :---------------------------------------------------------------------- | :----------------- | :------ | :----------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Observability Stack & Telemetry Pipeline](./observability-stack.md)    | Telemetry Pipeline | Current | Container topology, Paketo OpenTelemetry Java Agent buildpack, OpenTelemetry Collector pipeline, and deterministic OpenSearch bootstrap.               |
| [Structured Logging in Kotlin & Spring Boot](./structured-logging.md)   | Structured Logging | Current | SLF4J 2 fluent `.addKeyValue()` API, scoped MDC correlation, Logback OTLP bridge, SS4O document anatomy, and in-memory verification with LogCapture.   |
| [Checkout Scenarios & Dashboards Walkthrough](./exploring-telemetry.md) | System & Workload  | Current | Seven Checkout Scenario paths across Order States, interactive Traffic Generator REST API, and OpenSearch Dashboards / DQL investigation.              |

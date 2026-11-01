# 0001. Zero-Code OpenTelemetry Java Agent and Logback OTLP Bridge

**Status:** Accepted

## Context

Both the **Observed App** and **Traffic Generator** must emit rich structured logs—including SLF4J 2 key-value pairs,
MDC correlation attributes, source code coordinates, and W3C trace context—to the OpenTelemetry Collector without
polluting domain and application code with vendor SDK dependencies or sacrificing human-readable local console output
during local development (`docker compose logs`).

Coupling application source code to vendor-specific OpenTelemetry Java SDK dependencies complicates dependency upgrades,
entangles business logic with observability plumbing, and increases cognitive overhead for contributors writing domain
logic. At the same time, formatting console logs as raw JSON strings degrades local developer experience when inspecting
logs in the terminal.

## Decision

We adopt zero-code OpenTelemetry Java Agent bytecode instrumentation paired with an out-of-band Logback OTLP gRPC
bridge:

1. **Zero OpenTelemetry classpath dependencies**: Application modules depend exclusively on standard SLF4J 2 and
   Logback APIs provided by `spring-boot-starter-web`.
   [`ClasspathVerificationTest`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/test/kotlin/com/observeit/observedapp/ClasspathVerificationTest.kt)
   in `observed-app` and
   [`traffic-generator`](https://github.com/PawelBalcerek/observe-it/blob/main/traffic-generator/src/test/kotlin/com/observeit/trafficgenerator/ClasspathVerificationTest.kt)
   strictly enforces that no `io.opentelemetry.*` classes or JARs exist on the compile or runtime classpath.
2. **Buildpack agent injection**:
   [`build-logic/conventions/src/main/kotlin/conventions/spring.gradle.kts`](https://github.com/PawelBalcerek/observe-it/blob/main/build-logic/conventions/src/main/kotlin/conventions/spring.gradle.kts)
   configures Cloud Native Buildpacks (`docker.io/paketobuildpacks/opentelemetry`) to layer the OpenTelemetry Java Agent
   into container images at build time (`BP_OPENTELEMETRY_ENABLED=true`, `BP_JVM_VERSION=25`).
3. **Out-of-band Logback-to-OTLP bridge**:
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) activates the Java
   Agent at container runtime (`OTEL_JAVAAGENT_ENABLED=true`, `OTEL_LOGS_EXPORTER=otlp`, `OTEL_TRACES_EXPORTER=none`,
   `OTEL_METRICS_EXPORTER=none`, `OTEL_EXPORTER_OTLP_PROTOCOL=grpc`) along with runtime Logback capture flags:
   - `OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_MDC_ATTRIBUTES=*`
   - `OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_KEY_VALUE_PAIR_ATTRIBUTES=true`
   - `OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_CODE_ATTRIBUTES=true`
   - `OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_LOG_ATTRIBUTES=true`
   while maintaining human-readable console output (`[%X{trace_id:-},%X{span_id:-}] [%X{order.id:-}]` in
   `application.yml`, verified by
   [`LoggingFormatTest`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/integration/kotlin/com/observeit/observedapp/LoggingFormatTest.kt)).
4. **Health check probe bypass**: Container health check probes execute
   [`HealthCheck`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/HealthCheck.kt)
   with `-Dotel.javaagent.enabled=false` so short-lived probe JVMs avoid agent startup overhead and do not emit
   spurious telemetry.

## Considered Options

1. **JSON console encoder (`logstash-logback-encoder` / Spring Boot Structured Logging) with log scraper**:
   Formatting console logs as JSON and scraping `stdout` (e.g., via Filebeat or Fluent Bit) was rejected. Scraping
   `stdout` couples telemetry transport to console formatting, renders `docker compose logs` unreadable for humans
   debugging in the terminal, and requires fragile downstream parsing to reassemble multiline stack traces.
2. **Compile-time `opentelemetry-logback-appender-1.0` / Spring Boot OpenTelemetry starter**:
   Adding compile-time OpenTelemetry starters or appenders directly to application dependencies was rejected for
   Milestone 1. Zero-code bytecode instrumentation proves that standard SLF4J 2 APIs alone suffice to produce complete
   OTLP structured log records without coupling application build files to OpenTelemetry SDK lifecycles. (When custom
   domain metrics or trace spans are introduced in future milestones, a future ADR will evaluate adding
   `opentelemetry-api` where needed.)

## Consequences

- **Positive**: Clean architectural separation between business code (pure SLF4J 2) and telemetry export; simultaneous
  human-readable terminal logs on `stdout` and lossless OTLP/gRPC structured telemetry sent to the Collector; uniform
  agent provisioning across both services via a single Gradle convention plugin.
- **Trade-offs / Constraints**: Relies on `OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_*` environment variables
  governed by the upstream OpenTelemetry Java Agent; container health check commands must explicitly include
  `-Dotel.javaagent.enabled=false` because the Paketo buildpack injects agent flags for all container JVM processes by
  default.

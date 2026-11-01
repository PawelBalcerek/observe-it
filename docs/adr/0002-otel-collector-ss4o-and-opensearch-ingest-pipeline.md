# 0002. Direct OpenTelemetry Collector ss4o Export and OpenSearch Ingest Pipeline

**Status:** Accepted

## Context

Log records exported over OTLP by the OpenTelemetry Java Agent and received by the OpenTelemetry Collector must be
indexed into the OpenSearch **SS4O Log Index** (`ss4o_logs-observe-it-local`) with clean, query-friendly top-level
fields (`message`, `level`, `logger`, `service`, `version`, `env`, `exception`) and explicit field mappings (`keyword`,
`double`, `long`, `date_nanos`). This enables OpenSearch Dashboards Discover views, saved searches, and DQL queries to
work out of the box without requiring users to navigate deeply nested OTel-internal structures (`body`, `severity.text`,
`instrumentationScope.name`, `resource['service.name']`, `attributes['exception.stacktrace']`).

Simultaneously, the local Docker Compose environment must remain lightweight enough to run comfortably on developer
laptops alongside two JVM services and OpenSearch within a 3 GB memory footprint.

## Decision

We export logs directly from the OpenTelemetry Collector to OpenSearch using the `ss4o` mode and normalize document
structure at index time via an OpenSearch Painless ingest pipeline:

1. **Direct Collector `opensearch` exporter in `ss4o` mode**:
   [`docker/otel-collector/config.yaml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/otel-collector/config.yaml)
   configures the `opentelemetry-collector-contrib` `opensearch` exporter with `mapping.mode: ss4o`,
   `dataset: observe-it`, and `namespace: local`, exporting batches directly to the `ss4o_logs-observe-it-local` index
   fronted by `memory_limiter` and `batch` processors.
2. **OpenSearch `observe-it-logs-pipeline` Painless ingest processor**:
   [`docker/opensearch/ingest-pipeline.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/ingest-pipeline.json)
   defines an ingest pipeline registered as the `default_pipeline` on the `ss4o_logs-*` index template in
   [`docker/opensearch/index-template.json`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/index-template.json).
   At index time, a Painless script promotes and deduplicates top-level fields (`body` → `message`, `severity.text` →
   `level`, `instrumentationScope.name` → `logger`, `resource['service.name']` → `service`,
   `resource['service.version']` → `version`, `resource['deployment.environment.name']` → `env`,
   `attributes['exception.stacktrace']` → `exception`) and strips redundant fields (`attributes.data_stream`,
   `attributes['exception.message']`, and `attributes['exception.type']`).
3. **Deterministic startup ordering via one-shot bootstrap container**:
   [`docker/opensearch/bootstrap.sh`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/bootstrap.sh)
   runs in a dedicated one-shot `opensearch-bootstrap` container to register the ingest pipeline, `ss4o_logs-*` index
   template, and OpenSearch Dashboards
   [`saved-objects.ndjson`](https://github.com/PawelBalcerek/observe-it/blob/main/docker/opensearch/saved-objects.ndjson).
   In [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml), `otel-collector`
   waits for `opensearch-bootstrap` to complete (`condition: service_completed_successfully`), ensuring no log record
   is indexed before the index template and default pipeline are active.

## Considered Options

1. **OpenSearch Data Prepper (`otel-collector` → `data-prepper` → `opensearch`)**:
   Adding Data Prepper was rejected because it introduces an extra JVM container requiring 512 MB to 1 GB of memory
   and an additional network hop for transformations that OpenSearch ingest nodes handle natively with zero container
   overhead.
2. **OpenTelemetry Collector OTTL `transform` processor alone**:
   Performing all transformations inside the Collector before export was rejected. The Collector's `opensearch`
   exporter in `ss4o` mode serializes standard OTel LogRecord fields (`Body`, `SeverityText`, `InstrumentationScope`,
   `Resource`) into fixed SS4O JSON locations after pipeline processors execute. Reshaping the final indexed JSON
   document (such as renaming `body` to `message` and flattening `severity.text` to `level` while deduplicating) must
   occur after SS4O serialization, which is cleanly accomplished inside OpenSearch's ingest pipeline.
3. **Raw un-normalized SS4O documents**:
   Relying purely on OpenSearch dynamic mapping was rejected because raw SS4O output leaves `body`, `severity.text`,
   `instrumentationScope.name`, and duplicate `exception.*` attributes in nested locations that complicate query
   authoring and clutter OpenSearch Dashboards Discover columns.

## Consequences

- **Positive**: Eliminates 512 MB+ of container memory overhead by omitting Data Prepper; guarantees deterministic
  schema mappings (`keyword`, `long`, `double`, `date_nanos`) and ergonomic top-level fields (`service`, `level`,
  `logger`, `message`, `exception`) alongside nested domain attributes (`attributes.order.*`, `attributes.payment.*`,
  `attributes.inventory.*`).
- **Trade-offs / Constraints**: Document normalization logic is codified in an OpenSearch Painless script coupled to
  the SS4O exporter's serialized JSON structure; the Collector must await `opensearch-bootstrap` completion before
  shipping logs to prevent early records from bypassing pipeline processing.

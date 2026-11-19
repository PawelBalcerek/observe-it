# 0010. MongoDB Order Persistence, TTL Retention, and Testcontainers Integration Testing

**Status:** Accepted

## Context

Supersedes [ADR-0005](0005-bounded-in-memory-order-repository.md).

In Milestone 1, **Order** aggregates were stored in `InMemoryOrderRepository` (a bounded `ConcurrentHashMap` with FIFO
eviction) to avoid database container overhead while establishing structured logging. By Milestone 4 (Database
Observability & Persistence), that ephemeral store leaves three gaps:

1. **No durable persistence or database I/O**: **Order** state is lost on container restart, and the **Observed App**
   emits no database driver telemetry.
2. **Continuous background workload requires bounded storage**: Because the **Traffic Generator** continuously creates
   and transitions **Order** documents, an unbounded MongoDB collection would grow indefinitely on disk within the local
   sandbox.
3. **Hermetic, fast test suites under a ~3 GB container budget**: Local Docker Compose must fit MongoDB alongside
   OpenSearch, OpenSearch Dashboards, the OpenTelemetry Collector, and both JVM services, while `./gradlew test` unit
   tests must remain 100% Docker-free and `./gradlew integrationTest` must exercise real MongoDB behavior without
   per-test container restarts.

## Decision

We replace `InMemoryOrderRepository` with a hexagonal MongoDB persistence adapter, TTL-based retention, a right-sized
local `mongodb` container, and a singleton Testcontainers integration harness:

1. **Direct `MongoTemplate` hexagonal adapter (`MongoOrderRepository`)**:
   The `order.domain` package (`Order`, `OrderLineItem`, `OrderState`, `OrderRepository`) remains 100% pure Kotlin with
   zero Spring Data or MongoDB imports.
   [`MongoOrderRepository`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/infrastructure/MongoOrderRepository.kt)
   in `order.infrastructure` implements `OrderRepository` by injecting `MongoTemplate` directly without a Spring Data
   `MongoRepository` proxy interface:
   - `save(order)` maps via `OrderDocument.from(order)` and calls `mongoTemplate.save(...)`, executing a single MongoDB
     `update` with `upsert: true` on `_id` for both creation and state transitions while refreshing `updatedAt`.
   - `findById(id)` calls `mongoTemplate.findById(id, OrderDocument::class.java)?.toDomain()`.
   - `count()` calls `mongoTemplate.estimatedCount(OrderDocument::class.java).toInt()`, issuing an O(1) collection
     metadata `count` command rather than an O(N) aggregation scan.
2. **Document mapping (`OrderDocument`), BSON `Decimal128`, and 1-hour TTL retention**:
   [`OrderDocument`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/infrastructure/OrderDocument.kt)
   maps to `@Document(collection = "orders")` with `@Field(targetType = DECIMAL128)` on
   `OrderLineItemDocument.unitPrice` (paired with `spring.data.mongodb.representation.big-decimal: decimal128` in
   [`application.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/resources/application.yml))
   for exact monetary precision. Bounded retention is enforced by an `@Indexed(name = "orders_updated_at_ttl_idx",
   expireAfter = "${order.repository.ttl:1h}")` TTL index on `updatedAt` created automatically at startup via
   `spring.data.mongodb.auto-index-creation: true`.
3. **Right-sized local Docker Compose `mongodb` container**:
   [`docker-compose.yml`](https://github.com/PawelBalcerek/observe-it/blob/main/docker-compose.yml) provisions a
   single-node `mongo:8.0.38` container (`mem_limit: 512m`, `--wiredTigerCacheSizeGB 0.25 --bind_ip_all`,
   `127.0.0.1:27017:27017`, `mongodb-data:/data/db`, and a `mongosh --quiet --eval "db.adminCommand('ping').ok"`
   healthcheck). `observed-app` depends on `mongodb` (`condition: service_healthy`) and connects via
   `SPRING_MONGODB_URI=mongodb://mongodb:27017/observe_it`, keeping total memory across all six long-running containers
   at ~3.25 GB.
4. **Singleton `@ServiceConnection` `MongoDBContainer` and `FakeOrderRepository`**:
   [`observed-app/build.gradle.kts`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/build.gradle.kts)
   declares `spring-boot-testcontainers` and `org.testcontainers:testcontainers-mongodb:2.0.5` strictly in
   `integrationImplementation`, keeping `runtimeClasspath` and `./gradlew test` 100% Docker-free.
   [`TestMongoConfiguration`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/integration/kotlin/com/observeit/observedapp/test/TestMongoConfiguration.kt)
   exposes a singleton `@ServiceConnection` `MongoDBContainer("mongo:8.0.38")` started once per test JVM, imported by
   [`IntegrationTest`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/integration/kotlin/com/observeit/observedapp/IntegrationTest.kt),
   which clears the `orders` collection before and after each test. Unit tests (`OrderServiceTest`,
   `OtelOrderMetricsTest`) use
   [`FakeOrderRepository`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/testFixtures/kotlin/com/observeit/observedapp/order/FakeOrderRepository.kt)
   from `src/testFixtures`.

## Considered Options

1. **Spring Data `MongoRepository` proxy interface**:
   Using a Spring Data `MongoRepository` interface behind `MongoOrderRepository` was rejected because it adds a shallow
   pass-through abstraction and triggers `io.opentelemetry.spring-data-1.8` to emit an extra `SpanKind.INTERNAL` proxy
   span between `order.<action>` and the MongoDB `CLIENT` span.
2. **MongoDB capped collections or manual FIFO eviction**:
   Capped collections were rejected because MongoDB rejects in-place updates that grow document size and disallows
   `deleteMany` cleanup during integration tests; in-app FIFO eviction was rejected because it does not survive restarts
   or multi-instance deployments.
3. **External relational database (PostgreSQL / JDBC / Flyway)**:
   Provisioning PostgreSQL with relational schema migrations was deferred because Milestone 4 focuses on document
   persistence and MongoDB wire-protocol observability without adding migration tooling overhead to the local sandbox.
4. **Per-test MongoDB container lifecycle or `@DirtiesContext`**:
   Starting a fresh container per test class was rejected because repeated container startup slows
   `./gradlew integrationTest` dramatically compared to a singleton `@ServiceConnection` container with per-test
   collection cleanup.

## Consequences

- **Good, because** **Order** state persists across **Observed App** restarts with exact BSON `Decimal128` monetary
  representation while `orders_updated_at_ttl_idx` bounds disk usage automatically under continuous **Traffic
  Generator** load.
- **Good, because** `order.domain` stays completely free of Spring Data annotations and `./gradlew test` remains
  sub-second and Docker-free via `FakeOrderRepository`, while `./gradlew integrationTest` exercises the real MongoDB
  driver against `mongo:8.0.38`.
- **Bad, because** `./gradlew integrationTest` and `./gradlew check` now require a local Docker daemon to launch the
  singleton Testcontainers `MongoDBContainer`.
- **Bad, because** adding the `mongodb` container increases the local Docker Compose memory footprint from ~2.75 GB to
  ~3.25 GB.
- **Neutral, because** `estimatedCount()` trades exact transactional count accuracy for O(1) metadata reads, which is
  ideal for the 1 Hz `order.repository.size` gauge.

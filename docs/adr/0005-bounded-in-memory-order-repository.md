# 0005. Bounded Lock-Free In-Memory Order Repository

**Status:** Accepted

## Context

In Milestone 1 (Structured Logging), the **Observed App** must persist **Order** aggregates across the multi-step HTTP
requests comprising each **Checkout Scenario** (`create` → `reserve` → `pay` → `ship` or `cancel` → `get`).

Because the **Traffic Generator** creates **Order** instances continuously in the background, an unbounded in-memory map
would eventually exhaust heap memory (`OutOfMemoryError` within the container's 512 MB memory limit). Conversely,
provisioning an external relational database container (such as PostgreSQL) in Milestone 1 would introduce
infrastructure overhead, schema migrations, and connection pool tuning before database metrics or distributed JDBC
tracing are within project scope.

## Decision

We back the domain `OrderRepository` port with a bounded, lock-free in-memory repository implementing FIFO eviction:

1. **Domain repository port (`OrderRepository`)**:
   [`OrderService`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderService.kt)
   depends strictly on the domain
   [`OrderRepository`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/domain/OrderRepository.kt)
   interface (`save`, `findById`, `count`), maintaining hexagonal isolation and confining storage mechanisms to the
   `infrastructure` package.
2. **Bounded lock-free `InMemoryOrderRepository` with FIFO eviction**:
   [`InMemoryOrderRepository`](https://github.com/PawelBalcerek/observe-it/blob/main/observed-app/src/main/kotlin/com/observeit/observedapp/order/infrastructure/InMemoryOrderRepository.kt)
   stores **Order** aggregates in a `ConcurrentHashMap<String, Order>` paired with a `ConcurrentLinkedQueue<String>`
   tracking insertion order. It uses lock-free concurrency instead of `synchronized` blocks, ensuring Java 25 virtual
   threads (`spring.threads.virtual.enabled=true`) are never pinned to carrier threads.
3. **Configurable retention capacity**:
   The repository caps stored orders at `order.repository.capacity` (defaulting to 10,000 orders). When new orders are
   inserted beyond capacity, the oldest order IDs are evicted in FIFO order—more than sufficient to retain in-flight
   **Checkout Scenario** executions while bounding heap usage well within the container's 512 MB heap limit.

## Considered Options

1. **External relational database container (PostgreSQL)**:
   Provisioning PostgreSQL in Milestone 1 was deferred rather than permanently rejected. Adding a database container,
   schema migrations, and connection pool tuning before database metrics or distributed JDBC tracing are in scope
   would add unnecessary operational overhead to the local 3 GB sandbox. Because `OrderService` depends only on the
   `OrderRepository` port, PostgreSQL can be added in a future milestone by introducing a new `infrastructure` adapter
   and superseding this ADR without invalidating [ADR-0004](./0004-out-of-process-traffic-generator.md).
2. **Unbounded in-memory `ConcurrentHashMap`**:
   Storing **Order** aggregates without an eviction bound was rejected because continuous background traffic from the
   **Traffic Generator** would eventually exhaust the container's 512 MB memory limit with an `OutOfMemoryError`.

## Consequences

- **Good, because** local development avoids database container overhead and Testcontainers startup delays in unit and
  integration tests.
- **Good, because** FIFO eviction at 10,000 entries bounds heap usage predictably under continuous traffic while
  lock-free primitives (`ConcurrentHashMap`, `ConcurrentLinkedQueue`) avoid pinning Java 25 virtual threads.
- **Bad, because** **Order** state is ephemeral (lost on container restart) and completed orders are evicted after
  10,000 subsequent orders.
- **Neutral, because** domain and API layers depend strictly on `OrderRepository`, allowing a persistent adapter to
  replace `InMemoryOrderRepository` later without touching business logic.

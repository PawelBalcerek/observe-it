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

## Consequences

- **Positive**: Zero database container overhead in Milestone 1; constant, predictable memory consumption under
  continuous background traffic; non-blocking virtual-thread safety without thread pinning; fast in-process unit and
  integration test execution without Testcontainers database startup delays.
- **Trade-offs & Future Superseding Path**: **Order** state is ephemeral (lost on container restart) and completed
  orders are eventually evicted after 10,000 subsequent orders. Because `OrderService` interacts with persistence purely
  through the `OrderRepository` port, introducing a persistent database (e.g., PostgreSQL to demonstrate connection
  pool metrics or JDBC trace spans in a future milestone) will require only adding a new `infrastructure` adapter and
  superseding ADR-0005, without modifying domain or API code and without invalidating
  [ADR-0004](./0004-out-of-process-traffic-generator.md).

# Architecture & Packaging Conventions

## 1. Feature + 3-Subpackage Hexagonal Layout (`api`, `domain`, `infrastructure`)

Organize code inside each service module by **feature package first**, and split each feature into three subpackages (`api`, `domain`, `infrastructure`):

```text
<root-package>
└── <feature>/
    ├── api/             ← Driving adapters: HTTP controllers, Request/Response DTOs, @RestControllerAdvice
    ├── domain/          ← Entities, value objects, ports (interfaces), @Service orchestrators
    └── infrastructure/  ← Driven adapters: persistence (@Repository), external clients, schedulers
```

### Subpackage Responsibilities & Dependency Direction

Dependencies point strictly inward toward `domain` (`api` → `domain` ← `infrastructure`):

1. **`<feature>/api` (Driving Adapters)**:
   - Contains HTTP controllers (`@RestController`), request/response DTOs, and HTTP exception handlers (`@RestControllerAdvice`).
   - **Thin adapters**: Translate transport requests into `domain` service calls and map domain results or exceptions into HTTP responses and status codes.
   - Delegate strictly to `@Service` orchestrators in `domain`; keep controllers decoupled from persistence ports (`Repository`), `MDC` manipulation, and domain structured logging.

2. **`<feature>/domain` (Domain Model & Use-Case Orchestration)**:
   - **Entities & Value Objects**: Pure Kotlin types that own state transitions, business invariants, and domain calculations. Keep entities free of framework and logging imports.
   - **Ports**: Pure Kotlin interfaces (e.g., repositories, gateways) defining what the domain requires from driven adapters.
   - **Application Services (`@Service`)**: Coordinate use cases by loading/persisting domain models through ports, updating diagnostic context (`MDC`), and emitting structured logs (`LoggingEventBuilder.addKeyValue`).
   - Keep `domain` free of transport and web imports (`jakarta.servlet.*`, `org.springframework.web.*`).

3. **`<feature>/infrastructure` (Driven Adapters)**:
   - Contains concrete implementations of `domain` ports (`@Repository` stores, HTTP client adapters, background workers).
   - Encapsulates storage data structures, framework configuration properties (`@Value`, `@ConfigurationProperties`), and external I/O behind the `domain` port interface.

## 2. Cross-Cutting Concerns

When a cross-cutting capability (such as request correlation or telemetry context) spans both transport filters and domain services, separate the **transport-agnostic context** from the **transport adapter**:

- **Context / Domain Support (`<concern>/`)**: Pure Kotlin types/functions (e.g., `MDC` attribute keys and context population helpers) with zero `jakarta.servlet.*` or `org.springframework.web.*` dependencies, safe for `domain` services to call directly.
- **Transport Adapter (`<concern>/api/` or `<concern>/infrastructure/`)**: Servlet filters (`OncePerRequestFilter`) or interceptors that extract transport headers, delegate to the pure context helper, and guarantee cleanup in a `finally` block.

## 3. Lock-Free Concurrency & Shared State

Prefer lock-free `java.util.concurrent` primitives over `synchronized` blocks and mutable non-thread-safe collections:

- Use `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `AtomicReference`, `LongAdder`, `AtomicLong`, and `AtomicBoolean` for shared in-memory state, caches, and counters.
- For bounded in-memory stores with eviction, combine `ConcurrentHashMap` (for lock-free reads) with `ConcurrentLinkedQueue` (tracking insertion order for eviction) or a concurrent cache rather than wrapping mutable collections (`LinkedHashMap`, `MutableList`) in `synchronized` blocks.

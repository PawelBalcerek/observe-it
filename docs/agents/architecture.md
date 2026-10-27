# Architecture & Packaging Conventions

## 1. Feature + 3-Subpackage Hexagonal Layout (`api`, `domain`, `infrastructure`)

Organize code inside each service module by **feature package first**, and split each feature into three subpackages (`api`, `domain`, `infrastructure`):

```text
<root-package>
├── api/                 ← Shared HTTP concerns: GlobalControllerAdvice (@RestControllerAdvice)
└── <feature>/
    ├── api/             ← Driving adapters: HTTP controllers, Request/Response DTOs
    ├── domain/          ← Entities, value objects, ports (interfaces), @Service orchestrators
    └── infrastructure/  ← Driven adapters: persistence (@Repository), external clients, schedulers
```

### Subpackage Responsibilities & Dependency Direction

Dependencies point strictly inward toward `domain` (`api` → `domain` ← `infrastructure`):

1. **`<feature>/api` & Shared `api` (Driving Adapters)**:
   - **Thin controllers (`@RestController`)**: Validate transport inputs, translate requests into `domain` service calls, and return HTTP responses and status codes. Delegate strictly to `@Service` orchestrators in `domain`; keep controllers decoupled from persistence ports (`Repository`), `MDC` manipulation, and domain structured logging.
   - **Boundary validation (`@Valid`)**: Validate all request inputs at the `api` boundary using Jakarta Bean Validation annotations (`@field:NotBlank`, `@field:NotEmpty`, `@field:Positive`, `@field:Valid`) and `@AssertTrue` methods on request DTOs for cross-field or format rules (e.g., single currency across items, valid ISO-4217 currency code) so `domain` receives already-valid data.
   - **DTO ↔ Domain mapping**: Keep mapping inside `api` via `RequestDto.toDomain*()` conversion methods and `ResponseDto.from(domain)` factory methods so `domain` never imports `api` DTOs.
   - **Global `ProblemDetail` error handling (`api/GlobalControllerAdvice`)**: Centralize `@RestControllerAdvice` in the shared root `api` package returning RFC 7807 `ProblemDetail` responses (attaching validation messages under an `errors` property on `400 Bad Request`). Keep advice handlers limited to exceptions that can actually be thrown, and statically import `HttpStatus` constants (`HttpStatus.BAD_REQUEST`).

2. **`<feature>/domain` (Domain Model & Use-Case Orchestration)**:
   - **Entities & Value Objects**: Pure Kotlin types that own state transitions, business invariants, and domain calculations. Use rich standard value types—`BigDecimal` for monetary amounts and `java.util.Currency` for ISO-4217 currencies—converting to primitives (`Double`, `String`) only at serialization or telemetry boundaries. Keep entities free of framework and logging imports.
   - **Ports**: Pure Kotlin interfaces (e.g., repositories, gateways) defining what the domain requires from driven adapters.
   - **Application Services (`@Service`)**: Coordinate use cases by loading/persisting domain models through ports, updating diagnostic context (`MDC`), and emitting structured logs (`LoggingEventBuilder.addKeyValue`).
   - Keep `domain` free of transport and web imports (`jakarta.servlet.*`, `org.springframework.web.*`, `jakarta.validation.*`).

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

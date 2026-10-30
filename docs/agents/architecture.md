# Architecture & Packaging Conventions

## 1. Feature + 3-Subpackage Hexagonal Layout (`api`, `domain`, `infrastructure`)

Organize code inside each service module by **feature package first**, and split each feature into three subpackages (`api`, `domain`, `infrastructure`):

```text
<root-package>
├── api/                 ← Shared HTTP concerns: GlobalControllerAdvice (@RestControllerAdvice)
├── domain/              ← Shared domain types reused across features (e.g. NotFoundException base class)
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
   - **DTO naming**: The canonical full representation of an entity is `<Entity>Response` (`OrderResponse`, returned by lookups). Action-specific payloads carry the action as a prefix: `<Action><Entity>Request` / `<Action><Entity>Response` (`CreateOrderRequest`, `CreateOrderResponse`). Nested DTOs follow their parent's prefix (`CreateOrderLineItemRequest`, `OrderLineItemResponse`).
   - **Global & feature `ProblemDetail` error handling (`api/GlobalControllerAdvice` & `<feature>/api/<Feature>ControllerAdvice`)**: Return RFC 7807 `ProblemDetail` responses (attaching validation messages under an `errors` property on `400 Bad Request`). Keep advice handlers limited to exceptions that can actually be thrown, and statically import `HttpStatus` constants (`BAD_REQUEST`).
     - **Handle shared base exceptions in `GlobalControllerAdvice`**: Handlers catch base exception types from the root `domain` package (`NotFoundException`, `ConflictException`, `UnprocessableEntityException`, `GatewayTimeoutException`); each feature exception extends the matching base (`OrderNotFoundException : NotFoundException`), so `domain` stays decoupled from `api` and new features reuse existing handlers.
     - **One path per concern**: Every client-error (4xx) handler logs at `WARN` through one private helper (`logClientWarning(message)`), every server-error (5xx) handler logs at `ERROR` with its cause through one private helper (`logServerError(message, exception)`), and every handler builds its response through one private factory (`problemDetail(status, detail)`).
     - **Feature-specific structured error logging (`<Feature>ControllerAdvice`)**: When a domain failure requires structured key-value telemetry (`error.type`, `inventory.*`, `payment.*`), handle the exception in `<feature>/api/<Feature>ControllerAdvice` (`@Order(HIGHEST_PRECEDENCE)`) to emit the structured `WARN` / `ERROR` log and return the `ProblemDetail`, so `domain` services never log and throw.

2. **`<feature>/domain` (Domain Model & Use-Case Orchestration)**:
   - **Entities & Value Objects**: Pure Kotlin types that own state transitions, business invariants, and domain calculations. Transition methods (`reserve()`, `pay()`, `ship()`, `cancel()`) self-enforce their state-machine invariants and throw the corresponding domain exception (`InvalidOrderStateTransitionException`) directly, keeping `@Service` orchestrators free of duplicate precondition checks. Use rich standard value types—`BigDecimal` for monetary amounts and `java.util.Currency` for ISO-4217 currencies—converting to primitives (`Double`, `String`) only at serialization or telemetry boundaries. Keep entities free of framework and logging imports.
   - **Domain Exceptions**: Declare feature domain exceptions as `data class`es carrying their structured domain attributes (`sku`, `paymentMethod`, `latencyMs`, `attemptedAction`) and extending the matching shared root `domain` base exception. Value equality on domain attributes lets unit tests assert thrown exceptions directly with `shouldThrow<T> { ... } shouldBe ExpectedException(...)`.
   - **Ports**: Pure Kotlin interfaces (e.g., repositories, gateways) defining what the domain requires from driven adapters.
   - **Application Services (`@Service`)**: Coordinate use cases by loading/persisting domain models through ports, updating diagnostic context (`MDC`), and emitting structured logs (`LoggingEventBuilder.addKeyValue`) on outcome paths. On failure paths, throw the domain exception without logging; controller advice owns failure logging.
   - Keep `domain` free of transport and web imports (`jakarta.servlet.*`, `org.springframework.web.*`, `jakarta.validation.*`).
   - **Shared root `domain`**: Place types reused across multiple features (exception base classes such as `NotFoundException`) in the root `domain` package.

3. **`<feature>/infrastructure` (Driven Adapters)**:
   - Contains concrete implementations of `domain` ports (`@Repository` stores, HTTP client adapters, simulated gateways, background workers).
   - Encapsulates storage data structures, framework configuration properties (`@Value`, `@ConfigurationProperties`), and external I/O behind the `domain` port interface.
   - **Non-blocking delays & monotonic elapsed time**: Schedule artificial or simulated delays with `CompletableFuture.delayedExecutor(delayMs, MILLISECONDS)` and measure elapsed duration with `kotlin.time.measureTime` (`TimeSource.Monotonic`), never `Thread.sleep` or wall-clock `System.currentTimeMillis()` (enforced by Detekt `ForbiddenMethodCall`).

## 2. Cross-Cutting Concerns

When a cross-cutting capability (such as request correlation or telemetry context) spans both transport filters and domain services, separate the **transport-agnostic context** from the **transport adapter**:

- **Context / Domain Support (`<concern>/`)**: Pure Kotlin types/functions (e.g., `MDC` attribute keys and context population helpers) with zero `jakarta.servlet.*` or `org.springframework.web.*` dependencies, safe for `domain` services to call directly.
- **Transport Adapter (`<concern>/api/` or `<concern>/infrastructure/`)**: Servlet filters (`OncePerRequestFilter`) or interceptors that extract transport headers, delegate to the pure context helper, and guarantee cleanup in a `finally` block.
- **`MDC` is write-only telemetry context**: Populate `MDC` so logs carry correlation attributes, and pass every business or transport value (scenario, order id, outbound headers) as an explicit parameter. Production code never reads a value back out of `MDC` to drive behaviour.

## 3. Lock-Free Concurrency & Shared State

Prefer lock-free `java.util.concurrent` primitives over `synchronized` blocks and mutable non-thread-safe collections:

- Use `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `AtomicReference`, `LongAdder`, `AtomicLong`, and `AtomicBoolean` for shared in-memory state, caches, and counters.
- For bounded in-memory stores with eviction, combine `ConcurrentHashMap` (for lock-free reads) with `ConcurrentLinkedQueue` (tracking insertion order for eviction) or a concurrent cache rather than wrapping mutable collections (`LinkedHashMap`, `MutableList`) in `synchronized` blocks.

## 4. Static Imports

Statically import members whose bare name still reads unambiguously at the call site, in production and test code alike:

- Enum constants and standard library/framework constant fields: `CREATED`, `NOT_FOUND`, `APPLICATION_JSON`, `HIGHEST_PRECEDENCE`, `SECONDS`, `INFO`.
- Fixture, assertion, and matcher functions: `aCreateOrderRequest()`, `shouldBeNotFoundError(...)`, `shouldBe`.

Keep the qualifier where it carries meaning the bare name loses:

- Generic factory or constant names: `Currency.getInstance(...)`, `UUID.randomUUID()`, `BigDecimal.ZERO`, `Arguments.of(...)`, `LoggerFactory.getLogger(...)`.
- Functions on a Kotlin `object` acting as a namespace: `CorrelationContext.populateOrderCorrelation(...)`, `MDC.get(...)`.
- Names that would collide with another import or member in the same file.

## 5. Kotlin Idioms & Visibility

- **Exhaustive `when` expressions**: Branch on enums and sealed types with an exhaustive `when` listing every entry explicitly and without an `else` branch (enforced by Detekt `ElseCaseInsteadOfExhaustiveWhen`).
- **`when` over `if` / `else if` chains**: Branch on a single subject (status code, type, enum) with a `when` expression.
- **`private companion object` by default**: Mark every `companion object` holding class-internal constants `private companion object`. Widen a companion object only when it exposes factory methods (`ResponseDto.from(domain)`) or JUnit `@FieldSource` providers.
- **Named constants, grouped by meaning**: Extract repeated literals in a production class or test fixture into named constants; when a class or feature holds several constants of different kinds (telemetry keys, states, error types), group them in nested `object` namespaces (`Key.ORDER_ID`, `OrderState.PAID`, `ErrorType.OUT_OF_STOCK`), and extract telemetry constants shared across multiple classes in a feature into a feature `domain` object (`OrderTelemetry`).
- **No intermediate builder `@Bean`s**: When a `@Configuration` class builds a framework client (`RestClient`), construct it directly in one `@Bean` method rather than exposing an intermediate builder `@Bean` (`RestClient.Builder`) consumed only by that method.
- **Fix before suppressing**: Change the code to satisfy a Detekt finding; keep an `@Suppress` only when the rule cannot fit the case (e.g. a `when` over the open-ended `HttpStatusCode`), scoped to the smallest declaration.

## 6. Dead Code & Sources

- **Every declaration has a caller**: Each class, function, property, `@Bean`, and fixture helper a PR adds is called from `main` (or, for `testFixtures`, from a test) in the same PR. Add a declaration in the ticket that first uses it, not ahead of time. Detekt catches unused *private* members; PR code review checks public and internal declarations for callers.
- **KDoc cites verifiable sources**: KDoc explaining a non-trivial algorithm or framework mechanism (sampling, lifecycle phases, virtual threads) names the technique correctly and links a primary source (Javadoc, JEP, Spring reference, paper) by URL.

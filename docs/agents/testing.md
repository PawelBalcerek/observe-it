# Testing Conventions

## 1. Test Separation & Source Sets (`test`, `integration`, `testFixtures`)

Each service module configures three test source sets via `conventions.spring`:

- **Shared Test Fixtures (`src/testFixtures/kotlin`)**:
  - Enabled via Gradle's `java-test-fixtures` plugin and shared across both unit and integration tests.
  - Houses domain/DTO `*Fixtures` (Object Mothers), reusable HTTP `*ApiFixture` clients, `ProblemDetail` assertion helpers (`ProblemDetailFixtures`), and log capture helpers (`LogCapture`).
  - Keep internal helper properties/functions `private` and prune unused fixture helpers.
- **Unit Tests (`src/test/kotlin`)**:
  - Fast, isolated, and decoupled from Spring container startup or network I/O.
  - Verify domain state transitions and invariants, service orchestration and structured log emission, adapter mechanics (such as capacity eviction and concurrency), and filter edge cases.
  - Verify `@RestControllerAdvice` HTTP status codes, `ProblemDetail` payloads, and error telemetry end-to-end in integration tests rather than unit tests.
- **Integration Tests (`src/integration/kotlin`)**:
  - Configured via `com.coditory.integration-test` (`./gradlew integrationTest`, wired into `./gradlew check`).
  - Extend the `IntegrationTest` base class (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).
  - Verify the wired HTTP contract (including request validation `ProblemDetail` responses) and emitted telemetry as a **black box** through `*ApiFixture` / `RestTestClient` and `LogCapture`.
  - Interact strictly through the HTTP interface and captured logs—keep each integration test scoped to its feature's endpoints, and test cross-cutting servlet filters once in dedicated filter/health tests rather than re-testing filter cleanup across every endpoint test.
  - **Ascending HTTP status order**: Within each endpoint integration test class, order test methods by ascending HTTP response status (`2xx` → `4xx` → `5xx`, e.g., `200`/`201` → `400` → `404` → `409` → `422` → `504`).
  - **Outbound client adapter tests**: Group test methods by client method (in the port's declaration order), then by ascending HTTP status within each group. Cover every status the downstream service returns for that call, plus exactly one unhandled status (`500`) per client method.
- **One test class per subject**: Do not test the same class (`CheckoutScenarioRunner`) in both a unit test and an integration test. When a service is already exercised directly by an integration test, skip a duplicate unit test for it. Skip tests that only restate a declaration (enum entries, constant tables); test the invariant a declaration encodes (e.g., scenario weights sum to 1) where the code relying on it lives.

## 2. Fakes over Mocks

- Wire **real in-memory adapters** (fakes) into service unit tests rather than mocking domain ports.
- Do not use mocking frameworks (Mockito, MockK) for domain repositories or collaborators.

## 3. Minimal-Override `*Fixtures` with Self-Describing Dummy Defaults

Define Object Mother factory functions in `src/testFixtures/kotlin` using Kotlin default arguments:

- **Self-describing dummy defaults**: Default string values match their property name (`id: String = "id"`, `name: String = "name"`), constrained string/value types use valid self-explanatory defaults (`currency: String = "USD"`, `currency: Currency = Currency.getInstance("USD")`), and numeric/collection properties use simple constant defaults (`quantity: Int = 1`, `unitPrice: BigDecimal = BigDecimal("10.00")`).
- **Minimal-override rule**: In every test `// given` block (unit and integration alike), invoke fixture functions with their defaults and override **only** the properties directly asserted on in `// then` or required to distinguish multiple instances (e.g., `anItem(id = "item-1")`, `anItem(id = "item-2")`). Assert against the fixture defaults (`customerId shouldBe "customerId"`) and keep shared values (such as a single currency across items) on their default. Keep `// given` shorter than the behavior under test by relying on defaults.

```kotlin
object ItemFixtures {
    fun anItem(
        id: String = "id",
        name: String = "name",
        quantity: Int = 1,
        unitPrice: BigDecimal = BigDecimal("10.00"),
        currency: Currency = Currency.getInstance("USD"),
    ) = Item(
        id = id,
        name = name,
        quantity = quantity,
        unitPrice = unitPrice,
        currency = currency,
    )
}
```

## 4. Reusable HTTP (`*ApiFixture`), `ProblemDetail` & Telemetry (`LogCapture`) Fixtures

Keep test methods focused on behavior rather than infrastructure plumbing:

- **HTTP API Fixtures (`*ApiFixture`)**: Wrap `RestTestClient` requests and typed DTO serialization in reusable helper classes in `src/testFixtures/kotlin`. Expose status-specific outcome helpers (e.g., `createOrderAndExpect201(request)` returning the response DTO, `createOrderAndExpect400(request)` returning `ProblemDetail`).
- **`ProblemDetail` Assertions (`ProblemDetailFixtures`)**: Every error status the API returns has one `ProblemDetail.shouldBe<Status>Error(...)` helper (`shouldBeValidationError(expectedError)` for `400` with its `errors` property, `shouldBeNotFoundError(expectedDetail)` for `404`). Assert error responses only through these helpers, and add the helper alongside the first endpoint that returns a new status.
- **`LogCapture` Fixture**: Register `LogCapture` via `@JvmField @RegisterExtension` and verify log level, message, structured key-value pairs, and `MDC` entries in a single call with `logCapture.assertLogged(level, message, keyValues, mdc)`. Do not add redundant manual `MDC.get(...)` assertions alongside `assertLogged`.

## 5. Given / When / Then Structure, Parameterized Validation, Kotest & Awaitility

- **Explicit `// given`, `// when`, `// then`, `// and` comments**: Structure every `@Test` method with `// given`, `// when`, `// then`, and optional `// and` blocks.
- **Focused test methods**: Keep each `@Test` focused on a single behavior or scenario outcome so failures point to one cause.
- **Exhaustive `@ParameterizedTest` with `@FieldSource` for request validation**: Every validation constraint on a request DTO (blank strings, empty collections, non-positive numbers, `@AssertTrue` invariants) must be covered in integration tests using a single `@ParameterizedTest(name = "{0}")` backed by a companion `@FieldSource` list of `Arguments.of(description, invalidRequest, expectedError)`.
- **Kotest Assertions (`io.kotest:kotest-assertions-core`)**: Use Kotest matchers (`shouldBe`, `shouldNotBeNull`, `shouldNotBeBlank`, `shouldBeInstanceOf`, `shouldContainExactly`, `shouldContainAll`, `shouldThrow`) and `assertSoftly` inside standard JUnit Jupiter test methods, in place of JUnit `Assertions.*`.
- **Whole-value equality**: Assert a response, entity, list, or thrown domain exception with one `shouldBe` against an expected instance (`response shouldBe anOrderResponse(orderId = created.orderId)`, `shouldThrow<OutOfStockException> { ... } shouldBe OutOfStockException(...)`), passing any generated field (`id`, `orderId`) into the fixture; reserve `assertSoftly` for checking multiple properties of a value that has no fixture.
- **Explicit literals over production constants**: In tests and test fixtures (`src/test`, `src/integration`, `src/testFixtures`), write expected HTTP header names, path variable names, MDC keys, and domain values as explicit literals (`"X-Checkout-Scenario"`, `"order.id"`, `"customer.id"`) rather than importing constants from production code, so tests lock down the external contract and detect accidental constant renames. In test fixtures (`*MockFixture`, `*ApiFixture`) and when a numeric tolerance or shared input value (`TOLERANCE = 0.02`) is repeated across assertions in a test class, extract the repeated value to a `private` constant in that file's `private companion object` (grouped in nested `object` namespaces when there are several categories).
- **Static imports**: Follow `docs/agents/architecture.md` §4 (fixture functions, assertion helpers, and enum/framework constants such as `INFO` or `CREATED`).
- **Awaitility Kotlin (`org.awaitility:awaitility-kotlin`)**: When verifying asynchronous or concurrent background work, use Awaitility (`await atMost ... untilAsserted { ... }`) instead of `Thread.sleep`.

```kotlin
@Test
fun `creates resource and emits structured log`() {
    // given
    val request = aCreateResourceRequest()

    // when
    val response = resourceApi.createResourceAndExpect201(request)

    // then
    response shouldBe aCreateResourceResponse(id = response.id)

    // and
    logCapture.assertLogged(
        level = INFO,
        message = "Resource created",
        keyValues = mapOf("resource.name" to "name"),
        mdc = mapOf("resource.id" to response.id),
    )
}
```

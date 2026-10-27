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
- **Integration Tests (`src/integration/kotlin`)**:
  - Configured via `com.coditory.integration-test` (`./gradlew integrationTest`, wired into `./gradlew check`).
  - Extend the `IntegrationTest` base class (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).
  - Verify the wired HTTP contract (including request validation `ProblemDetail` responses) and emitted telemetry as a **black box** through `*ApiFixture` / `RestTestClient` and `LogCapture`.
  - Interact strictly through the HTTP interface and captured logs—keep each integration test scoped to its feature's endpoints, and test cross-cutting servlet filters once in dedicated filter/health tests rather than re-testing filter cleanup across every endpoint test.

## 2. Fakes over Mocks

- Wire **real in-memory adapters** (fakes) into service unit tests rather than mocking domain ports.
- Do not use mocking frameworks (Mockito, MockK) for domain repositories or collaborators.

## 3. Minimal-Override `*Fixtures` with Self-Describing Dummy Defaults

Define Object Mother factory functions in `src/testFixtures/kotlin` using Kotlin default arguments:

- **Self-describing dummy defaults**: Default string values match their property name (`id: String = "id"`, `name: String = "name"`), constrained string/value types use valid self-explanatory defaults (`currency: String = "USD"`, `currency: Currency = Currency.getInstance("USD")`), and numeric/collection properties use simple constant defaults (`quantity: Int = 1`, `unitPrice: BigDecimal = BigDecimal("10.00")`).
- **Minimal-override rule**: In test `// given` blocks, invoke fixture functions with their defaults and override **only** the properties directly asserted on in `// then` or required to distinguish multiple instances (e.g., `anItem(id = "item-1")`, `anItem(id = "item-2")`).

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
- **`ProblemDetail` Validation Assertions (`ProblemDetailFixtures`)**: Verify `400 Bad Request` validation responses and their `errors` property using `problem.shouldBeValidationError(expectedError)`.
- **`LogCapture` Fixture**: Register `LogCapture` via `@JvmField @RegisterExtension` and verify log level, message, structured key-value pairs, and `MDC` entries in a single call with `logCapture.assertLogged(level, message, keyValues, mdc)`. Do not add redundant manual `MDC.get(...)` assertions alongside `assertLogged`.

## 5. Given / When / Then Structure, Parameterized Validation, Kotest & Awaitility

- **Explicit `// given`, `// when`, `// then`, `// and` comments**: Structure every `@Test` method with `// given`, `// when`, `// then`, and optional `// and` blocks.
- **Focused test methods**: Keep each `@Test` focused on a single behavior or scenario outcome so failures point to one cause.
- **Exhaustive `@ParameterizedTest` with `@FieldSource` for request validation**: Every validation constraint on a request DTO (blank strings, empty collections, non-positive numbers, `@AssertTrue` invariants) must be covered in integration tests using a single `@ParameterizedTest(name = "{0}")` backed by a companion `@FieldSource` list of `Arguments.of(description, invalidRequest, expectedError)`.
- **Kotest Assertions (`io.kotest:kotest-assertions-core`)**: Use Kotest matchers (`shouldBe`, `shouldNotBeNull`, `shouldNotBeBlank`, `shouldBeInstanceOf`, `shouldContainExactly`, `shouldContainAll`, `shouldThrow`) and `assertSoftly` inside standard JUnit Jupiter test methods.
- **Awaitility Kotlin (`org.awaitility:awaitility-kotlin`)**: When verifying asynchronous or concurrent background work, use Awaitility (`await atMost ... untilAsserted { ... }`) instead of `Thread.sleep`.

```kotlin
@Test
fun `creates resource and emits structured log`() {
    // given
    val request = aCreateResourceRequest(name = "custom-name")

    // when
    val response = resourceApi.createResourceAndExpect201(request)

    // then
    assertSoftly(response) {
        id.shouldNotBeBlank()
        name shouldBe "custom-name"
    }

    // and
    logCapture.assertLogged(
        level = Level.INFO,
        message = "Resource created",
        keyValues = mapOf("resource.name" to "custom-name"),
        mdc = mapOf("resource_id" to response.id),
    )
}
```

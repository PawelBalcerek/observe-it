# Testing Conventions

## 1. Test Separation & Source Sets (`test`, `integration`, `testFixtures`)

Each service module configures three test source sets via `conventions.spring`:

- **Shared Test Fixtures (`src/testFixtures/kotlin`)**:
  - Enabled via Gradle's `java-test-fixtures` plugin and shared across both unit and integration tests.
  - Houses domain/DTO `*Fixtures` (Object Mothers), reusable HTTP `*ApiFixture` clients, and log capture helpers (`LogCapture`).
- **Unit Tests (`src/test/kotlin`)**:
  - Fast, isolated, and decoupled from Spring container startup or network I/O.
  - Verify domain state transitions and invariants, service orchestration and structured log emission, adapter mechanics (such as capacity eviction and concurrency), and filter edge cases.
- **Integration Tests (`src/integration/kotlin`)**:
  - Configured via `com.coditory.integration-test` (`./gradlew integrationTest`, wired into `./gradlew check`).
  - Extend the `IntegrationTest` base class (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).
  - Verify the wired HTTP contract and emitted telemetry as a **black box** through `*ApiFixture` / `RestTestClient` and `LogCapture`.
  - Interact strictly through the HTTP interface and captured logs—do not `@Autowired` internal repositories or services to inspect state, and do not re-test internal adapter mechanics already covered in unit tests.

## 2. Fakes over Mocks

- Wire **real in-memory adapters** (fakes) into service unit tests rather than mocking domain ports.
- Do not use mocking frameworks (Mockito, MockK) for domain repositories or collaborators.

## 3. Minimal-Override `*Fixtures` with Self-Describing Dummy Defaults

Define Object Mother factory functions in `src/testFixtures/kotlin` using Kotlin default arguments:

- **Self-describing dummy defaults**: Default string values match their property name (`id: String = "id"`, `name: String = "name"`, `currency: String = "currency"`), and numeric/collection properties use simple constant defaults (`quantity: Int = 1`, `amount: Double = 10.0`).
- **Minimal-override rule**: In test `// given` blocks, invoke fixture functions with their defaults and override **only** the properties directly asserted on in `// then` or required to distinguish multiple instances (e.g., `anItem(id = "item-1")`, `anItem(id = "item-2")`).

```kotlin
object ItemFixtures {
    fun anItem(
        id: String = "id",
        name: String = "name",
        quantity: Int = 1,
        unitPrice: Double = 10.0,
    ) = Item(
        id = id,
        name = name,
        quantity = quantity,
        unitPrice = unitPrice,
    )
}
```

## 4. Reusable HTTP (`*ApiFixture`) & Telemetry (`LogCapture`) Fixtures

Keep test methods focused on behavior rather than infrastructure plumbing:

- **HTTP API Fixtures (`*ApiFixture`)**: Wrap `RestTestClient` requests and typed DTO serialization in reusable helper classes in `src/testFixtures/kotlin` instead of embedding multi-line raw JSON strings and manual `ObjectMapper.readTree` parsing in test methods.
- **`LogCapture` Fixture**: Encapsulate Logback `ListAppender<ILoggingEvent>` attachment, cleanup, and event querying in a reusable fixture/extension in `src/testFixtures/kotlin` instead of repeating `@BeforeEach` / `@AfterEach` logger setup across test classes.

## 5. Given / When / Then Structure, Kotest Assertions & Awaitility

- **Explicit `// given`, `// when`, `// then`, `// and` comments**: Structure every `@Test` method with `// given`, `// when`, `// then`, and optional `// and` blocks.
- **Focused test methods**: Keep each `@Test` focused on a single behavior or scenario outcome so failures point to one cause.
- **Kotest Assertions (`io.kotest:kotest-assertions-core`)**: Use Kotest matchers (`shouldBe`, `shouldNotBeNull`, `shouldNotBeBlank`, `shouldBeInstanceOf`, `shouldContainExactly`, `shouldThrow`) and `assertSoftly` inside standard JUnit Jupiter `@Test` methods.
- **Awaitility Kotlin (`org.awaitility:awaitility-kotlin`)**: When verifying asynchronous or concurrent background work, use Awaitility (`await atMost ... untilAsserted { ... }`) instead of `Thread.sleep`.

```kotlin
@Test
fun `creates resource and emits structured log`() {
    // given
    val request = aCreateResourceRequest(name = "custom-name")

    // when
    val response = resourceApi.create(request)

    // then
    assertSoftly(response) {
        id.shouldNotBeBlank()
        name shouldBe "custom-name"
    }

    // and
    val log = capturedLogs.singleWithKeyValue("resource.id", response.id)
    log.assertKeyValues("resource.name" to "custom-name")
}
```

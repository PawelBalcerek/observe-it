# Testing Conventions

## 1. Test Separation: Unit vs Integration

- **Unit tests** reside under `src/test/kotlin`:
  - Fast, isolated, and strictly decoupled from container lifecycles or network IO.
  - Examples: classpath verification, isolated domain logic, and pure algorithm checks.
- **Integration tests** reside under `src/integration/kotlin`:
  - Configured via the `com.coditory.integration-test` plugin, which registers the `integrationTest` task and wires it into `./gradlew check`.
  - Extend the `IntegrationTest` base class annotated with `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`.
  - Exercise running Spring application contexts and HTTP endpoints using Spring's `RestTestClient`.

## 2. Given / When / Then / And Structure

All tests across unit, integration, and build conventions must structure their execution flow using explicit `// given`, `// when`, `// then`, and `// and` comments. The `// given` setup and `// then` verification phases can also contain `// and` comments when setting up multiple steps or asserting multiple outcomes:

```kotlin
@Test
fun `health endpoint returns UP status with HTTP 200`() {
    // given
    val path = "/actuator/health"
    val jsonMapper = JsonMapper()

    // when
    val response = restTestClient.get()
        .uri(path)
        .exchange()

    // then
    response.expectStatus().isOk

    // and
    val body = response.expectBody(String::class.java).returnResult().responseBody
    val json = jsonMapper.readTree(body)
    assertEquals("UP", json.get("status").asString())
}
```

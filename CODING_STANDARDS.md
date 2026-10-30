# Coding Standards

Entry point for reviewing a diff against this repo's standards. Read and apply **every rule in every file below**, not a
sample:

| Source                                                       | Covers                                                                                                                                                   |
|--------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`docs/agents/architecture.md`](docs/agents/architecture.md) | Production code: packaging and dependency direction, cross-cutting context (`MDC`), concurrency, static imports, Kotlin idioms, dead code, KDoc sources. |
| [`docs/agents/testing.md`](docs/agents/testing.md)           | Tests and fixtures: test levels, ordering, fixtures, assertions, literals and constants.                                                                 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md)                         | Commit, branch, and PR shape.                                                                                                                            |

## Enforced by tooling

Skip anything already enforced by `./gradlew check` (Ktlint and Detekt in [`config/detekt/detekt.yml`](config/detekt/detekt.yml)).

## Review-only checks

Run both checks on the diff during review:

- **Callers**: for every declaration the diff adds, grep for a caller (`docs/agents/architecture.md` §6).
- **Suppressions**: list every `@Suppress` the diff adds and judge whether the code should change instead
  (`docs/agents/architecture.md` §5).

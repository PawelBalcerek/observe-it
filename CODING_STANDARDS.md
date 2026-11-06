# Coding Standards

Entry point for reviewing a diff against this repo's standards. Read and apply **every rule in every file below**, not a
sample:

| Source                                                         | Covers                                                                                                                                                                                                                                         |
| -------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`docs/agents/architecture.md`](docs/agents/architecture.md)   | Production code: packaging and dependency direction, cross-cutting context (`MDC`), concurrency, static imports, Kotlin idioms, dead code, latest dependency versions, KDoc sources.                                                           |
| [`docs/agents/testing.md`](docs/agents/testing.md)             | Tests and fixtures: test levels, ordering, fixtures, assertions, literals and constants.                                                                                                                                                       |
| [`docs/agents/documentation.md`](docs/agents/documentation.md) | Documentation and Markdown: `CONTEXT.md` glossary adherence, living guides (domain-agnostic General Pattern → `### External Sources & Ecosystem Practice` → `### In Observe-It`) vs. append-only atomic ADRs, code blocks, Mermaid, and links. |
| [`CONTRIBUTING.md`](CONTRIBUTING.md)                           | Commit, branch, and PR shape.                                                                                                                                                                                                                  |

## Enforced by tooling

Skip anything already enforced by `./gradlew check` (Ktlint and Detekt in
[`config/detekt/detekt.yml`](config/detekt/detekt.yml)) or `make docs-build` (`markdownlint-cli2` in
[`docs/.markdownlint-cli2.jsonc`](docs/.markdownlint-cli2.jsonc), `mermaid.parse()` syntax validation in
[`docs/scripts/lint-mermaid.mjs`](docs/scripts/lint-mermaid.mjs), and `vitepress build` dead-link checking).

## Review-only checks

Run all checks on the diff during review:

- **Callers**: for every declaration the diff adds, grep for a caller (`docs/agents/architecture.md` §6).
- **Versions**: for every dependency, plugin, container image, or GitHub Action added or bumped in the diff, verify it
  uses the latest compatible stable release (`docs/agents/architecture.md` §6).
- **Suppressions**: list every `@Suppress` the diff adds and judge whether the code should change instead
  (`docs/agents/architecture.md` §5).

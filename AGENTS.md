## Agent skills

### Issue tracker

Issues and PRDs live in GitHub Issues (`gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default five canonical triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`,
`wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout (`CONTEXT.md` and `docs/adr/` at the repo root). See `docs/agents/domain.md`.

### Architecture & packaging

Feature packages split into `api`, `domain`, and `infrastructure` subpackages (`api` → `domain` ← `infrastructure`),
transport-agnostic cross-cutting context, lock-free `java.util.concurrent` state over `synchronized`, DTO naming,
shared exception handling, and static imports. Read before writing or modifying any production code. See
`docs/agents/architecture.md`.

### Testing conventions

Unit tests (`src/test/kotlin`), black-box integration tests (`src/integration/kotlin`), and shared fixtures
(`src/testFixtures/kotlin`) using `given` / `when` / `then` / `and` blocks, minimal-override `*Fixtures` with
self-describing dummy defaults, `LogCapture` and `*ApiFixture` helpers, fakes over mocks, Kotest assertions, and
Awaitility. Read before writing or modifying any test or test fixture. See `docs/agents/testing.md`.

### Code formatting & static analysis (ktlint & detekt)

Ktlint (`org.jlleitschuh.gradle.ktlint`) and Detekt (`dev.detekt`, configured in `config/detekt/detekt.yml`) are wired into `./gradlew check`. Before committing any changes, run `./gradlew check` and format with `./gradlew ktlintFormat` if needed. Reviews check diffs against `CODING_STANDARDS.md`.

### Git & PR workflow

Issue-first ticket lifecycle (`feat/`, `fix/` or `retro/<ticket-slug>` branch, single `feat: ...` / `fix: ...` /
`retro: ...` commit amended on review, PR linking `Closes #<n>` / `Resolves #<n>`, PR review discussion and
reply-and-resolve loop, and retro rules). Read before starting a ticket, committing, opening a PR, handling PR review
comments, or running a retro. See `CONTRIBUTING.md`.

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
transport-agnostic cross-cutting context, and lock-free `java.util.concurrent` state over `synchronized`. Read before
creating or modifying packages, controllers, domain services, adapters, or filters. See `docs/agents/architecture.md`.

### Testing conventions

Unit tests (`src/test/kotlin`), black-box integration tests (`src/integration/kotlin`), and shared fixtures
(`src/testFixtures/kotlin`) using `given` / `when` / `then` / `and` blocks, minimal-override `*Fixtures` with
self-describing dummy defaults, `LogCapture` and `*ApiFixture` helpers, fakes over mocks, Kotest assertions, and
Awaitility. Read before writing or modifying any test or test fixture. See `docs/agents/testing.md`.

### Code formatting & style (ktlint)

Ktlint is enforced via `org.jlleitschuh.gradle.ktlint`. Before committing any changes, run `./gradlew ktlintCheck`
(or `./gradlew check`) and format with `./gradlew ktlintFormat` if needed.

### Git & PR workflow

Deliver each ticket on a `feat/<ticket-slug>` or `fix/<ticket-slug>` branch as a single `feat: ...` / `fix: ...` commit
(amend/rebase on review changes) and a single PR (via `gh pr` or `gh stack`, referencing `Closes #<n>` /
`Resolves #<n>`), keeping commit messages and PR descriptions up to date. See `CONTRIBUTING.md`.

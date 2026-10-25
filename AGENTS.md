## Agent skills

### Issue tracker

Issues and PRDs live in GitHub Issues (`gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default five canonical triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`,
`wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout (`CONTEXT.md` and `docs/adr/` at the repo root). See `docs/agents/domain.md`.

### Testing conventions

Tests are split into unit tests (`src/test/kotlin`) and integration tests (`src/integration/kotlin`), structured with
`given` / `when` / `then` / `and` blocks. See `docs/agents/testing.md`.

### Code formatting & style (ktlint)

Ktlint is enforced via `org.jlleitschuh.gradle.ktlint`. Before committing any changes, run `./gradlew ktlintCheck`
(or `./gradlew check`) and format with `./gradlew ktlintFormat` if needed.

### Git & PR workflow

Deliver each ticket on a `feat/<ticket-slug>` or `fix/<ticket-slug>` branch as a single `feat: ...` / `fix: ...` commit
(amend/rebase on review changes) and a single PR (via `gh pr` or `gh stack`, referencing `Closes #<n>` /
`Resolves #<n>`), keeping commit messages and PR descriptions up to date. See `CONTRIBUTING.md`.

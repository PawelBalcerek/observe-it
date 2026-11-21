# Agent Guidelines

## Repo-wide workflow

### Interactive questioning

Ask clarifying or seam-confirmation questions only during `wayfinder` and `grilling` (`grill-me`, `grill-with-docs`,
`grilling`) sessions. During `implement` (including `tdd`), select and proceed with the best-fit seams and defaults
directly without pausing for confirmation.

### Issue tracker & triage labels

Issues and PRDs live in GitHub Issues (`gh` CLI); read [`docs/agents/issue-tracker.md`](docs/agents/issue-tracker.md)
before creating or updating issues. Triage uses five canonical labels (`needs-triage`, `needs-info`, `ready-for-agent`,
`ready-for-human`, `wontfix`); see [`docs/agents/triage-labels.md`](docs/agents/triage-labels.md).

### Domain docs

Single-context layout (`CONTEXT.md` and `docs/adr/` at the repo root). Use canonical terms from `CONTEXT.md` and read
relevant ADRs before designing or editing a feature; see [`docs/agents/domain.md`](docs/agents/domain.md).

### Git, PRs & code review

Follow the issue-first ticket lifecycle (`feat/`, `fix/`, or `retro/<ticket-slug>` branch, single Conventional Commit
amended on review, PR linking `Closes #<n>`) in [`CONTRIBUTING.md`](CONTRIBUTING.md). Reviews check diffs against
path-scoped [`CODING_STANDARDS.md`](CODING_STANDARDS.md). When adding or bumping any dependency, plugin, container
image, or GitHub Action across any stack, query the live registry and pin the latest compatible stable release.

## Stack guides & verification

Read the matching guide before editing files in a stack, and run its verification gate before committing:

| Stack                     | Paths                                                                         | Verification gate                            | Guides                                                                                                           |
| ------------------------- | ----------------------------------------------------------------------------- | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Kotlin/JVM                | `observed-app/`, `traffic-generator/`, `e2e/`, `build-logic/`, `*.gradle.kts` | `./gradlew check` (`./gradlew ktlintFormat`) | [`docs/agents/architecture.md`](docs/agents/architecture.md), [`docs/agents/testing.md`](docs/agents/testing.md) |
| Storefront                | `storefront/`                                                                 | `pnpm --dir storefront check`                | [`docs/agents/frontend.md`](docs/agents/frontend.md)                                                             |
| Browser Traffic Generator | `browser-traffic-generator/`                                                  | `pnpm --dir browser-traffic-generator check` | [`docs/agents/browser-traffic.md`](docs/agents/browser-traffic.md)                                               |
| Docs & Markdown           | `docs/`, `*.md`                                                               | `make docs-build`                            | [`docs/agents/documentation.md`](docs/agents/documentation.md)                                                   |

# Coding Standards

Path-scoped entry point for reviewing a diff against this repo's standards. Apply the **Always apply** section on every
review, then apply every stack section whose paths are touched by the diff—reading **every rule in each applicable
guide**, not a sample.

## Always apply (repo-wide)

| Source                               | Covers                                                                             |
| ------------------------------------ | ---------------------------------------------------------------------------------- |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Issue-first branch, single Conventional Commit, and PR shape.                      |
| [`CONTEXT.md`](CONTEXT.md)           | Canonical domain glossary terms and _Avoid_ synonyms across code, tests, and docs. |

**Review-only check:**

- **Versions**: for every dependency, plugin, container image, runtime major (Node.js, Java), or GitHub Action added or
  bumped across Gradle, `pnpm`/`npm`, Dockerfiles, Compose, or `.github/workflows/`, verify it uses the latest
  compatible stable release from an active (non-archived) live registry—never copied from a stale sibling job or
  Dockerfile—or pins the highest compatible release with an explanatory `ignore` entry in `.github/dependabot.yml`.

## Kotlin/JVM (`observed-app/`, `traffic-generator/`, `e2e/`, `build-logic/`, `*.gradle.kts`)

- **Guides**: [`docs/agents/architecture.md`](docs/agents/architecture.md), [`docs/agents/testing.md`](docs/agents/testing.md)
- **Skip (enforced by `./gradlew check`)**: Ktlint formatting and Detekt rules in
  [`config/detekt/detekt.yml`](config/detekt/detekt.yml).
- **Review-only checks**:
  - **Callers**: for every declaration and default parameter value the diff adds in `src/main`, grep for a caller in
    `src/main` that uses the declaration or omits the defaulted parameter (`docs/agents/architecture.md` §6).
  - **Suppressions**: list every `@Suppress` the diff adds and judge whether the code should change instead
    (`docs/agents/architecture.md` §5).
  - **Test signal**: for every new test or `Fake*` fixture in the diff, verify it tests application behavior rather than
    framework annotations, raw storage internals, single-line SDK wrappers, or the absence of unimplemented behavior,
    keeps fakes free of spy/callback hooks, and avoids replaying domain state transitions in persistence adapter tests
    (`docs/agents/testing.md` §1, §2).

## Storefront (`storefront/`)

- **Guide**: [`docs/agents/frontend.md`](docs/agents/frontend.md)
- **Skip (enforced by `pnpm --dir storefront check`)**: TypeScript compiler checks (`tsconfig.json`), Biome lint
  (including unidirectional imports and `@opentelemetry/*` / `web-vitals` isolation in `src/telemetry/`) and formatting
  (`biome.json`), Knip dead-export and unused-file checks (`knip`), and Vitest test execution.
- **Review-only checks**:
  - **Suppressions**: list every `biome-ignore`, `@ts-expect-error`, and `@ts-ignore` the diff adds and judge whether
    the code should change instead (`docs/agents/frontend.md` §4).
  - **Component typing & state**: verify React function components are explicitly typed with `FC` / `FC<Props>` and
    cross-feature state is shared via React Context (`src/lib/` + `src/app/`) rather than prop-drilling
    (`docs/agents/frontend.md` §1).
  - **Named constants & config**: verify repeated protocol or domain literals are grouped in `as const` objects and
    cross-module subsystem constants (such as `src/telemetry/config.ts`) are centralized rather than scattered across
    files (`docs/agents/frontend.md` §1, §3).
  - **Test signal**: verify tests use `// given` / `// when` / `// then` structure, reuse `src/test/` fixtures
    (`orderFixtures`, `observedAppApiFixture`, `consoleFixture`) instead of inline `fetch` or `vi.spyOn` mocks, extract
    domain-named `render*` / `user*` / `expect*` helpers (file-local or on `InMemoryTelemetryHarness`) rather than
    multi-line raw `expect(...)` chains, assert user-visible behavior via Testing Library accessible queries
    (`getByRole`, `getByLabelText`) and in-memory OTel exporters, and skip tests that restate static declarations or
    duplicate Biome/TypeScript checks (`docs/agents/frontend.md` §4).

## Browser Traffic Generator (`browser-traffic-generator/`)

- **Guide**: [`docs/agents/browser-traffic.md`](docs/agents/browser-traffic.md)
- **Skip (enforced by `pnpm --dir browser-traffic-generator check`)**: TypeScript compiler checks (`tsconfig.json`),
  Biome lint (including `@opentelemetry/*` import isolation via `noRestrictedImports`) and formatting (`biome.json`),
  Knip dead-export and unused-file checks (`knip`), and Vitest test execution.
- **Review-only checks**:
  - **Suppressions**: list every `biome-ignore`, `@ts-expect-error`, and `@ts-ignore` the diff adds and judge whether
    the code should change instead (`docs/agents/browser-traffic.md` §5).
  - **Named constants & config**: verify repeated HTTP methods, status codes, headers, and route paths are grouped in
    `as const` objects (`HTTP_METHOD`, `HTTP_STATUS`, `HTTP_HEADER`, `ROUTES`) rather than repeated inline
    (`docs/agents/browser-traffic.md` §4).
  - **Test signal**: verify unit tests use `// given` / `// when` / `// then` structure, reusable fixtures
    (`src/test/`), and domain-named `expect*` helpers rather than multi-line raw `expect(...)` chains; skip tests that
    restate static declarations/defaults, use brittle float-boundary tables, or duplicate Biome/TypeScript checks; cover
    scenario weight distribution, control API routes, and `BrowserContext`/recycle hygiene with in-memory fakes rather
    than launching real Chromium (`docs/agents/browser-traffic.md` §5).
  - **Uninstrumented invariant**: verify zero `@opentelemetry/*` dependencies in `package.json` and zero OTLP export
    paths anywhere in `browser-traffic-generator/` (imports are enforced by Biome `noRestrictedImports`,
    `docs/agents/browser-traffic.md` §2).

## Docs & Markdown (`docs/`, `*.md`)

- **Guide**: [`docs/agents/documentation.md`](docs/agents/documentation.md)
- **Skip (enforced by `make docs-build`)**: `markdownlint-cli2` in
  [`docs/.markdownlint-cli2.jsonc`](docs/.markdownlint-cli2.jsonc), `mermaid.parse()` syntax validation in
  [`docs/scripts/lint-mermaid.mjs`](docs/scripts/lint-mermaid.mjs), MADR ADR structure validation in
  [`docs/scripts/lint-adr.mjs`](docs/scripts/lint-adr.mjs), `npm test` over `docs/scripts/*.test.mjs`, and
  `vitepress build` dead-link checking.
- **Review-only checks**:
  - **No linter restatements**: flag any rule added to `AGENTS.md` or `docs/agents/*.md` that is already enforced by a
    configured linter, compiler, or check script; keep tool configs as the single source of truth.

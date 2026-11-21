# Multi-Stack Agent Guidance Layout for `AGENTS.md` and `docs/agents/` (2026)

**Issue**: [#186 — Research multi-stack agent guidance layout for AGENTS.md and docs/agents](https://github.com/PawelBalcerek/observe-it/issues/186)
**Parent Map**: [#179 — Frontend Observability & Browser Traffic](https://github.com/PawelBalcerek/observe-it/issues/179)

## Executive Summary & Recommendation

As Observe-It expands from a single Kotlin/Gradle stack + VitePress docs site into a polyglot repository adding a
standalone React/TypeScript SPA (`storefront/` with `pnpm`, Vite 8, Biome v2, Vitest 5) and potentially a standalone
Node.js/TypeScript Playwright browser-traffic runner, keeping all stack rules flat in the root `AGENTS.md` and
unscoped `CODING_STANDARDS.md` will dilute agent attention and load irrelevant Kotlin rules on frontend PRs.

**Recommended Layout for Observe-It — Option C (Hybrid):**

1. **Keep root `AGENTS.md` thin (~50–70 lines)** as the single repo-wide entry point: repo-wide workflow pointers
   (interactive questioning, issue tracker, triage labels, domain docs, Git & PR workflow) plus a compact **Stack
   Routing Table** mapping each stack's paths to its verification command and its deep guide in `docs/agents/`.
2. **Keep `docs/agents/` as the single source of truth** for deep stack conventions (progressively disclosed on
   demand): retain `docs/agents/architecture.md` and `docs/agents/testing.md` (scoped explicitly to Kotlin/JVM),
   `docs/agents/documentation.md`, and add `docs/agents/frontend.md` (and `docs/agents/browser-traffic.md` only if a
   separate Playwright runner is added with distinct conventions).
3. **Add tiny (~10–15 line) nested `AGENTS.md` shims only at standalone non-Gradle stack roots**
   (`storefront/AGENTS.md`, `docs/AGENTS.md`, and future `browser-traffic/AGENTS.md`). Each shim states only the local
   verification command, key non-obvious local gotchas, and a pointer to the authoritative `docs/agents/*.md` file —
   zero rule duplication. Do **not** duplicate `AGENTS.md` across the four Kotlin/Gradle subprojects (`observed-app/`,
   `traffic-generator/`, `e2e/`, `build-logic/`).
4. **Avoid vendor-specific rule folders** (`.claude/rules/`, `.cursor/rules/*.mdc`, `.github/instructions/`). All six
   major 2026 coding agent harnesses support plain-Markdown `AGENTS.md` (Claude Code natively falls back to `AGENTS.md`
   when no `CLAUDE.md` exists), avoiding multi-file drift and YAML frontmatter.
5. **Make `CODING_STANDARDS.md` path-scoped by stack**: keep repo-wide standards (`CONTRIBUTING.md`, `CONTEXT.md`,
   dependency version freshness) always active, and instruct `code-review`'s Standards axis to load only the
   `docs/agents/*` files and review-only checks matching the stacks touched in the diff.
6. **Exclude `AGENTS.md` in `docs/.vitepress/config.mts`** (`srcExclude` and `llmstxt.ignoreFiles`) when adding
   `docs/AGENTS.md` so VitePress does not publish agent instructions to GitHub Pages.

---

## 1. Current Observe-It Agent Guidance Audit

Observe-It currently has a clean, vendor-neutral agent guidance setup:

- **Root `AGENTS.md` (58 lines, ~3.1 KB)**: Acts as a pointer index with zero vendor-specific files (no `CLAUDE.md`,
  `.claude/rules/`, `.cursor/rules/`, or `.github/instructions/`).
- **`docs/agents/` (~45 KB total across 6 files)**: Excluded from VitePress via `srcExclude: ['agents/**']` in
  `docs/.vitepress/config.mts`. Holds deep conventions (`architecture.md` 13.2 KB, `testing.md` 14.4 KB,
  `documentation.md` 11.0 KB, `issue-tracker.md` 3.8 KB, `domain.md` 2.1 KB, `triage-labels.md` 1.0 KB).
- **`CODING_STANDARDS.md` (35 lines, ~3.6 KB)**: Entry point for the `code-review` skill's Standards sub-agent.

**Polyglot friction points identified in the current layout:**

1. **Implicit Kotlin-only framing in root `AGENTS.md`**: Three of the nine sections (*Architecture & packaging*,
   *Testing conventions*, *Code formatting & static analysis (ktlint & detekt)*) describe Kotlin/Gradle rules without
   scoping them to JVM paths, paying context load on every docs-only or frontend-only turn.
2. **Unscoped `CODING_STANDARDS.md`**: Instructs reviewers to *"Read and apply every rule in every file below, not a
   sample"*, unconditionally pulling ~27.6 KB of Kotlin architecture and testing rules into a `storefront/` or `docs/`
   review, and lists Kotlin-only review checks (`src/main`, `@Suppress`, `Fake*`) at the top level.
3. **Standalone JS/TS subprojects lack local entry points**: Both `docs/` (standalone `npm` + VitePress) and the
   planned `storefront/` (standalone `pnpm` + Vite 8 + Biome v2 + Vitest 5) are self-contained toolchains where
   developers and agents frequently operate inside the subdirectory.

---

## 2. How Major 2026 Agent Harnesses Discover & Load Instructions

Primary-source behavior across the six major coding agent harnesses reveals a critical asymmetry: **not all harnesses
lazy-load nested `AGENTS.md` files below the repository root.**

| Harness                     | Primary Format(s)                          | Root → CWD Walk (Startup) | Lazy-Load Nested Below CWD?               | Native Path-Scoped Rules                    | Size / Budget Guardrails                    |
| --------------------------- | ------------------------------------------ | ------------------------- | ----------------------------------------- | ------------------------------------------- | ------------------------------------------- |
| **Claude Code**             | `CLAUDE.md` (native), `AGENTS.md` fallback | Yes (ancestors to root)   | Yes, on native `Read`/`Write`/`Edit` only | `.claude/rules/*.md` (`paths:` frontmatter) | Recommends <200 lines; per-file cap on lazy |
| **OpenAI Codex CLI**        | `AGENTS.override.md`, `AGENTS.md`          | Yes (Git root → CWD)      | **No** (locked at startup)                | None (directory hierarchy only)             | `project_doc_max_bytes` = 32 KiB shared cap |
| **Cursor**                  | `AGENTS.md`, `.cursor/rules/*.mdc`         | Yes                       | Yes (implicit `subdir/**` glob)           | `.cursor/rules/*.mdc` (`globs:` + `always`) | Plain `.md` ignored inside `.cursor/rules/` |
| **GitHub Copilot**          | `AGENTS.md`, `.github/instructions/*`      | Yes                       | Yes (nearest `AGENTS.md` wins)            | `.github/instructions/*.instructions.md`    | `applyTo:` glob frontmatter for `.github/`  |
| **Gemini CLI**              | `GEMINI.md` (or `AGENTS.md` via config)    | Yes (CWD → root)          | Yes (Just-In-Time on directory access)    | None (`context.fileName` array in settings) | `context.discoveryMaxDirs` traversal cap    |
| **Antigravity CLI**         | `GEMINI.md`, `AGENTS.md`, `.agents/rules/` | Yes (CWD → root)          | **No** (walks upward from CWD only)       | `.agents/rules/*.md` (`model_decision`)     | 24 KB per-file cap; 20k-token rules budget  |
| **OpenCode**                | `AGENTS.md`                                | Yes (CWD → root)          | **No** (startup/ancestor walk only)       | `opencode.json` `instructions` globs        | All `instructions` globs load every turn    |

### Key Primary-Source Nuances

- **Open `AGENTS.md` specification (`agents.md`)**: Plain Markdown with no frontmatter. Supports nested `AGENTS.md`
  files in monorepos where the file closest to the edited file takes precedence over ancestor files.
- **Claude Code (`code.claude.com/docs/en/memory`)**: Supports `AGENTS.md` natively via the default
  `claude-md-or-agents-md` mode — if no `CLAUDE.md` exists in the directory chain, Claude Code loads root and nested
  `AGENTS.md` files automatically. Nested files load lazily when Claude invokes its native `Read`, `Write`, or `Edit`
  tools on a path inside that subtree (caveat: shell commands like `cat` via Bash do not trigger lazy loading).
  Anthropic recommends keeping root instruction files **under 200 lines** and enforcing hard invariants via linters,
  hooks, or settings rather than prose.
- **OpenAI Codex CLI (`developers.openai.com/codex/guides/agents-md`)**: Builds the instruction chain once at session
  startup by walking from `~/.codex` and the Git root down to the launch CWD. Nested `AGENTS.md` files *below* the
  launch CWD are **not** dynamically loaded mid-session. All discovered files share a single cumulative
  `project_doc_max_bytes` budget (default **32 KiB**); a bloated root file silently truncates nested files.
- **Cursor (`cursor.com/docs/context/rules`)**: Reads root and nested `AGENTS.md` files without frontmatter; a nested
  `AGENTS.md` behaves like an implicit `subdir/**` glob rule that auto-attaches when the agent works inside that
  subtree, with deeper files overriding parent rules.
- **GitHub Copilot (`docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions`)**:
  Supports root and nested `AGENTS.md` anywhere in the repository, applying the nearest file in the directory tree.
  Centralized glob targeting requires `.github/instructions/*.instructions.md` with `applyTo` YAML frontmatter.
- **Gemini CLI (`geminicli.com/docs/cli/gemini-md/`) & Antigravity (`agy-customizations`)**: Gemini CLI supports
  `AGENTS.md` via `context.fileName` in `.gemini/settings.json` and dynamically loads Just-In-Time (JIT) nested context
  files when tools access subdirectories. Antigravity CLI discovers `AGENTS.md` natively by walking upward from CWD to
  the repository root, with a 24 KB per-file cap and a 20,000-token aggregate rules budget (over-budget rules are
  demoted to path pointers).
- **OpenCode (`opencode.ai/docs/rules/`)**: Walks upward from CWD to project root; does not lazy-load deeper
  subdirectory `AGENTS.md` files on file access from a root-launched session. Recommends explicit read-on-demand
  pointers in root `AGENTS.md` to avoid context crowding.

**Architectural Consequence:** Relying *solely* on nested `AGENTS.md` files breaks root-launched sessions in **Codex
CLI**, **Antigravity CLI**, and **OpenCode** (and Bash-only reads in Claude Code). Conversely, keeping a thin root
`AGENTS.md` with explicit stack pointers guarantees 100% discoverability across every harness, while tiny nested
`AGENTS.md` shims add zero-friction ergonomics for subdirectory-launched sessions and JIT-capable harnesses.

---

## 3. Context Bloat, Instruction Dilution & `writing-for-agents`

Recent empirical research and Observe-It's own `.agents/skills/writing-for-agents/SKILL.md` converge on the same
design discipline:

- **ETH Zurich & LogicStar study (Gloaguen et al., Feb 2026, `arXiv:2602.11988`)**: Evaluating `AGENTS.md` across
  SWE-bench and real repositories found that LLM-generated context files *reduced* task success rates by ~3% while
  increasing token and inference costs by >20%; human-written files improved success only marginally (+4%) at the same
  >20% cost penalty. Agents reliably followed explicit non-standard tooling commands (such as custom test runners),
  whereas broad repository overviews and redundant style prose caused over-exploration and attention dilution.
- **Anthropic Context Engineering (*Effective context engineering for AI agents*)**: Models exhibit "context rot"
  well before hitting hard token limits. Treat always-loaded instructions as scarce RAM: retain the smallest set of
  high-signal tokens, offload deterministic style checks to linters/tooling, and rely on on-demand retrieval.
- **Observe-It's `writing-for-agents` principles**:
  - **Context load vs. cognitive load**: Every line in root `AGENTS.md` spends context load on every turn across every
    stack. Disclose stack-specific rules behind a sharply worded **context pointer** (front-loaded trigger, one trigger
    per branch).
  - **Branching disclosure test**: Inline only what *every* branch (every stack) needs; push stack-specific material
    behind a pointer.
  - **Environment as source of truth**: Never cache one-file, one-command lookups (`package.json` scripts, Gradle task
    lists, directory trees) that drift; document only unwritten conventions, architectural invariants, and gotchas.
  - **Single source of truth**: Keep each rule in one authoritative file so updates are a one-place edit and never
    inflate a rule's prominence via duplication.

---

## 4. Option Comparison for Observe-It

| Criterion                                | Option A: Thin Root `AGENTS.md` + `docs/agents/*` Only | Option B: Standalone Nested `AGENTS.md` per Stack       | Option C (Recommended): Hybrid (Thin Root + Tiny Shims + `docs/agents/*`) |
| ---------------------------------------- | ------------------------------------------------------ | ------------------------------------------------------- | ------------------------------------------------------------------------- |
| **Root-launched Codex / Agy / OpenCode** | Always discovers stack rules via root pointers         | **Fails**: nested files below CWD are not auto-loaded   | Always discovers stack rules via root pointers                            |
| **Subdir-launched / JIT harnesses**      | Must walk up to root, then follow pointer              | Auto-loads local `AGENTS.md` immediately                | Auto-loads local shim immediately, points to `docs/agents/*`              |
| **Multi-dir Kotlin/Gradle stack**        | Clean single pointer for 4 Gradle modules              | Awkward (duplicate across 4 dirs or fall back to root)  | Clean single pointer in root for 4 Gradle modules                         |
| **Context load at repo root**            | Minimal (~50–70 lines)                                 | Minimal at root, but large nested files risk 32 KiB cap | Minimal (~50–70 lines root; ~10–15 line shims)                            |
| **Single source of truth**               | High (all deep rules live in `docs/agents/*`)          | Low if rules drift between root and nested files        | High (shims only hold local command + pointer, zero rule copies)          |
| **Tooling / VitePress footprint**        | Zero changes to `docs/.vitepress/config.mts`           | Requires VitePress `srcExclude` for `docs/AGENTS.md`    | Requires 1-line `srcExclude` update for `docs/AGENTS.md`                  |
| **Maintenance overhead**                 | Lowest                                                 | Highest (multiple full rule files to keep in sync)      | Very low (shims change only if local verification command changes)        |

**Why Option C beats Option A and Option B:**

- **Option B fails** on harness portability (Codex, Antigravity, and OpenCode ignore nested `AGENTS.md` below CWD)
  and on Observe-It's Gradle layout (Kotlin spans `observed-app/`, `traffic-generator/`, `e2e/`, and `build-logic/`).
- **Option A works well** and is already close to Observe-It's current shape, but lacks local ergonomics when an
  engineer or agent opens a session directly inside `storefront/` or `docs/`, or relies on Cursor/Claude/Copilot
  auto-attaching local `AGENTS.md` context when touching a file in a standalone JS/TS package.
- **Option C combines both strengths without duplication**: `docs/agents/*` remains the sole home for deep rules; root
  `AGENTS.md` routes every stack reliably for root-launched agents; and 10–15 line shims in `storefront/AGENTS.md` and
  `docs/AGENTS.md` give local-session and JIT harnesses immediate verification commands and pointers.

---

## 5. Concrete Target Layout for Observe-It

### 5.1 Directory & File Layout

```text
observe-it/
├── AGENTS.md                        # Thin (~50–70 lines): repo-wide workflow + stack routing table
├── CODING_STANDARDS.md              # Path-scoped review dispatch table (repo-wide + per-stack checks)
├── CONTRIBUTING.md                  # Repo-wide Git, branch, commit, PR, and retro workflow
├── CONTEXT.md                       # Ubiquitous domain glossary
├── docs/
│   ├── AGENTS.md                    # ~10-line shim: `make docs-build` + pointer to `docs/agents/documentation.md`
│   ├── .vitepress/config.mts        # Exclude `['agents/**', 'AGENTS.md']` from VitePress & llmstxt
│   └── agents/
│       ├── issue-tracker.md         # Repo-wide (unchanged)
│       ├── triage-labels.md         # Repo-wide (unchanged)
│       ├── domain.md                # Repo-wide (unchanged)
│       ├── architecture.md          # Kotlin/JVM production code conventions (scoped explicitly to JVM)
│       ├── testing.md               # Kotlin/JVM testing & fixture conventions (scoped explicitly to JVM)
│       ├── frontend.md              # NEW: React 19 / TS / Vite 8 / Biome v2 / Vitest 5 conventions
│       ├── browser-traffic.md       # OPTIONAL: only if standalone Playwright runner has distinct rules
│       └── documentation.md         # Docs & Markdown conventions (unchanged)
└── storefront/
    └── AGENTS.md                    # ~12-line shim: `pnpm` verification gates + pointer to `docs/agents/frontend.md`
```

> [!NOTE]
> Keeping `docs/agents/architecture.md` and `docs/agents/testing.md` at their existing paths avoids churn in existing
> skills, PRs, and historical references while scoping them explicitly to Kotlin/JVM inside root `AGENTS.md` and
> `CODING_STANDARDS.md`.

### 5.2 Root `AGENTS.md` Shape (~55–65 Lines)

Keep the five repo-wide sections (Interactive questioning, Issue tracker, Triage labels, Domain docs, Git & PR workflow)
unchanged, and replace the four stack-specific sections with a single **Stack guides & verification** section:

```markdown
### Stack guides & verification

Read the matching stack guide before editing code, tests, build configs, or documentation, and run its verification
gate before committing:

| Scope                                                         | Verification gate                     | Standards & conventions                                               |
| ------------------------------------------------------------- | ------------------------------------- | --------------------------------------------------------------------- |
| Kotlin/JVM (`observed-app/`, `traffic-generator/`, `e2e/`, `build-logic/`) | `./gradlew check` (`./gradlew ktlintFormat`) | `docs/agents/architecture.md`, `docs/agents/testing.md` |
| React SPA (`storefront/`)                                     | `pnpm --dir storefront check`         | `docs/agents/frontend.md`                                             |
| Docs & Markdown (`docs/`, `*.md`)                             | `make docs-build`                     | `docs/agents/documentation.md`                                        |

Reviews check diffs against `CODING_STANDARDS.md` (scoped to the stacks touched by the diff).
```

This trims always-loaded Kotlin prose from root `AGENTS.md` so frontend-only and docs-only turns pay near-zero
irrelevant context load, while preserving strong, front-loaded triggers for every stack.

### 5.3 Nested Shim Pattern (`storefront/AGENTS.md` & `docs/AGENTS.md`)

Keep each nested shim strictly to ~10–15 lines (local verification commands, non-obvious local gotchas, and a pointer
to `docs/agents/*` — never copy rules from `docs/agents/*`):

```markdown
# Storefront Agent Guidelines

Standalone React 19 + TypeScript + Vite 8 + Biome v2 + Vitest 5 SPA (`pnpm`).

- **Read before editing**: `../docs/agents/frontend.md` (component, state, telemetry, and Vitest conventions).
- **Verify before committing**: run `pnpm check` (typecheck + Biome CI + Vitest + production build) from `storefront/`.
- **Do not** run `./gradlew check` for `storefront/`-only changes, and do not fold `storefront/` into `docs/` (`npm`).
```

### 5.4 Keeping `CODING_STANDARDS.md` Clean for `code-review`

Restructure `CODING_STANDARDS.md` into three concise parts so the `code-review` Standards sub-agent loads only what
the diff touches:

1. **Always apply (repo-wide)**: `CONTRIBUTING.md` (commit/branch/PR shape) and `CONTEXT.md` (domain glossary).
2. **Apply by touched stack**: A table mapping file globs to the exact `docs/agents/*.md` files to read in full (JVM →
   `architecture.md` + `testing.md`; `storefront/**` → `frontend.md`; `docs/**` or `*.md` → `documentation.md`).
3. **Enforced by tooling & Review-only checks**:
   - **Repo-wide review-only check**: **Versions** (latest compatible stable release for any added/bumped Gradle,
     `pnpm`/`npm`, Docker image, or GitHub Action dependency).
   - **Kotlin/JVM (`src/main`, `src/test`, `src/integration`, `src/testFixtures`)**: Skip `./gradlew check` (Ktlint,
     Detekt); run **Callers**, **Suppressions (`@Suppress`)**, and **Test signal** checks.
   - **Storefront (`storefront/`)**: Skip `pnpm check` (TypeScript strict check, Biome v2 lint/format, Vitest); run
     TypeScript/React review-only checks (e.g., `biome-ignore` / `@ts-expect-error` justifications, dead exports,
     behavioral test signal over implementation-detail mocks).
   - **Documentation (`docs/`, `*.md`)**: Skip `make docs-build` (`markdownlint-cli2`, Mermaid, MADR ADR lint, VitePress
     dead-link check).

### 5.5 Documentation Tooling Integration Notes

- **VitePress exclusion**: When creating `docs/AGENTS.md`, update `docs/.vitepress/config.mts` so `srcExclude` and
  `llmstxt.ignoreFiles` include `'AGENTS.md'` alongside `'agents/**'`.
- **Markdown linting**: `docs/.markdownlint-cli2.jsonc` already globs `../*.md` and `**/*.md` (covering root
  `AGENTS.md`, `CODING_STANDARDS.md`, and `docs/AGENTS.md`). Add `../storefront/AGENTS.md` (or `../storefront/**/*.md`)
  when `storefront/` is created so nested `AGENTS.md` files remain linted by `make docs-build`.

---

## 6. Primary Sources

1. **AGENTS.md Open Specification** — Nested directory support and nearest-file precedence:
   [https://agents.md/](https://agents.md/)
2. **Anthropic — Claude Code Memory & Rules** — `CLAUDE.md`, `AGENTS.md` fallback, lazy-loaded nested directories,
   `.claude/rules/`, and <200-line guidance:
   [https://code.claude.com/docs/en/memory](https://code.claude.com/docs/en/memory)
3. **Anthropic Engineering — Effective Context Engineering for AI Agents** — Context rot, finite attention budget, and
   progressive disclosure:
   [https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)
4. **OpenAI Codex — Custom Instructions with `AGENTS.md`** — Startup root-to-CWD walk, `AGENTS.override.md`, and
   32 KiB `project_doc_max_bytes` shared budget:
   [https://developers.openai.com/codex/guides/agents-md](https://developers.openai.com/codex/guides/agents-md)
5. **Cursor Documentation — Rules** — Nested `AGENTS.md` implicit directory scoping vs. `.cursor/rules/*.mdc`:
   [https://cursor.com/docs/context/rules](https://cursor.com/docs/context/rules)
6. **GitHub Copilot Documentation — Repository Custom Instructions** — Nested `AGENTS.md` nearest-file precedence vs.
   `.github/instructions/*.instructions.md` (`applyTo`):
   [https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions](https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions)
7. **Gemini CLI Documentation — `GEMINI.md` / Context Files** — `context.fileName` (`AGENTS.md`), upward walk, and
   Just-In-Time (JIT) nested discovery:
   [https://geminicli.com/docs/cli/gemini-md/](https://geminicli.com/docs/cli/gemini-md/)
8. **OpenCode Documentation — Rules** — Upward directory traversal, `AGENTS.md`, and `opencode.json` `instructions`:
   [https://opencode.ai/docs/rules/](https://opencode.ai/docs/rules/)
9. **Gloaguen, Mündler, Müller, Raychev, Vechev (ETH Zurich & LogicStar.ai, Feb 2026)** — *Evaluating AGENTS.md: Are
   Repository-Level Context Files Helpful for Coding Agents?*:
   [https://arxiv.org/abs/2602.11988](https://arxiv.org/abs/2602.11988)
10. **Observe-It Internal Skills** — `.agents/skills/writing-for-agents/SKILL.md` (context pointers, context load vs.
    cognitive load, progressive disclosure, environment as source of truth, single source of truth) and
    `agy-customizations/docs/rules.md` (Antigravity upward CWD→root walk, 24 KB per-file cap, 20k-token budget).

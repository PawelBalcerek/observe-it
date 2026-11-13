# Documentation & Markdown Standards

## 1. Role-Based `docs/` Layout

Human-facing documentation lives under `docs/` and is published to GitHub Pages via VitePress (`docs/.vitepress/`).
Internal AI coding-assistant instructions in `docs/agents/` are excluded from compilation, routing, and local search
indexing via `srcExclude: ['agents/**']`:

```text
docs/
├── index.md                 ← Landing page: pitch, system diagram, Quick Start, roadmap, upstream links
├── guides/                  ← Living narrative and operational guides
│   ├── observability-stack.md
│   ├── structured-logging.md
│   ├── metrics-and-dashboards.md
│   └── exploring-telemetry.md
├── adr/                     ← Append-only Architecture Decision Records
│   ├── index.md
│   └── NNNN-<decision-slug>.md
└── agents/                  ← Agent instructions (excluded from the public VitePress site)
```

## 2. Living Guides vs. Append-Only Atomic ADRs

### Living Guides (`docs/guides/*.md`, `docs/index.md`, `README.md`)

Update guides in place whenever system architecture, configuration, or behavior changes so they always reflect the
current state of `main`.

Every `## H2` section in a narrative guide (`docs/guides/*.md`, excluding `docs/guides/index.md`) follows a **General
Pattern → External Sources → In Observe-It** progression:

1. **General Pattern First (Domain-Agnostic)**: Explain the transferable observability or engineering pattern first in
   domain-agnostic terms—what problem it solves, how the mechanism works in Spring Boot / SLF4J / OpenTelemetry /
   OpenSearch, and what rules of thumb apply in any production service. Keep the prose, bullet lists, code/key examples,
   and Mermaid diagrams in this part strictly domain-agnostic: use generic systems terminology (`request.id`, `user.id`,
   `entity.id`, `Ingress Filter`, `Domain Service`) and never leak Observe-It domain glossary terms (**Order**,
   **Checkout Scenario**), repository class/file/pipeline names (`MdcCorrelationFilter`, `observe-it-logs-pipeline`), or
   Observe-It-specific schema normalization decisions (such as promoting `body` → `message` or `severity.text` → `level`)
   before `### In Observe-It`.
2. **`### External Sources & Ecosystem Practice` (When Applicable)**: Where the pattern reflects broader industry or
   upstream practice, cite authoritative external sources (specifications, official framework manuals, or reference
   architectures such as `open-telemetry/opentelemetry-demo`). State only what each upstream source itself documents (and
   how it relates when Observe-It takes a different path), with zero unverified generalizations about industry adoption
   (such as `"often"`, `"most teams"`, or `"typically in the industry"`). When a mechanism is purely specific to
   Observe-It's sandbox design, omit external citations rather than forcing artificial links.
3. **`### In Observe-It` (Concrete Repository Example)**: Present Observe-It's implementation (`Order` domain classes,
   configuration files, and tests) as the concrete reference example illustrating the general pattern. When the **General
   Pattern** enumerates a taxonomy implemented in the repository (such as `DEBUG`, `INFO`, `WARN`, and `ERROR` log
   severities), illustrate every entry in `### In Observe-It`.

Write direct, plain technical prose in narrative guides: state each point once without restating a numbered list in a
trailing summary paragraph, and use concrete transitions instead of vague back-references (`also`).

### Append-Only Atomic ADRs (`docs/adr/NNNN-*.md` & `docs/adr/index.md`)

ADRs capture point-in-time decisions that pass the 3-part ADR test (**Hard to reverse**, **Surprising without context**,
and **Result of a real trade-off**):

- **Single-decision atomicity**: Scope each ADR to one architectural decision with a single cohesive lifecycle so future
  changes can supersede individual decisions cleanly without invalidating unrelated choices.
- **Immutable once accepted**: Keep accepted ADR bodies intact when a decision changes. Reversing or replacing a
  decision creates a new ADR (`docs/adr/MMMM-*.md`) and updates the prior ADR's status header and superseding banner.
- **Active ADR template (`Accepted`, MADR-style)**: Include all four H2 sections in order (`## Context`, `## Decision`,
  `## Considered Options`, `## Consequences`), enforced by `docs/scripts/lint-adr.mjs` during `make docs-build`:
  - In `## Considered Options`, explicitly state for each option whether it was **rejected** (ruled out due to a
    fundamental architectural or ergonomic mismatch) or **deferred** / **postponed** (not chosen for the current
    milestone scope, open to a future ADR).
  - In `## Consequences`, use an unordered list of `- **Good, because** ...`, `- **Bad, because** ...`, and
    `- **Neutral, because** ...` items with at least one `Good, because` and one `Bad, because` entry.

```markdown
# NNNN. Short Decision Title

**Status:** Accepted

## Context

What forces, constraints, and problem prompted this decision.

## Decision

What we decided and how it works in Observe-It.

## Considered Options

1. **Alternative A**:
   Alternative A was rejected because ...
2. **Alternative B**:
   Alternative B was deferred for Milestone X because ...

## Consequences

- **Good, because** ...
- **Bad, because** ...
- **Neutral, because** ...
```

Register every new ADR in both `docs/adr/index.md` and `docs/.vitepress/config.mts`.

- **Superseded ADR format**: Update the `**Status:**` line, insert a `> [!WARNING]` GFM alert immediately below it
  linking forward to the superseding ADR, and update the `Status` column in `docs/adr/index.md`:

```markdown
# NNNN. Short Decision Title

**Status:** Superseded by [ADR-MMMM](MMMM-new-decision-slug.md)

> [!WARNING]
> This decision has been superseded by [ADR-MMMM: New Decision Title](MMMM-new-decision-slug.md). It is retained as
> historical context.
```

The new ADR (`MMMM-new-decision-slug.md`) states `Supersedes [ADR-NNNN](NNNN-old-decision-slug.md)` in its `**Status:**`
line or `## Context` section.

## 3. Markdown Formatting, Code Blocks, Mermaid & Alerts

- **Zero YAML frontmatter**: Author `README.md`, `docs/index.md`, `docs/guides/*.md`, and `docs/adr/*.md` as pure
  Markdown without YAML frontmatter so files render cleanly in GitHub's native file viewer and VitePress infers page
  titles from the `# H1` heading.
- **Heading hierarchy**: Start every document with a single `# H1` title (`MD025`), followed by hierarchical `## H2` and
  `### H3` sections without skipping levels (`MD001`, matching VitePress `outline: [2, 3]`). In narrative guides
  (`docs/guides/*.md`, excluding `docs/guides/index.md`), open the first section with an explicit `## H2` heading
  immediately after `# H1`, and mark the progression steps inside each `## H2` section with
  `### External Sources & Ecosystem Practice` and `### In Observe-It` H3 subheadings rather than bold inline labels so
  every section and subsection is indexed in the VitePress table of contents.
- **120-character line wrapping & aligned tables**: Wrap prose paragraphs, blockquotes, and list items at 120 characters
  (`MD013` in `docs/.markdownlint-cli2.jsonc`, matching `.editorconfig`). Markdown tables (`tables: false`), fenced code
  blocks (`code_blocks: false`), headings (`headings: false`), and unbreakable inline URLs (`strict: false`) are exempt
  from line wrapping, and all Markdown tables must use vertically aligned column pipes (`MD060` with `style: "aligned"`).
- **Fenced code blocks**:
  - Declare a language identifier on every fenced code block (`kotlin`, `bash`, `yaml`, `json`, `text`, `mermaid`,
    `http`, `markdown`), enforced by `MD040`, and surround fences with blank lines (`MD031`).
  - Write runnable `bash` commands without `$` shell prompt prefixes so commands can be copy-pasted directly.
  - Pair concise, focused code or config excerpts with a link to the full source file rather than pasting entire classes
    or configuration files into guides.
- **Mermaid diagrams**:
  - Use fenced ```` ```mermaid ```` blocks (`flowchart LR`, `flowchart TD`, `sequenceDiagram`), rendered interactively
    with zoom, pan, and fullscreen controls via `vitepress-mermaid-renderer`.
  - Quote every node label containing parentheses, brackets, colons, or slashes (for example,
    `OA["Observed App (:8080)"]`) and keep node labels free of raw HTML tags.
  - All diagrams are validated via `mermaid.parse()` during linting (`docs/scripts/lint-mermaid.mjs`).
- **GitHub-Flavored Markdown alerts**: Use `> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`, and `> [!CAUTION]`
  sparingly for high-signal callouts. Keep alerts top-level and separated by prose rather than nested or placed
  back-to-back.

## 4. Cross-Linking, Upstream Docs & Domain Glossary

- **`docs/**` → `docs/**`**: Use relative `.md` paths (for example,
  `../adr/0001-zero-code-otel-javaagent-and-logback-bridge.md` or `./guides/observability-stack.md`), verified at build
  time by VitePress strict dead-link checking (`ignoreDeadLinks: false`).
- **`docs/**` → repository files outside `docs/`**: Use full GitHub URLs
  (`https://github.com/PawelBalcerek/observe-it/blob/main/...`) when linking to `README.md`, `CONTRIBUTING.md`,
  `CONTEXT.md`, `docker-compose.yml`, Gradle scripts, or Kotlin source files so links resolve both on the published
  GitHub Pages site (`https://pawelbalcerek.github.io/observe-it/`) and in GitHub's repository viewer.
- **Root `README.md` → repository files**: Use relative repository paths (`docs/guides/observability-stack.md`,
  `docker-compose.yml`) alongside a prominent link to the published GitHub Pages documentation site.
- **Upstream vendor documentation**: Link directly to official OpenTelemetry, OpenSearch, SS4O, and SLF4J documentation
  for vendor fundamentals.
- **Domain glossary (`CONTEXT.md`) adherence**: Use the canonical terms defined in `CONTEXT.md` (**Observed App**,
  **Traffic Generator**, **Order**, **Order State**, **Order Line Item**, **Checkout Scenario**, **SS4O Log Index**,
  **SS4O Metrics Index**) and avoid the listed _Avoid_ synonyms.

## 5. Verification (`make docs-build`)

Before committing changes to any `.md` file, docs configuration, or documentation tooling, run `make docs-build` (or
`make -C docs build`, or `make docs-lint` for lint-only checks) to execute `markdownlint-cli2`, `mermaid.parse()` syntax
validation (`docs/scripts/lint-mermaid.mjs`), MADR ADR validation (`docs/scripts/lint-adr.mjs`), `npm test`
(`docs/scripts/*.test.mjs`), and `vitepress build` with strict dead-link checking. When changes touch only documentation
or Markdown, `make docs-build` is the primary verification gate (without running `./gradlew check`).

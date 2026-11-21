# Docs Guidelines

Read [`agents/documentation.md`](agents/documentation.md) before editing any Markdown file, ADR, or VitePress
configuration.

- **Verification gate**: Run `make docs-build` from the repo root (or `make build` inside `docs/`).
- **Package manager**: `docs/` uses `npm` (`package-lock.json`), not `pnpm`.
- **VitePress exclusions**: `docs/AGENTS.md` and `docs/agents/**` are excluded from VitePress and `llms.txt` via
  `docs/.vitepress/config.mts`, while still linted by `markdownlint-cli2`.

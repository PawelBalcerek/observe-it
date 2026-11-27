# Browser Traffic Generator Guidelines

Read [`../docs/agents/browser-traffic.md`](../docs/agents/browser-traffic.md) before editing any file in
`browser-traffic-generator/`.

- **Package manager**: Standalone `pnpm` package (`packageManager` pinned in `package.json`); never couple it to
  `storefront/` or a shared workspace.
- **Uninstrumented invariant**: Zero `@opentelemetry/*` dependencies, imports, or OTLP export paths anywhere in this
  package (`ADR-0015`).
- **Verification**: Run `pnpm --dir browser-traffic-generator check` (or `make browser-traffic-check`) before
  committing.

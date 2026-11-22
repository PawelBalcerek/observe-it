# Storefront Guidelines

Read [`../docs/agents/frontend.md`](../docs/agents/frontend.md) before editing any file in `storefront/`.

- **Verification gate**: Run `make storefront-check` from the repo root (or `pnpm --dir storefront check`).
- **Package manager**: Standalone `pnpm` package (`packageManager` pinned in `package.json`); do not add root JS
  config files or a workspace manifest.
- **Architecture**: Strictly unidirectional imports (`app` → `features` → `components` / `telemetry` / `lib`), zero
  cross-feature deep imports, and all telemetry isolated inside `src/telemetry/`.
- **Scenario blindness**: Never send `X-Checkout-Scenario` from **Storefront** code.

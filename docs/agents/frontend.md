# Storefront Frontend Conventions

Applies to `storefront/` (React, Vite, TypeScript, Tailwind CSS, Biome, and Vitest). Compiler strictness, lint rules,
and formatting live in `storefront/tsconfig.json` and `storefront/biome.json`; run `pnpm --dir storefront check` to
verify types, linting, formatting, and unit tests before committing.

## 1. Feature-Based Architecture & Unidirectional Imports

Organize `storefront/src/` using a Feature-Based ("Bulletproof React") layout:

```text
storefront/src/
├── app/          ← Router, root layout, React Error Boundary, application bootstrap
├── features/     ← Vertical domain slices colocating components, hooks/state, API calls, and tests
│   ├── catalog/
│   ├── cart/
│   ├── checkout/
│   ├── orders/
│   └── diagnostics/
├── components/   ← Shared, stateless UI primitives reused across features
├── telemetry/    ← OpenTelemetry Web SDK bootstrap, domain span/log helpers, Web Vitals reporter
└── lib/          ← Shared API fetch client, formatting helpers, domain types
```

Enforce strictly unidirectional imports:

- **`app` → `features` → `components` / `telemetry` / `lib`**.
- Keep `components/`, `telemetry/`, and `lib/` free of imports from `features/` or `app/`.
- Keep features decoupled from one another: avoid cross-feature deep imports (such as importing another feature's
  internal components or hooks). Share cross-feature data through URL routes, domain types and React Context in
  `src/lib/`, or providers mounted in `src/app/`.
- Keep every exported symbol used by a caller in `storefront/src/` (no dead exports).
- Type React function components explicitly with `FC` / `FC<Props>`.

## 2. Semantic HTML & Accessible Locators

Build UI elements with native semantic HTML (`<main>`, `<nav>`, `<button>`, `<form>`, `<label>`, `<table>`, headings)
and explicit ARIA attributes when native semantics are insufficient:

- Give interactive controls clear, stable accessible names so users, Testing Library queries, and **Browser Traffic
  Generator** Playwright locators target roles and labels instead of CSS classes or `data-testid` hooks.
- Represent **Order State** values, validation feedback, and loading or error states in accessible text or status
  regions (`role="status"`, `role="alert"`).
- Use canonical `CONTEXT.md` terminology in code, labels, and tests (**Storefront**, **Observed App**, **Order**,
  **Order State**, **Order Line Item**, **Checkout Scenario**).

## 3. Telemetry Boundaries

Isolate all `@opentelemetry/*` and `web-vitals` imports inside `src/telemetry/` so UI features depend only on thin,
domain-named telemetry helpers:

- **Same-origin OTLP proxy (`ADR-0014`)**: Export traces to `/otlp/v1/traces` and logs to `/otlp/v1/logs` over the
  same-origin proxy (Vite `server.proxy` locally, Nginx in the container). Exclude `/otlp/` in `FetchInstrumentation`
  `ignoreUrls` to prevent trace recursion.
- **Traces (`@opentelemetry/sdk-trace`)**: Initialize `TracerProvider` with `ZoneContextManager`, W3C `traceparent` /
  `baggage` propagation, `service.name=storefront`, `service.version` (from `VITE_APP_VERSION`),
  `deployment.environment.name=local`, `browserDetector`, and focused instrumentations (`fetch`, `document-load`,
  `user-interaction` on `['click', 'submit']`). Expose thin custom domain span helpers (such as
  `storefront.checkout.start` and `storefront.checkout.complete`) from `src/telemetry/`.
- **Logs & Web Vitals (`@opentelemetry/sdk-logs`)**: Emit global `error` / `unhandledrejection` handlers, React Error
  Boundary crashes, structured workflow events, and Core Web Vitals (`LCP`, `CLS`, `INP`, `FCP`, `TTFB`) as
  `browser.web_vital` log events into the **SS4O Log Index**.
- **No browser metrics SDK**: Keep **Storefront** free of `@opentelemetry/sdk-metrics` and `/otlp/v1/metrics` exports
  (`ADR-0014`); span-derived RED metrics populate the **Prometheus Metrics Store** through Data Prepper.

## 4. Behavioral Testing (Vitest & Testing Library)

Colocate `*.test.ts` / `*.test.tsx` tests alongside the feature or module they verify:

- **Explicit `// given`, `// when`, `// then`, `// and` comments**: Structure every `it(...)` test with `// given`,
  `// when`, `// then`, and optional `// and` blocks (mirroring `docs/agents/testing.md` §5).
- **Reusable test fixtures (`src/test/`)**: Use minimal-override Object Mothers (`orderFixtures.ts`) and API fetch
  fixtures (`observedAppApiFixture.ts`) rather than ad-hoc inline `fetch` mocks or repeated payload literals.
- **Private render, interaction & expectation helpers**: Keep each UI test readable by extracting file-local arrow
  helpers for rendering (`renderCartPage`), user interactions (`userClicksProceedToPayment`), and domain assertions
  (`expectOrderState`, `expectAlert`).
- **Query like a user**: Prefer `getByRole`, `getByLabelText`, `findByText`, and `@testing-library/user-event` over DOM
  structure or CSS class assertions.
- **Verify behavior, not internals**: Assert rendered outcomes, navigation, API requests/responses, and error handling
  rather than internal React state or implementation details.
- **In-memory telemetry assertions**: Verify custom spans, error capture, and `browser.web_vital` log records with
  OpenTelemetry in-memory exporters (`InMemorySpanExporter`, `InMemoryLogRecordExporter`) rather than mocking SDK
  internals.
- **Suppressions**: Fix the underlying type or lint issue before reaching for `biome-ignore`, `@ts-expect-error`, or
  `@ts-ignore`.

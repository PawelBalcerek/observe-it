# Browser Traffic Generator Conventions

Applies to `browser-traffic-generator/` (standalone Node.js, TypeScript, `playwright-core`, Biome, Knip, and Vitest).
Compiler strictness, lint rules (including `@opentelemetry/*` import isolation), formatting, and dead-export checks live
in `browser-traffic-generator/tsconfig.json`, `browser-traffic-generator/biome.json`, and
`browser-traffic-generator/package.json`; run `pnpm --dir browser-traffic-generator check` before committing.

## 1. Standalone Package Layout

Maintain `browser-traffic-generator/` as an independent `pnpm` package with its own `package.json`, `pnpm-lock.yaml`,
and build configuration—never coupled to `storefront/` via a shared `pnpm` workspace (`ADR-0015`):

- Interact with **Storefront** strictly as a black-box user over HTTP URLs and semantic/ARIA Playwright locators
  (`getByRole`, `getByLabelText`).
- Target `playwright-core` with `chromium-headless-shell` on Linux `amd64`/`arm64` so containers stay lightweight and
  support full Core Web Vitals (`LCP`, `INP`, `CLS`).

## 2. Strictly Uninstrumented Invariant (`ADR-0015`)

Keep `browser-traffic-generator/` completely uninstrumented by OpenTelemetry:

- Zero `@opentelemetry/*` dependencies or imports anywhere in `browser-traffic-generator/`.
- Zero OTLP exporters, trace headers injected by a Node.js SDK, or log/metric bridges.
- All browser-originated distributed traces must start inside **Storefront**'s OpenTelemetry Web SDK so the **APM
  Service Map Index** shows authentic `storefront` → `observed-app` → `mongodb` edges without a synthetic runner node.

## 3. Browser Lifecycle, Isolation & Scenario Headers

Drive **Storefront** journeys with strict isolation and bounded memory:

- **Shared `Browser`, fresh `BrowserContext`**: Reuse a single headless Chromium `Browser` instance across sequential
  journeys, and open a fresh `BrowserContext` per **Checkout Scenario** (always closed in a `finally` block) for clean
  trace, cookie, and session boundaries.
- **Periodic `Browser` recycle**: Close and relaunch the `Browser` process periodically (every 50–100 journeys) to
  reclaim native Chromium/V8 memory fragmentation within the container memory budget.
- **`X-Checkout-Scenario` header attachment**: Set `X-Checkout-Scenario` via Playwright `BrowserContext`
  `extraHTTPHeaders` so every **Storefront** `/api/*` request carries the active **Checkout Scenario** into **Observed
  App** correlation without modifying **Storefront** code.
- **Weighted UI-realistic scenarios**: Drive the 6 user-realistic **Checkout Scenarios** (`HAPPY_PATH`,
  `LOW_STOCK_WARNING`, `OUT_OF_STOCK`, `PAYMENT_DECLINED`, `PAYMENT_GATEWAY_TIMEOUT`, `BUYER_CANCELLATION`) plus
  `BROWSER_DIAGNOSTICS` at a low steady cadence (~1 journey every 5–10 seconds, configurable via environment variables).
  Leave `INVALID_STATE_TRANSITION` to the Kotlin **Traffic Generator**.

## 4. `:8082` HTTP Control API Conventions

Expose a lightweight server using Node's built-in `http` module on port `8082` (no extra web framework required):

- `GET /health`: Liveness/readiness probe for Docker Compose health checks.
- `GET /status`: Current runner state (running/paused, active/completed journey counts, last scenario outcome).
- `POST /pause` and `POST /resume`: Pause or resume background synthetic journeys during manual exploration.
- `POST /trigger/{scenario}`: Deterministically trigger a single **Checkout Scenario** or `BROWSER_DIAGNOSTICS` journey
  (used by `:e2e:e2eTest` and manual testing), returning explicit 4xx JSON error responses for unknown scenario names.
- Group repeated HTTP methods, status codes, headers, and route paths into `as const` constant objects (`HTTP_METHOD`,
  `HTTP_STATUS`, `HTTP_HEADER`, `ROUTES`) rather than repeating raw literals inline.

## 5. Testing Conventions

- **Explicit `// given`, `// when`, `// then`, `// and` comments**: Structure every `it(...)` test with `// given`,
  `// when`, `// then`, and optional `// and` blocks.
- **Reusable test fixtures & domain expectation helpers**: Extract Object Mother factories, HTTP/Playwright fakes
  (`src/test/`), and domain-named `expect*` helpers (`expectBrowserSnapshots`, `expectResponse`, `expectStatus`,
  `expectJourneySteps`) rather than repeating inline mocks, payload literals, or multi-line raw `expect(...)` chains.
- **High-signal behavioral unit tests**: Keep unit tests fast and deterministic under Vitest; assert implemented
  behavior (scenario weight distribution, control API routes, header attachment wiring, `BrowserContext` cleanup, and
  periodic `Browser` recycle logic using in-memory fakes rather than launching real Chromium) and skip tests that
  restate static declarations/defaults, duplicate Biome/TypeScript checks, or assert unimplemented behavior ("never does
  X").
- Fix the underlying type or lint issue before reaching for `biome-ignore`, `@ts-expect-error`, or `@ts-ignore`.

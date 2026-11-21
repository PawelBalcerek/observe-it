# Modern React SPA Stack and JS/TS Workspace Tooling (2026)

**Issue**: [#180 — Research modern React SPA stack and monorepo tooling (2026)](https://github.com/PawelBalcerek/observe-it/issues/180)
**Parent Map**: [#179 — Frontend Observability & Browser Traffic](https://github.com/PawelBalcerek/observe-it/issues/179)
**Blocks**: [#183 — Decide frontend tech stack and repository workspace layout](https://github.com/PawelBalcerek/observe-it/issues/183)

## Executive Summary & Recommendation

For a lightweight, SPA-first React storefront living inside Observe-It's existing Gradle + VitePress + Docker Compose
repository, the 2026 primary-source ecosystem has converged on native Rust/Go tooling that makes small JS/TS projects
fast enough to avoid heavy monorepo orchestrators.

**Recommended 2026 Stack for Observe-It:**

- **Runtime & UI**: **React 19** (`^19.3.x`) + **TypeScript 7** (`~7.0.x`, Go-native compiler) with `tsconfig.json`
  project references (`tsconfig.app.json` + `tsconfig.node.json`), `erasableSyntaxOnly: true`, and
  `verbatimModuleSyntax: true`.
- **Bundler & Dev Server**: **Vite 8** (`^8.3.x`, unified **Rolldown + Oxc** Rust core) with `@vitejs/plugin-react`
  (`^6.1.x`). Defer React Compiler (`babel-plugin-react-compiler`) initially to keep builds Babel-free unless profiling
  justifies it.
- **Testing**: **Vitest 5** (`^5.x`) using `jsdom` + `@testing-library/react` for fast unit/component tests, with
  stable **Vitest Browser Mode** (`@vitest/browser-playwright` + `vitest-browser-react`) available if real-browser
  layout or Web API fidelity is needed at the component layer.
- **Linting & Formatting**: **Biome v2** (`^2.6.x`) as a single Rust binary (`biome ci` / `biome check --write`) with
  native type-aware rules, the `react` linter domain, and Tailwind v4 class sorting (`tailwind.stylesheet`). Strong
  runner-up: **Oxlint + Oxfmt** (now default in `create-vite`). Avoid ESLint 10 + Prettier unless a specific third-party
  ESLint plugin is required.
- **Styling & UI**: **Tailwind CSS v4** via `@tailwindcss/vite` (CSS-first `@import "tailwindcss";` and `@theme`
  tokens, zero `tailwind.config.js` or PostCSS boilerplate). Start with semantic HTML + Tailwind utilities; pull in
  copy-paste **shadcn/ui** (backed by **Base UI** or **Radix UI**) only if accessible dialogs, drawers, or toasts are
  needed.
- **Routing**: **React Router v7** (Library/Declarative mode) or **TanStack Router** (pure client-side SPA, no SSR).
- **Package Manager & Workspace Layout**:
  1. **Keep `docs/` (VitePress) isolated** in its own `docs/package.json` (`npm`, `docs/Makefile`, `pages.yml`). Do not
     fold `docs/` into a JS workspace with the React app.
  2. **Use `pnpm` (v10+/v12)** pinned via `packageManager`.
  3. **Start standalone** (e.g., `frontend/` with its own `package.json` and `pnpm-lock.yaml`) if only the React SPA is
     added; **adopt plain `pnpm` workspaces** (`pnpm-workspace.yaml` + `catalog:`) only if a second JS/TS package (such
     as a Playwright browser-runner from `#182`/`#185`) genuinely shares internal TypeScript code with the SPA.
  4. **Skip Turborepo**: for 2–3 small packages built with Vite 8, TypeScript 7, and Biome v2, Turborepo adds config
     and cache-invalidation overhead without meaningful speedup, and cannot safely cache non-deterministic live-stack
     browser tests.

---

## Recommended Stack at a Glance

| Layer               | Recommended Choice (2026)                           | Runner-Up / Alternative                    | Primary Source                                                                                |
| :------------------ | :-------------------------------------------------- | :----------------------------------------- | :-------------------------------------------------------------------------------------------- |
| **UI Framework**    | React 19 (`^19.3.1`)                                | — (constraint in `#179`)                   | [react.dev](https://react.dev/)                                                               |
| **Build / Dev**     | Vite 8 (`^8.3.5`, Rolldown + Oxc)                   | —                                          | [vite.dev](https://vite.dev/), [vitejs/vite](https://github.com/vitejs/vite)                  |
| **Language**        | TypeScript 7 (`~7.0.2`, Go-native `tsc`)            | TypeScript 6.0 (last JS-based release)     | [TypeScript Blog](https://devblogs.microsoft.com/typescript/)                                 |
| **Unit Testing**    | Vitest 5 (`^5.x`) + `jsdom` + Testing Library       | Vitest 5 Browser Mode (`playwright`)       | [vitest.dev](https://vitest.dev/)                                                             |
| **Lint & Format**   | Biome v2 (`^2.6.x`, single binary)                  | Oxlint + Oxfmt; ESLint 10 + Prettier       | [biomejs.dev](https://biomejs.dev/), [oxc.rs](https://oxc.rs/)                                |
| **Styling / UI**    | Tailwind CSS v4 (`@tailwindcss/vite`) + semantic UI | Tailwind v4 + shadcn/ui (Base UI / Radix)  | [tailwindcss.com](https://tailwindcss.com/), [base-ui.com](https://base-ui.com/)              |
| **Routing**         | React Router v7 (Library mode, SPA)                 | TanStack Router (type-safe search params)  | [reactrouter.com](https://reactrouter.com/), [tanstack.com/router](https://tanstack.com/router) |
| **Package Manager** | `pnpm` (pinned via `packageManager`)                | `npm` (already used in `docs/`)            | [pnpm.io](https://pnpm.io/)                                                                   |
| **Workspace**       | Standalone `pnpm` (or plain `pnpm-workspace.yaml`)  | Turborepo (not recommended at this scale)  | [pnpm.io/workspaces](https://pnpm.io/workspaces), [turborepo.dev](https://turborepo.dev/)     |

---

## Workspace Architecture Trade-Offs

### Observe-It Repository Context

Observe-It is a multi-module Gradle repository (`settings.gradle.kts` includes `observed-app`, `traffic-generator`,
`e2e`, and `build-logic`) orchestrated at the root by `Makefile` and `docker-compose.yml`. Today, its only JavaScript
project is `docs/`, which is intentionally self-contained:

- `docs/package.json` + `docs/package-lock.json` (`vitepress ^1.6.4`, `markdownlint-cli2`, `mermaid`, `npm ci`).
- `docs/Makefile` runs either local `npm` or `node:26-alpine` in Docker.
- `.github/workflows/pages.yml` triggers strictly on `*.md`, `docs/**`, and `.github/workflows/pages.yml`, with
  `cache: 'npm'` and `cache-dependency-path: 'docs/package-lock.json'`.
- `.github/dependabot.yml` tracks `/docs` under `package-ecosystem: "npm"` with a Mermaid `< 12` ignore rule.

### Side-by-Side Comparison: Standalone vs. Plain pnpm Workspaces vs. Turborepo

| Dimension                   | Option A: Standalone Package(s) (`frontend/` + isolated `docs/`)            | Option B: Plain `pnpm` Workspaces (`pnpm-workspace.yaml` + `catalog:`)           | Option C: `pnpm` Workspaces + Turborepo (`turbo.json`)                               |
| :-------------------------- | :-------------------------------------------------------------------------- | :------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------- |
| **Repo Root Footprint**     | **Zero JS files at root**; Kotlin/Gradle root stays clean                   | Adds root `package.json`, `pnpm-workspace.yaml`, `pnpm-lock.yaml` (or under `web/`) | Same as Option B, plus `turbo.json` and `.turbo/` cache directory                    |
| **Impact on `docs/`**       | **None** — `docs/Makefile`, `pages.yml`, and `dependabot.yml` stay untouched | Best if `docs/` stays **excluded**; including `docs/` couples `pages.yml` to root lockfile | Same as Option B; VitePress 1.6 bundles an older Vite major, offering zero dep sharing |
| **Shared Internal TS Code** | Not supported cleanly without relative imports or local file links          | **First-class** via `workspace:*` protocol (e.g., shared API DTOs or test IDs)   | Same as Option B (Turborepo delegates workspace linking to `pnpm`)                   |
| **Dependency Alignment**    | Per-package lockfile; Dependabot tracks each directory independently        | **Centralized** via `catalog:` in `pnpm-workspace.yaml`; supported by Dependabot | Same as Option B                                                                     |
| **Task Execution**          | Per-directory `pnpm --dir frontend ...` or `Makefile` targets               | Built-in `pnpm -r` (topological order) and `pnpm --filter <pkg>`                 | Parallel DAG scheduling + local/remote artifact caching (`turbo run build test lint`) |
| **Caching Value (2–3 pkgs)** | Native tool speed (Vite 8 + TS 7 + Biome v2) is already ~1–3s               | Same (~1–3s); CI path filters skip untouched directories completely              | Marginal local gain; **cannot safely cache** non-deterministic live-stack browser runs |
| **Docker Build Ergonomics** | **Simplest**: `COPY frontend/package.json frontend/pnpm-lock.yaml`          | Requires copying root lockfile + `pnpm-workspace.yaml` + `pnpm deploy --filter`  | Requires `turbo prune <app> --docker` step in Dockerfile                             |
| **Maintenance Overhead**    | **Lowest**                                                                  | **Low** (1 YAML file, zero extra CLI binaries)                                   | **Moderate** (`turbo.json` inputs/outputs, env-var hashing, extra dependency)        |

### Why `docs/` Should Stay Decoupled

1. **Zero shared dependencies**: `docs/` runs VitePress (`^1.6.4`, Vue-based, bundling an older Vite major),
   `markdownlint-cli2`, and `mermaid`. The storefront SPA runs React 19, Vite 8, Tailwind CSS v4, and OpenTelemetry
   browser SDK packages. Merging them into one lockfile shares virtually nothing.
2. **CI isolation**: `.github/workflows/pages.yml` currently triggers only on `docs/**` and `*.md`. Pulling `docs/`
   into a root `pnpm-lock.yaml` forces either unnecessary Pages rebuilds on every React/Playwright dependency bump or
   brittle path exclusions.
3. **Zero-Node Docker fallback**: `docs/Makefile` already gives Kotlin-only contributors a working `node:26-alpine`
   Docker fallback without touching root workspace tooling.

### Why Turborepo Is Overkill Here

Turborepo's official documentation highlights two primary strengths: **task-graph scheduling with local/remote caching**
for large multi-package monorepos ([Turborepo Caching](https://turborepo.dev/docs/crafting-your-repository/caching),
[Constructing CI](https://turborepo.dev/docs/crafting-your-repository/constructing-ci)). Neither solves a real
bottleneck in Observe-It:

- **Native-speed tools erase the cold-build bottleneck**: With Vite 8 (Rolldown + Oxc), TypeScript 7 (Go), and Biome v2
  (Rust), a full lint + typecheck + production build of a lightweight storefront SPA takes only a few seconds.
- **Non-deterministic tasks cannot be cached**: Turborepo's own caching guide explicitly states that *"Turborepo assumes
  that your tasks are deterministic. If a task is able to produce different outputs given the set of inputs that
  Turborepo is aware of, caching may not work as expected."* A browser-runner package exercising a live Docker Compose
  stack (`observed-app`, OTel Collector, OpenSearch) is inherently non-deterministic and must disable caching
  (`"cache": false`).
- **Gradle + GitHub Actions already orchestrate the repo**: Observe-It already uses `Makefile`, Gradle build cache, and
  path-filtered GitHub Actions workflows. Adding a second build orchestrator (`turbo.json`) for 1–2 JS packages adds
  mental overhead without solving a pain point.

### Workspace Recommendation & Upgrade Path

- **If `#182`/`#185` results in a single JS/TS package** (the React SPA only, or a browser runner that shares no
  TypeScript code with the SPA): use **Option A (standalone `pnpm` package at `frontend/`)**. Keep the repository root
  free of JS config files and keep Dockerfiles trivial.
- **If `#182`/`#185` adds a JS/TS browser runner that shares internal code** (e.g., Checkout Scenario metadata, API
  types, or `data-testid` contracts) with the SPA: use **Option B (plain `pnpm` workspaces)** with `pnpm-workspace.yaml`
  and `catalog:` — either scoped under a dedicated parent folder (e.g., `web/pnpm-workspace.yaml` containing
  `storefront/` and `browser-runner/`, keeping the Gradle root spotless) or at the repo root while excluding `docs/`.
- Migrating from Option A to Option B later is a mechanical, non-breaking change (add `pnpm-workspace.yaml` and merge
  lockfiles).

---

## Detailed Evaluation by Stack Layer

### 1. React 19 & React Compiler

React 19 (`^19.3.1` in Vite 8's official `create-vite` `template-react-ts`) is the current stable line
([vitejs/vite `template-react-ts/package.json`](https://github.com/vitejs/vite/blob/main/packages/create-vite/template-react-ts/package.json)).
Key React 19 features directly useful in a lightweight SPA without SSR:

- **Actions & form state (`useActionState`, `useOptimistic`)**: Native pending, error, and optimistic UI handling for
  checkout and order-mutation forms without third-party form/state libraries ([react.dev](https://react.dev/)).
- **`ref` as a standard prop**: Eliminates `forwardRef` boilerplate across UI components.
- **`use()` hook**: Reads promises and context directly inside render with `<Suspense>`.

**React Compiler (1.0+)**: React's official Vite integration requires `babel-plugin-react-compiler` plus
`@rolldown/plugin-babel` alongside `@vitejs/plugin-react` v6 (`reactCompilerPreset()`)
([react.dev — React Compiler](https://react.dev/learn/react-compiler)). Because adding Babel re-introduces a JS AST
pass on top of Vite 8's native Rust Oxc pipeline, **deferring React Compiler** is sensible for a small storefront SPA
where manual memoization is rarely needed; it can be opted in cleanly later if profiling warrants it.

### 2. Vite 8 (Rolldown + Oxc)

Released stable on March 12, 2026 (current `8.3.x`), **Vite 8** replaces the legacy dual-bundler architecture (esbuild
in dev + Rollup in prod) with a unified Rust toolchain ([Vite 8.0 Announcement](https://vite.dev/blog/announcing-vite8)):

- **Rolldown** handles both dev and production bundling while preserving Rollup/Vite plugin compatibility.
- **Oxc** handles TypeScript/JSX parsing, transformation, and minification.
- **Lightning CSS** handles CSS minification.
- **Native `resolve.tsconfigPaths`** removes the need for `vite-tsconfig-paths`.

Because Vite 8 unifies dev and prod bundling, it eliminates dev/prod behavior drift and makes production Docker image
builds fast out of the box.

### 3. TypeScript 7 & `tsconfig` Configuration

TypeScript's 2026 release timeline culminated in two major milestones on the official TypeScript blog
([devblogs.microsoft.com/typescript](https://devblogs.microsoft.com/typescript/)):

- **TypeScript 6.0 (March 2026)**: Final JS-based transition release aligning defaults and deprecations for the native
  port.
- **TypeScript 7.0 (July 2026)**: Stable Go-native compiler port (`~7.0.2` in Vite 8's official `template-react-ts`),
  delivering roughly 8x–12x faster type checking and lower memory usage with semantic parity.

Vite 8's official `template-react-ts` splits TypeScript configuration via project references (`tsconfig.json` →
`tsconfig.app.json` for browser `src/` + `tsconfig.node.json` for `vite.config.ts`) and runs `"build": "tsc -b && vite build"`
([vitejs/vite `template-react-ts`](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts)).
Recommended strict compiler flags from TypeScript 5.8–7.0 and Vite 8 docs:

```json
{
  "compilerOptions": {
    "target": "ES2023",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noEmit": true,
    "verbatimModuleSyntax": true,
    "erasableSyntaxOnly": true,
    "moduleDetection": "force",
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  }
}
```

- `erasableSyntaxOnly: true` (introduced in TypeScript 5.8) forbids runtime-emitting TS syntax (`enum`, parameter
  properties, runtime namespaces), guaranteeing compatibility with Oxc type-stripping and Node.js native TS execution
  ([TypeScript 5.8 Release Notes](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-5-8.html)).
- `verbatimModuleSyntax: true` enforces explicit `import type` so bundlers never accidentally retain type-only imports.

### 4. Vitest 5 & Component Testing Strategy

Vitest shares Vite's exact config and transform pipeline, making it the default test runner for Vite SPAs:

- **Vitest 4.0 (October 2025)** graduated **Browser Mode** to stable with dedicated provider packages
  (`@vitest/browser-playwright`, `@vitest/browser-webdriverio`) ([Vitest 4.0](https://vitest.dev/blog/vitest-4)).
- **Vitest 5.0 (September 2026)** added a built-in **Trace View** (`browser.traceView: true`) and adaptive browser
  session prewarming; **Vitest 5.1 (November 2026)** added import-map-based module mocking in Browser Mode
  ([Vitest 5.0](https://vitest.dev/blog/vitest-5), [Vitest Guide](https://vitest.dev/guide/browser/)).

| Test Environment                                    | Strengths                                                        | Trade-Offs                                                      | Best Fit in Observe-It                                           |
| :-------------------------------------------------- | :--------------------------------------------------------------- | :-------------------------------------------------------------- | :--------------------------------------------------------------- |
| **`jsdom` + `@testing-library/react`**              | Zero browser binary needed in unit CI; fast; mature DOM APIs     | Simulated DOM (no real CSS layout, `fetch` / Performance APIs)  | Default for fast unit & React component tests (`pnpm test`)      |
| **`happy-dom` + `@testing-library/react`**          | Faster than `jsdom`                                              | Less complete Web API fidelity than `jsdom`                     | Optional speed optimization if `jsdom` ever slows down           |
| **Vitest Browser Mode (`@vitest/browser-playwright`)** | Real Chromium engine, real OTel/Performance/DOM APIs, Trace View | Requires Playwright Chromium binary in CI (~150–300 MB)         | Valuable if testing real browser OTel instrumentation in isolation |

**Recommendation**: Start with `vitest` + `jsdom` + `@testing-library/react` for unit/component tests (fast, zero
browser-binary overhead in the frontend CI job), and keep Vitest Browser Mode as an opt-in project config if
browser-specific telemetry hooks need real-Chromium unit coverage outside `:e2e:e2eTest`.

### 5. Linting & Formatting: Biome v2 vs. Oxlint/Oxfmt vs. ESLint 10 + Prettier

| Criterion                 | Biome v2 (`^2.6.x`)                                               | Oxlint + Oxfmt (VoidZero / Oxc)                              | ESLint 10 + Prettier                                             |
| :------------------------ | :---------------------------------------------------------------- | :----------------------------------------------------------- | :--------------------------------------------------------------- |
| **Tool Count & Config**   | **1 binary, 1 file** (`biome.jsonc`); lint + format + imports     | 2 binaries (`oxlint` + `oxfmt`, or bundled via Vite+)        | Multiple packages (`eslint`, `typescript-eslint`, React plugins, `prettier`) |
| **Type-Aware Linting**    | **Native in Rust** (Biotype, no `tsc` invocation; e.g. `noFloatingPromises`) | Supported via type-aware engine (`oxlint`)                   | Full `tsc` type service (`typescript-eslint`), slowest in CI     |
| **React 19 Rules**        | Built-in `react` domain (`useHookAtTopLevel`, `useExhaustiveDependencies`, `useJsxKeyInIterable`, nursery `useReactCompiler`) | 800+ ESLint-compatible rules including React & Hooks; default in `create-vite` | Broadest plugin ecosystem; ESLint 10 (Feb 2026) dropped `.eslintrc` and broke some older React plugins |
| **Tailwind v4 Sorting**   | **Built-in** in v2.6 via `tailwind.stylesheet` + `useTailwindSortedClasses` | Built-in Tailwind sorting in `oxfmt`                         | Requires `prettier-plugin-tailwindcss`                           |
| **CI Ergonomics**         | Single `biome ci` command (mirrors Observe-It's `./gradlew check` style) | Two commands (`oxlint && oxfmt --check`)                     | Two commands + config glue (`eslint-config-prettier`)            |

**Primary sources**: [Biome v2 Blog](https://biomejs.dev/blog/biome-v2/),
[Biome v2.6 Blog](https://biomejs.dev/blog/biome-v2-6/), [Oxc Docs](https://oxc.rs/),
[ESLint v10.0.0 Release](https://eslint.org/blog/2026/02/eslint-v10.0.0-released/).

**Recommendation**: **Biome v2** is the cleanest fit for Observe-It. It mirrors Observe-It's Kotlin quality gate
(`ktlint` + `detekt` behind a single `./gradlew check`) with one binary, one config file (`biome.jsonc`), native
type-aware checks, React hooks rules, and Tailwind v4 class sorting. **Oxlint + Oxfmt** is a strong runner-up if the
team prefers staying 100% inside the Vite/Oxc toolchain.

### 6. Styling & UI Primitives: Tailwind CSS v4

**Tailwind CSS v4** ships a first-party Vite plugin (`@tailwindcss/vite`) that integrates directly with Vite's module
graph, eliminating `postcss.config.js`, `autoprefixer`, and `tailwind.config.js`
([Tailwind CSS v4.0](https://tailwindcss.com/blog/tailwindcss-v4)). Configuration lives directly in CSS:

```css
@import "tailwindcss";

@theme {
  --font-sans: "Inter", system-ui, sans-serif;
  --color-brand-600: oklch(0.55 0.18 250);
}
```

For UI components in a lightweight storefront (product catalog, cart/checkout form, scenario selector, order status
view):

- **Default**: Semantic HTML (`<button>`, `<form>`, `<dialog>`, `<details>`) styled with Tailwind CSS v4 utilities keeps
  the bundle minimal and avoids heavy component-library lock-in.
- **If accessible interactive primitives are needed** (toasts, popovers, comboboxes, modals): copy-paste **shadcn/ui**
  components — which natively support React 19 (no `forwardRef`) and Tailwind v4 OKLCH/`@theme` tokens, and now support
  both **Base UI** (`@base-ui-components/react`) and **Radix UI** as underlying headless primitives
  ([ui.shadcn.com](https://ui.shadcn.com/), [base-ui.com](https://base-ui.com/), [radix-ui.com](https://www.radix-ui.com/)).

### 7. Client-Side Routing

Observe-It `#179` explicitly scopes out SSR and Next.js/BFF meta-frameworks. For a 3–4 screen SPA (Catalog, Cart /
Checkout, Order Confirmation / Status), two modern client-side routers stand out:

- **React Router v7 (Library / Declarative Mode)**: Minimal footprint (`react-router` package), familiar `<BrowserRouter>`
  and `<Routes>` API, zero build plugin required, and cleanly compatible with static Nginx/Caddy `try_files` SPA
  fallback ([reactrouter.com](https://reactrouter.com/)).
- **TanStack Router**: 100% inferred TypeScript route params and validated search params (useful if Checkout Scenario
  filters or order IDs live in URL query params), at the cost of a small Vite route-generation plugin
  ([tanstack.com/router](https://tanstack.com/router)).

**Recommendation**: Default to **React Router v7 in Library mode** for lowest setup complexity; choose **TanStack
Router** only if URL search-parameter state becomes a central part of the storefront UX.

### 8. Package Manager: `pnpm` vs. `npm`

**pnpm** (v10–v12, pinned via `"packageManager": "pnpm@<exact-version>"` in `package.json`) offers three concrete
advantages over `npm` for a modern TS project ([pnpm.io](https://pnpm.io/)):

1. **Strict `node_modules`**: Prevents phantom dependency bugs (importing transitive packages not declared in
   `package.json`).
2. **Content-addressable store & fast CI**: Significantly faster installs and smaller disk footprint than `npm ci`,
   especially when Docker layers or multiple JS packages are involved.
3. **First-class workspaces & `catalog:`**: Built-in `pnpm-workspace.yaml` catalogs centralize shared versions if a
   second JS/TS package is added later, and **GitHub Dependabot** natively supports `pnpm-lock.yaml` and `pnpm`
   workspace catalogs under `package-ecosystem: "npm"`
   ([GitHub Dependabot pnpm catalogs](https://github.blog/changelog/)).

Using `pnpm` for the new frontend does not require migrating `docs/` off `npm` — keeping `docs/` untouched avoids
unnecessary churn in `docs/Makefile` and `.github/workflows/pages.yml`.

---

## Decision Inputs for Ticket `#183`

Ticket `#183` (*Decide frontend tech stack and repository workspace layout*) can lock down four concrete choices using
these findings:

1. **Core SPA stack**: Confirm React 19 + Vite 8 + TypeScript 7 + Tailwind CSS v4 (`@tailwindcss/vite`) + Vitest 5
   (`jsdom` + Testing Library) + Biome v2 + React Router v7 (Library mode).
2. **Package manager**: Confirm `pnpm` (pinned via `packageManager`) for the new frontend, leaving `docs/` on `npm`.
3. **Workspace topology** (coordinated with `#185` synthetic traffic decision):
   - Standalone `frontend/` if only the React SPA lives in JS/TS (or if a browser runner shares no TS code).
   - Plain `pnpm` workspaces (`pnpm-workspace.yaml` + `catalog:`, no Turborepo, `docs/` excluded) if a JS/TS browser
     runner shares internal TypeScript packages with the storefront.
4. **CI & `Makefile` integration**: Add a path-filtered frontend CI workflow (`biome ci`, `tsc -b`, `vitest run`,
   `vite build`), matching `Makefile` targets, and a Dependabot entry (`package-ecosystem: "npm"`, `directory: "/frontend"`).

---

## Primary Sources

- React 19 & React Compiler (Vite integration): [react.dev](https://react.dev/),
  [react.dev/learn/react-compiler](https://react.dev/learn/react-compiler)
- Vite 8 (Rolldown + Oxc) & `template-react-ts`:
  [vite.dev/blog/announcing-vite8](https://vite.dev/blog/announcing-vite8),
  [vitejs/vite `packages/create-vite/template-react-ts`](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts)
- TypeScript 5.8–7.0 (`erasableSyntaxOnly`, TS 6.0 transition, TS 7.0 native Go port):
  [devblogs.microsoft.com/typescript](https://devblogs.microsoft.com/typescript/),
  [TypeScript 5.8 Release Notes](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-5-8.html)
- Vitest 4 & 5 (Browser Mode, Trace View, import-map mocks):
  [vitest.dev/blog/vitest-4](https://vitest.dev/blog/vitest-4),
  [vitest.dev/blog/vitest-5](https://vitest.dev/blog/vitest-5),
  [vitest.dev/guide/browser](https://vitest.dev/guide/browser/)
- Biome v2 & v2.6 (Biotype type-aware linting, linter domains, Tailwind v4 sorting):
  [biomejs.dev/blog/biome-v2](https://biomejs.dev/blog/biome-v2/),
  [biomejs.dev/blog/biome-v2-6](https://biomejs.dev/blog/biome-v2-6/)
- Oxc (`oxlint`, `oxfmt`) & ESLint 10: [oxc.rs](https://oxc.rs/),
  [eslint.org/blog/2026/02/eslint-v10.0.0-released](https://eslint.org/blog/2026/02/eslint-v10.0.0-released/)
- Tailwind CSS v4 (`@tailwindcss/vite`, `@theme`), shadcn/ui, Base UI, Radix UI:
  [tailwindcss.com/blog/tailwindcss-v4](https://tailwindcss.com/blog/tailwindcss-v4),
  [ui.shadcn.com](https://ui.shadcn.com/), [base-ui.com](https://base-ui.com/),
  [radix-ui.com](https://www.radix-ui.com/)
- React Router v7 & TanStack Router: [reactrouter.com](https://reactrouter.com/),
  [tanstack.com/router](https://tanstack.com/router)
- pnpm Workspaces, Catalogs, & `packageManager` pinning:
  [pnpm.io/workspaces](https://pnpm.io/workspaces), [pnpm.io/catalogs](https://pnpm.io/catalogs),
  [pnpm.io/filtering](https://pnpm.io/filtering)
- Turborepo Caching & CI:
  [turborepo.dev/docs/crafting-your-repository/caching](https://turborepo.dev/docs/crafting-your-repository/caching),
  [turborepo.dev/docs/crafting-your-repository/constructing-ci](https://turborepo.dev/docs/crafting-your-repository/constructing-ci)

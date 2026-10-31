# Static Site Generators and GitHub Pages Deployment for Observe-It

**Issue**: [#56 — Evaluate static site generators and GitHub Pages deployment for Observe-It](https://github.com/PawelBalcerek/observe-it/issues/56)
**Parent Map**: [#54 — Milestone 1 Documentation, Standards, and GitHub Pages](https://github.com/PawelBalcerek/observe-it/issues/54)

## Executive Summary & Recommendation

**Recommended Primary Choice: VitePress** (with all Node/VitePress files isolated inside `docs/` — i.e., `docs/.vitepress/config.mts` and `docs/package.json`).
**Strong Runner-Up (Zero-Node / Docker-First Alternative): MkDocs Material**.

Why **VitePress** wins for Observe-It:
1. **Native GitHub-Flavored Alerts (`> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`, `> [!CAUTION]`)**: Enabled out of the box (`markdown.gfmAlerts: true` by default) via `markdown-it-github-alerts`, meaning Markdown files render identically when browsed directly in the GitHub web UI and on the published GitHub Pages site.
2. **Zero Frontmatter Required on Plain Markdown (`docs/adr/NNNN-*.md`)**: Unlike Astro Starlight (which enforces a required `title` in YAML frontmatter via Zod schema validation and fails the build without it), VitePress natively infers page titles from the first `# H1` heading. Standard ADR files (`# 0001. Use OpenSearch and SS4O...`) render as-is without requiring frontmatter boilerplate.
3. **Clean `docs/agents/**` Exclusion**: First-class `srcExclude: ['agents/**', 'research/**']` in `docs/.vitepress/config.mts` excludes agent instructions from compilation, routing, and built-in local search indexing while keeping them in `docs/agents/` in git.
4. **Zero Root-Directory Clutter**: By placing `package.json`, `package-lock.json`, and `.vitepress/` inside `docs/` (or keeping only `docs/.vitepress/`), the repository root remains a clean Kotlin/Gradle + Docker workspace (`build.gradle.kts`, `settings.gradle.kts`, `docker-compose.yml`).
5. **First-Class Local Search & Fast Dev Server**: Built-in MiniSearch (`themeConfig.search.provider: 'local'`) requires no external Algolia keys, and `vitepress-plugin-mermaid` adds client-side theme-aware Mermaid rendering for ` ```mermaid ` fenced code blocks.

---

## Side-by-Side Comparison Matrix

| Requirement / Dimension | VitePress (v1.x) | MkDocs Material (v9.x) | Astro Starlight | Docusaurus (v3.x) |
| :--- | :--- | :--- | :--- | :--- |
| **Mermaid (` ```mermaid `)** | Community plugin (`vitepress-plugin-mermaid`) wrapping `mermaid` | **Built-in** via `pymdownx.superfences` custom fence in `mkdocs.yml` | Community plugin (`astro-mermaid` or `rehype-mermaid`) | **First-party plugin** (`@docusaurus/theme-mermaid` + `markdown.mermaid: true`) |
| **GFM Alerts (`> [!NOTE]`)** | **Native (enabled by default)** via `markdown.gfmAlerts: true` | Requires 3rd-party plugin (`mkdocs-callouts` or `pymdownx.blocks.admonition`); native syntax is `!!! note` | Requires 3rd-party plugin (`starlight-github-alerts`); native syntax is `:::note` | Requires 3rd-party remark plugin (`remark-github-admonitions-to-directives`); native syntax is `:::note` |
| **Plain Markdown / ADR (`docs/adr/NNNN-*.md`) compatibility** | **High** — no frontmatter required; infers title from `# H1`; standard CommonMark/GFM | **High** — no frontmatter required; infers title from `# H1`; standard Markdown | **Low out-of-the-box** — `docsSchema()` requires YAML frontmatter `title:` on every `.md` file | **Medium** — MDX v3 parser is strict by default on `<` and `{` unless `markdown.format: 'detect'` is set |
| **Excluding `docs/agents/**`** | **Built-in**: `srcExclude: ['agents/**']` in `.vitepress/config.mts` | **Built-in** (MkDocs 1.5+): `exclude_docs: agents/` in `mkdocs.yml` | **Built-in**: negated glob pattern `['**/*.md', '!agents/**']` in `glob()` loader | **Built-in**: `exclude: ['agents/**']` in `@docusaurus/plugin-content-docs` |
| **Repo Root Footprint** | **Minimal**: can live entirely inside `docs/.vitepress/` + `docs/package.json` (0 files at repo root) | **Minimal**: single `mkdocs.yml` (can even live in `docs/mkdocs.yml` via `-f docs/mkdocs.yml`) | **Moderate**: full Astro project (`astro.config.mjs`, `src/content.config.ts`, `tsconfig.json`, `package.json`) best isolated in a sub-directory | **Heavy**: full React SPA scaffold (`docusaurus.config.ts`, `sidebars.ts`, `src/`, `static/`, `package.json`, `tsconfig.json`) |
| **Local Preview DX (Kotlin + Docker repo)** | `npm --prefix docs run dev` or one-liner `docker run` with `node:22-alpine` | `docker run --rm -it -p 8000:8000 -v ${PWD}:/docs squidfunk/mkdocs-material` (or `pipx` / `uvx`) | `npm --prefix docs run dev` | `npm --prefix docs start` |
| **Built-in Offline Search** | **Built-in** (`search: { provider: 'local' }` via MiniSearch) | **Built-in** (`plugins: [search]`) | **Built-in** (Pagefind) | Requires 3rd-party plugin (e.g., `@easyops-cn/docusaurus-search-local`) or Algolia DocSearch |

---

## Detailed Evaluation by Requirement

### 1. Mermaid Diagram Support (` ```mermaid `)

Observe-It documentation (ADRs, Observability Stack Overview, and Checkout Scenario / telemetry flow diagrams for the **Observed App**, **Traffic Generator**, and **SS4O Log Index**) relies on standard GitHub-compatible ` ```mermaid ` fenced code blocks so diagrams render both on GitHub and on the documentation site.

* **VitePress**:
  * VitePress does not bundle Mermaid in core ([vuejs/vitepress#1998](https://github.com/vuejs/vitepress/issues/1998)), and recommends [`vitepress-plugin-mermaid`](https://emersonbottero.github.io/vitepress-plugin-mermaid/).
  * Setup wraps `defineConfig` with `withMermaid({ ... })` in `docs/.vitepress/config.mts` and installs `vitepress-plugin-mermaid` + `mermaid`. It automatically switches between light and dark Mermaid themes when the user toggles the VitePress color scheme.
* **MkDocs Material**:
  * **First-class built-in support** ([Material for MkDocs — Diagrams](https://squidfunk.github.io/mkdocs-material/reference/diagrams/)).
  * Enabled solely via `mkdocs.yml` using `pymdownx.superfences` (bundled with `mkdocs-material`, no extra pip packages required):
    ```yaml
    markdown_extensions:
      - pymdownx.superfences:
          custom_fences:
            - name: mermaid
              class: mermaid
              format: !!python/name:pymdownx.superfences.fence_code_format
    ```
  * Material for MkDocs automatically lazy-loads Mermaid.js only on pages containing diagrams and adapts diagram palettes to light/dark mode.
* **Astro Starlight**:
  * Starlight does not include native Mermaid support out of the box ([withastro/starlight#442](https://github.com/withastro/starlight/discussions/442)).
  * Requires either a client-side integration such as [`astro-mermaid`](https://github.com/joesaby/astro-mermaid) or build-time rendering via [`rehype-mermaid`](https://github.com/remcohaszing/rehype-mermaid) (which requires Playwright/Chromium in CI if using `inline-svg`).
* **Docusaurus**:
  * **Official first-party package**: [`@docusaurus/theme-mermaid`](https://docusaurus.io/docs/markdown-features/diagrams).
  * Enabled by setting `markdown: { mermaid: true }` and `themes: ['@docusaurus/theme-mermaid']` in `docusaurus.config.ts`. Supports light/dark mode configuration natively.

### 2. GitHub-Flavored Callouts / Alerts (`> [!NOTE]`, `> [!TIP]`, etc.)

Using standard GitHub blockquote alert syntax (`> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`, `> [!CAUTION]`) ensures that readers viewing `.md` files directly in the repository on GitHub see properly styled callouts rather than broken custom directives (`:::note` or `!!! note`).

* **VitePress**:
  * **Native out-of-the-box support** ([VitePress Markdown Extensions — GitHub-Styled Alerts](https://vitepress.dev/guide/markdown#github-styled-alerts)).
  * Powered by `markdown-it-github-alerts`, enabled by default (`markdown: { gfmAlerts: true }`). Renders all five GitHub alert types (`NOTE`, `TIP`, `IMPORTANT`, `WARNING`, `CAUTION`) using VitePress custom container styles while also supporting `::: tip` syntax.
* **MkDocs Material**:
  * Native admonition syntax uses indented `!!! note` or `??? note` blocks ([Material for MkDocs — Admonitions](https://squidfunk.github.io/mkdocs-material/reference/admonitions/)).
  * Standard `> [!NOTE]` blockquotes are **not** transformed by default; supporting them requires installing the third-party [`mkdocs-callouts`](https://github.com/oprypin/mkdocs-callouts) or [`python-markdown-github-alerts`](https://pypi.org/project/markdown-github-alerts/) package alongside `admonition` in `mkdocs.yml`.
* **Astro Starlight**:
  * Native aside syntax uses `:::note` container directives ([Starlight Authoring Content — Asides](https://starlight.astro.build/guides/authoring-content/#asides)).
  * Supporting `> [!NOTE]` syntax requires the community plugin [`starlight-github-alerts`](https://github.com/HiDeoo/starlight-github-alerts).
* **Docusaurus**:
  * Native admonition syntax uses `:::note` container directives ([Docusaurus Admonitions](https://docusaurus.io/docs/markdown-features/admonitions)).
  * Supporting `> [!NOTE]` syntax requires configuring [`remark-github-admonitions-to-directives`](https://github.com/incentro-dc/remark-github-admonitions-to-directives) in `beforeDefaultRemarkPlugins`.

### 3. Rendering Directly from `docs/` (including `docs/adr/NNNN-*.md`) & Excluding `docs/agents/**`

Observe-It stores agent instructions in `docs/agents/*.md` and public documentation (ADRs in `docs/adr/NNNN-*.md` and narrative guides) under `docs/`. The SSG must compile `docs/` in-place without forcing `docs/agents/**` into the public site or requiring non-standard Markdown changes to ADRs.

* **VitePress**:
  * **Source Directory**: When invoked via `vitepress dev docs` / `vitepress build docs` (or from `docs/package.json` with `srcDir: '.'`), VitePress reads `docs/index.md`, `docs/adr/NNNN-*.md`, and guide subdirectories directly ([VitePress Routing](https://vitepress.dev/guide/routing)).
  * **Exclusions**: [`srcExclude`](https://vitepress.dev/reference/site-config#srcexclude) accepts glob patterns relative to `srcDir`:
    ```ts
    export default defineConfig({
      srcExclude: ['agents/**', 'research/**'],
    })
    ```
    Excluded files are ignored by the dev server, static build, and local search indexer.
  * **Plain Markdown / ADRs**: No YAML frontmatter is required. VitePress extracts the document title from the first `# H1` heading and generates clean URLs.
* **MkDocs Material**:
  * **Source Directory**: `docs_dir: docs` is the default in [MkDocs Configuration](https://www.mkdocs.org/user-guide/configuration/#docs_dir).
  * **Exclusions**: Since MkDocs 1.5+, [`exclude_docs`](https://www.mkdocs.org/user-guide/configuration/#exclude_docs) is built into core MkDocs using `.gitignore`-style patterns:
    ```yaml
    docs_dir: docs
    exclude_docs: |
      agents/
      research/
    ```
  * **Plain Markdown / ADRs**: No YAML frontmatter is required; MkDocs infers page titles from the first `# H1` heading.
* **Astro Starlight**:
  * **Source Directory & Exclusions**: Starlight uses [Astro Content Collections](https://starlight.astro.build/reference/configuration/) (`src/content.config.ts`). While `docsLoader()` can be replaced or configured with Astro's `glob()` loader pointing `base` to `docs/` and excluding `!agents/**` ([Astro Content Loader API](https://docs.astro.build/en/reference/content-loader-reference/#glob-loader)), Starlight expects an Astro project structure (`astro.config.mjs`, `src/content.config.ts`).
  * **Frontmatter Friction on ADRs**: Starlight's `docsSchema()` enforces `title: z.string()` in YAML frontmatter on **every** Markdown file ([Starlight Frontmatter Reference](https://starlight.astro.build/reference/frontmatter/#title-required)). Any ADR (`docs/adr/0001-*.md`) without `---\ntitle: ...\n---` causes `astro build` to fail unless a custom loader transforms raw files to inject frontmatter.
* **Docusaurus**:
  * **Source Directory & Exclusions**: `@docusaurus/plugin-content-docs` points to `path: 'docs'` (or `'../docs'` if Docusaurus lives in a subfolder) and supports `exclude: ['agents/**', 'research/**']` ([Docusaurus Docs Plugin Configuration](https://docusaurus.io/docs/api/plugins/@docusaurus/plugin-content-docs#exclude)).
  * **MDX Parsing Caveat**: Docusaurus v3 compiles `.md` files with MDX v3 by default unless configured with `markdown: { format: 'detect' }` ([Docusaurus Markdown Configuration](https://docusaurus.io/docs/api/docusaurus-config#markdown)). Without `format: 'detect'`, standard Markdown containing generic type parameters (e.g., `List<OrderLineItem>`) or unescaped `{` in prose fails compilation as invalid JSX.

### 4. Footprint & Local Preview Developer Experience in a Kotlin/Gradle + Docker Repository

Observe-It is a Kotlin 2.x / Spring Boot / Gradle repository with Docker Compose (`docker-compose.yml`). A documentation generator should not clutter the project root with frontend configuration files (`tsconfig.json`, `docusaurus.config.ts`, `sidebars.ts`, `node_modules/`, etc.).

* **VitePress (Self-Contained in `docs/`)**:
  * **Footprint**: Can be 100% contained inside `docs/`:
    * `docs/package.json` & `docs/package-lock.json`
    * `docs/.vitepress/config.mts`
    * Ignore `docs/node_modules/`, `docs/.vitepress/cache/`, and `docs/.vitepress/dist/` in `.gitignore`.
    * Zero files added to the repository root.
  * **Local Preview**:
    * With local Node.js: `npm --prefix docs install && npm --prefix docs run dev` (starts instant Vite HMR server on `http://localhost:5173`).
    * Or via Docker (zero host Node installation required):
      ```bash
      docker run --rm -it -p 5173:5173 -v "$PWD/docs:/app" -w /app node:22-alpine sh -c "npm ci && npx vitepress dev --host"
      ```
* **MkDocs Material (Docker-Native / Single Config File)**:
  * **Footprint**: Requires only a single config file (`mkdocs.yml` at repo root, or `docs/mkdocs.yml` passed via `mkdocs serve -f docs/mkdocs.yml`) and optionally `docs/requirements.txt` for pinning `mkdocs-material` + `mkdocs-callouts`.
  * **Local Preview**: Official [`squidfunk/mkdocs-material`](https://squidfunk.github.io/mkdocs-material/getting-started/#with-docker) Docker image aligns naturally with a Docker-based repo, though if third-party plugins like `mkdocs-callouts` are needed for `> [!NOTE]`, a small custom `Dockerfile` or `uvx --with mkdocs-material --with mkdocs-callouts mkdocs serve` is required.
* **Astro Starlight & Docusaurus**:
  * Both generate multi-file frontend project scaffolds (`package.json`, `tsconfig.json`, `astro.config.mjs` / `docusaurus.config.ts`, `src/`, `public/` / `static/`). Even when tucked into a sub-directory (e.g., `site/` or `docs-site/`), they introduce substantially more framework boilerplate than VitePress or MkDocs Material for a documentation site that needs stock theming, built-in search, and Mermaid.

### 5. Automated Build & Deployment to GitHub Pages via GitHub Actions

All four static site generators integrate cleanly with GitHub's official artifact-based Pages deployment pipeline ([GitHub Docs — Using custom workflows with GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)) without committing build output to a `gh-pages` branch:

* **Required Official Actions**:
  * [`actions/configure-pages`](https://github.com/actions/configure-pages) (`v5`)
  * [`actions/upload-pages-artifact`](https://github.com/actions/upload-pages-artifact) (`v3`)
  * [`actions/deploy-pages`](https://github.com/actions/deploy-pages) (`v4`)
* **Observe-It CI & Dependabot Alignment**:
  * In `.github/workflows/ci.yml`, Observe-It pins all GitHub Actions to full commit SHAs with version comments (e.g., `uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1`). A new `.github/workflows/docs.yml` (or `pages.yml`) workflow should follow this exact SHA-pinning convention.
  * When hosting under `https://pawelbalcerek.github.io/observe-it/`, VitePress requires `base: '/observe-it/'` in `docs/.vitepress/config.mts` ([VitePress Deploy Guide — GitHub Pages](https://vitepress.dev/guide/deploy#github-pages)).
  * `.github/dependabot.yml` can be extended with a `package-ecosystem: "npm"` entry targeting `directory: "/docs"` so VitePress and Mermaid dependencies receive automated weekly updates alongside Gradle and GitHub Actions.

#### Reference GitHub Actions Workflow Structure for VitePress (`.github/workflows/pages.yml`)

```yaml
name: Deploy Docs to GitHub Pages

on:
  push:
    branches:
      - main
    paths:
      - 'docs/**'
      - '!docs/agents/**'
      - '.github/workflows/pages.yml'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4 # Pin to commit SHA per repo convention

      - name: Setup Node.js
        uses: actions/setup-node@v4 # Pin to commit SHA per repo convention
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: docs/package-lock.json

      - name: Setup Pages
        uses: actions/configure-pages@v5 # Pin to commit SHA per repo convention

      - name: Install dependencies
        working-directory: docs
        run: npm ci

      - name: Build VitePress site
        working-directory: docs
        run: npm run build

      - name: Upload Pages artifact
        uses: actions/upload-pages-artifact@v3 # Pin to commit SHA per repo convention
        with:
          path: docs/.vitepress/dist

  deploy:
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4 # Pin to commit SHA per repo convention
```

---

## Primary Sources

1. **VitePress**:
   * [Markdown Extensions — GitHub-Styled Alerts (`gfmAlerts`)](https://vitepress.dev/guide/markdown#github-styled-alerts)
   * [Site Config Reference — `srcExclude`](https://vitepress.dev/reference/site-config#srcexclude)
   * [Default Theme — Local Search (`provider: 'local'`)](https://vitepress.dev/reference/default-theme-search#local-search)
   * [Deploying a VitePress Site — GitHub Pages](https://vitepress.dev/guide/deploy#github-pages)
   * [`vitepress-plugin-mermaid` Documentation](https://emersonbottero.github.io/vitepress-plugin-mermaid/)
2. **MkDocs & Material for MkDocs**:
   * [MkDocs Configuration — `docs_dir` & `exclude_docs`](https://www.mkdocs.org/user-guide/configuration/#exclude_docs)
   * [Material for MkDocs — Diagrams (Mermaid.js)](https://squidfunk.github.io/mkdocs-material/reference/diagrams/)
   * [Material for MkDocs — Admonitions](https://squidfunk.github.io/mkdocs-material/reference/admonitions/)
   * [`mkdocs-callouts` (GitHub/Obsidian `[!NOTE]` callout syntax plugin)](https://github.com/oprypin/mkdocs-callouts)
3. **Astro Starlight**:
   * [Starlight Frontmatter Reference (`title` required)](https://starlight.astro.build/reference/frontmatter/#title-required)
   * [Starlight Authoring Content — Asides](https://starlight.astro.build/guides/authoring-content/#asides)
   * [Astro Content Loader API (`glob()` loader)](https://docs.astro.build/en/reference/content-loader-reference/#glob-loader)
   * [Deploy your Astro Site to GitHub Pages](https://docs.astro.build/en/guides/deploy/github/)
   * [`starlight-github-alerts` Plugin](https://github.com/HiDeoo/starlight-github-alerts)
4. **Docusaurus**:
   * [Docusaurus Diagrams (`@docusaurus/theme-mermaid`)](https://docusaurus.io/docs/markdown-features/diagrams)
   * [Docusaurus Admonitions](https://docusaurus.io/docs/markdown-features/admonitions)
   * [`@docusaurus/plugin-content-docs` — `path` and `exclude`](https://docusaurus.io/docs/api/plugins/@docusaurus/plugin-content-docs#exclude)
   * [`docusaurus.config.js` — `markdown.format` (`mdx` vs `detect`)](https://docusaurus.io/docs/api/docusaurus-config#markdown)
   * [Deploying Docusaurus to GitHub Pages](https://docusaurus.io/docs/deployment#deploying-to-github-pages)
5. **GitHub Pages Actions**:
   * [`actions/configure-pages`](https://github.com/actions/configure-pages)
   * [`actions/upload-pages-artifact`](https://github.com/actions/upload-pages-artifact)
   * [`actions/deploy-pages`](https://github.com/actions/deploy-pages)

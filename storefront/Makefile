.DEFAULT_GOAL := check
.PHONY: dev dev-docker check check-docker

PNPM_VERSION := 12.21.0

dev-docker:
	docker run --rm -p 5173:5173 \
		-v "$$(git rev-parse --show-toplevel):/workspace" \
		-v observe-it-storefront-node-modules:/workspace/storefront/node_modules \
		-v observe-it-storefront-pnpm-store:/pnpm/store \
		-w /workspace/storefront \
		node:24-alpine sh -c "npm install -g pnpm@$(PNPM_VERSION) && pnpm config set store-dir /pnpm/store && pnpm install --frozen-lockfile && pnpm run dev -- --host 0.0.0.0"

check-docker:
	docker run --rm \
		-v "$$(git rev-parse --show-toplevel):/workspace" \
		-v observe-it-storefront-node-modules:/workspace/storefront/node_modules \
		-v observe-it-storefront-pnpm-store:/pnpm/store \
		-w /workspace/storefront \
		node:24-alpine sh -c "npm install -g pnpm@$(PNPM_VERSION) && pnpm config set store-dir /pnpm/store && pnpm install --frozen-lockfile && pnpm run check"

dev:
	@if command -v pnpm >/dev/null 2>&1; then \
		if [ ! -d node_modules ]; then pnpm install --frozen-lockfile; fi; \
		pnpm run dev; \
	else \
		$(MAKE) dev-docker; \
	fi

check:
	@if command -v pnpm >/dev/null 2>&1; then \
		pnpm install --frozen-lockfile && pnpm run check; \
	else \
		$(MAKE) check-docker; \
	fi

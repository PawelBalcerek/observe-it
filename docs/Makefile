.DEFAULT_GOAL := build
.PHONY: dev dev-docker build build-docker lint lint-docker

dev-docker:
	docker run --rm -p 5173:5173 -v "$$(git rev-parse --show-toplevel):/workspace" -v observe-it-docs-node-modules:/workspace/docs/node_modules -w /workspace/docs node:26-alpine sh -c "npm install && npm run dev -- --host 0.0.0.0"

build-docker:
	docker run --rm -v "$$(git rev-parse --show-toplevel):/workspace" -v observe-it-docs-node-modules:/workspace/docs/node_modules -w /workspace/docs node:26-alpine sh -c "npm ci && npm run lint && npm run build"

lint-docker:
	docker run --rm -v "$$(git rev-parse --show-toplevel):/workspace" -v observe-it-docs-node-modules:/workspace/docs/node_modules -w /workspace/docs node:26-alpine sh -c "npm ci && npm run lint"

dev:
	@if command -v npm >/dev/null 2>&1; then \
		if [ ! -d node_modules ]; then npm install; fi; \
		npm run dev; \
	else \
		$(MAKE) dev-docker; \
	fi

build:
	@if command -v npm >/dev/null 2>&1; then \
		npm ci && npm run lint && npm run build; \
	else \
		$(MAKE) build-docker; \
	fi

lint:
	@if command -v npm >/dev/null 2>&1; then \
		if [ ! -d node_modules ]; then npm ci; fi; \
		npm run lint; \
	else \
		$(MAKE) lint-docker; \
	fi

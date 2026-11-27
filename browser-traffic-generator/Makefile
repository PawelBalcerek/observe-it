.DEFAULT_GOAL := check
.PHONY: check check-docker build-image

PNPM_VERSION := 12.23.0
OBSERVE_IT_IMAGE_PREFIX ?= ghcr.io/pawelbalcerek/observe-it
OBSERVE_IT_IMAGE_TAG ?= $(shell sed -n 's/^version = "\(.*\)"/\1/p' ../build.gradle.kts)

check-docker:
	docker run --rm \
		-v "$$(git rev-parse --show-toplevel):/workspace" \
		-v observe-it-browser-traffic-node-modules:/workspace/browser-traffic-generator/node_modules \
		-v observe-it-browser-traffic-pnpm-store:/pnpm/store \
		-w /workspace/browser-traffic-generator \
		node:26-slim sh -c "npm install -g pnpm@$(PNPM_VERSION) && pnpm config set store-dir /pnpm/store && pnpm install --frozen-lockfile && pnpm run check"

check:
	@if command -v pnpm >/dev/null 2>&1; then \
		pnpm install --frozen-lockfile && pnpm run check; \
	else \
		$(MAKE) check-docker; \
	fi

build-image:
	docker build \
		-t observe-it/browser-traffic-generator:$(OBSERVE_IT_IMAGE_TAG) \
		-t $(OBSERVE_IT_IMAGE_PREFIX)/browser-traffic-generator:$(OBSERVE_IT_IMAGE_TAG) \
		.

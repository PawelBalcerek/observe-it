.DEFAULT_GOAL := up
.PHONY: build up down reset logs verify docs-dev docs-dev-docker docs-build docs-build-docker docs-lint docs-lint-docker storefront-dev storefront-dev-docker storefront-check storefront-check-docker storefront-build-image browser-traffic-check browser-traffic-check-docker browser-traffic-build-image

OBSERVE_IT_IMAGE_PREFIX ?= ghcr.io/pawelbalcerek/observe-it
OBSERVE_IT_IMAGE_TAG ?= $(shell sed -n 's/^version = "\(.*\)"/\1/p' build.gradle.kts)

build:
	./gradlew bootBuildImage

up: build
	docker compose up -d

down:
	docker compose down

reset:
	docker compose down -v

logs:
	docker compose logs -f

verify:
	./gradlew check :e2e:e2eTest

docs-dev:
	$(MAKE) -C docs dev

docs-dev-docker:
	$(MAKE) -C docs dev-docker

docs-build:
	$(MAKE) -C docs build

docs-build-docker:
	$(MAKE) -C docs build-docker

docs-lint:
	$(MAKE) -C docs lint

docs-lint-docker:
	$(MAKE) -C docs lint-docker

storefront-dev:
	$(MAKE) -C storefront dev

storefront-dev-docker:
	$(MAKE) -C storefront dev-docker

storefront-check:
	$(MAKE) -C storefront check

storefront-check-docker:
	$(MAKE) -C storefront check-docker

storefront-build-image:
	docker build \
		--build-arg VITE_APP_VERSION=$(OBSERVE_IT_IMAGE_TAG) \
		-t observe-it/storefront:$(OBSERVE_IT_IMAGE_TAG) \
		-t $(OBSERVE_IT_IMAGE_PREFIX)/storefront:$(OBSERVE_IT_IMAGE_TAG) \
		storefront

browser-traffic-check:
	$(MAKE) -C browser-traffic-generator check

browser-traffic-check-docker:
	$(MAKE) -C browser-traffic-generator check-docker

browser-traffic-build-image:
	$(MAKE) -C browser-traffic-generator build-image

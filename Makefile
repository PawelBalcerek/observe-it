.DEFAULT_GOAL := up
.PHONY: build up down reset logs verify docs-dev docs-dev-docker docs-build docs-build-docker docs-lint docs-lint-docker

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
	./scripts/verify-m1.sh

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

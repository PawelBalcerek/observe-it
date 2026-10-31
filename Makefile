.DEFAULT_GOAL := up
.PHONY: build up down reset logs verify

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

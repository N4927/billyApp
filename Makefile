# ========= Makefile (Docker + Pytest) =========
SHELL := /usr/bin/env bash
.SHELLFLAGS := -euo pipefail -c

# ---- Compose binary autodetect ----
COMPOSE := $(shell \
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then \
    echo "docker compose"; \
  elif command -v docker-compose >/dev/null 2>&1; then \
    echo "docker-compose"; \
  else \
    echo ""; \
  fi)

# ---- Project names (no conflicts, lowercase) ----
PROJECT_BASE := $(shell basename "$$(pwd)" | tr '[:upper:]' '[:lower:]')
PROJECT_DEV  := $(PROJECT_BASE)-dev
PROJECT_PROD := $(PROJECT_BASE)-prod

# ---- Compose handles ----
DC_DEV  := $(COMPOSE) -p $(PROJECT_DEV)  --env-file .env.dev  -f docker-compose.dev.yml
DC_PROD := $(COMPOSE) -p $(PROJECT_PROD) --env-file .env.prod -f docker-compose.prod.yml

# ---- Service / pytest args ----
API  ?= api
ARGS ?=

# ---- Guards ----
_check-compose:
	@([ -n "$(COMPOSE)" ]) || { echo "Docker Compose not found"; exit 1; }

_check-dev:
	@[ -f ".env.dev" ] || { echo "Missing .env.dev"; exit 1; }
	@[ -f "docker-compose.dev.yml" ] || { echo "Missing docker-compose.dev.yml"; exit 1; }

_check-prod:
	@[ -f ".env.prod" ] || { echo "Missing .env.prod"; exit 1; }
	@[ -f "docker-compose.prod.yml" ] || { echo "Missing docker-compose.prod.yml"; exit 1; }

# ===================== DEV =====================
.PHONY: dev-up dev-down dev-test
dev-up: _check-compose _check-dev
	$(DC_DEV) build --pull
	$(DC_DEV) up -d --build --force-recreate

dev-down: _check-compose _check-dev
	$(DC_DEV) down -v --remove-orphans

dev-test: _check-compose _check-dev
	@set -e; \
	$(DC_DEV) build --pull; \
	status=0; \
	$(DC_DEV) run --rm $(API) pytest $(ARGS) || status=$$?; \
	$(DC_DEV) down --remove-orphans; \
	exit $$status

# ===================== PROD ====================
.PHONY: prod-up prod-down prod-test
prod-up: _check-compose _check-prod
	$(DC_PROD) build --pull
	$(DC_PROD) up -d --build --force-recreate

# aggressive teardown: remove orphans + any stray one-offs
prod-down: _check-compose _check-prod
	-$(DC_PROD) down -v --remove-orphans
	-@docker ps -aq --filter "name=^$(PROJECT_PROD)-" | xargs -r docker rm -f
	-@docker network ls --format '{{.Name}}' | grep -E '^$(PROJECT_PROD)_' | xargs -r docker network rm
	-@docker volume ls --format '{{.Name}}' | grep -E '^$(PROJECT_PROD)_' | xargs -r docker volume rm

# hard rebuild, run tests, always teardown deps started by `run`
prod-test: _check-compose _check-prod
	@set -e; \
	$(DC_PROD) build --pull --no-cache; \
	status=0; \
	$(DC_PROD) run --rm $(API) pytest $(ARGS) || status=$$?; \
	$(DC_PROD) down -v --remove-orphans; \
	docker ps -aq --filter "name=^$(PROJECT_PROD)-" | xargs -r docker rm -f; \
	docker network ls --format '{{.Name}}' | grep -E '^$(PROJECT_PROD)_' | xargs -r docker network rm; \
	docker volume ls --format '{{.Name}}' | grep -E '^$(PROJECT_PROD)_' | xargs -r docker volume rm; \
	exit $$status

# ===================== COMMON ==================
.PHONY: ps logs cleanup
ps: _check-compose
	@if [ -f docker-compose.dev.yml ] && [ -f .env.dev ]; then \
	  $(COMPOSE) -p $(PROJECT_DEV)  --env-file .env.dev  -f docker-compose.dev.yml  ps; \
	fi; \
	if [ -f docker-compose.prod.yml ] && [ -f .env.prod ]; then \
	  $(COMPOSE) -p $(PROJECT_PROD) --env-file .env.prod -f docker-compose.prod.yml ps; \
	fi

logs: _check-compose
	@if docker ps --format '{{.Names}}' | grep -q '^$(PROJECT_DEV)-$(API)-'; then \
	  $(COMPOSE) -p $(PROJECT_DEV)  --env-file .env.dev  -f docker-compose.dev.yml  logs -f $(API); \
	elif docker ps --format '{{.Names}}' | grep -q '^$(PROJECT_PROD)-$(API)-'; then \
	  $(COMPOSE) -p $(PROJECT_PROD) --env-file .env.prod -f docker-compose.prod.yml logs -f $(API); \
	else \
	  echo "No $(API) container running in dev or prod"; \
	fi

# total wipe of both envs (images, containers, networks, volumes)
cleanup: _check-compose
	-$(COMPOSE) -p $(PROJECT_DEV)  --env-file .env.dev  -f docker-compose.dev.yml  down -v --rmi all --remove-orphans 2>/dev/null || true
	-$(COMPOSE) -p $(PROJECT_PROD) --env-file .env.prod -f docker-compose.prod.yml down -v --rmi all --remove-orphans 2>/dev/null || true
	-@docker ps -aq --filter "name=^$(PROJECT_DEV)-"  | xargs -r docker rm -f
	-@docker ps -aq --filter "name=^$(PROJECT_PROD)-" | xargs -r docker rm -f
	-@docker network ls --format '{{.Name}}' | grep -E '^($(PROJECT_DEV)|$(PROJECT_PROD))_' | xargs -r docker network rm
	-@docker volume ls  --format '{{.Name}}' | grep -E '^($(PROJECT_DEV)|$(PROJECT_PROD))_' | xargs -r docker volume rm
	-docker system prune -af --volumes

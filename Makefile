# DocShelf developer entry points: docker stack, local backend/frontend dev, tests, android, backup/restore, key generation
SHELL := /bin/bash
.DEFAULT_GOAL := help

COMPOSE ?= docker compose
# Java 21 for local (non-Docker) backend runs. Override with `make backend-test JAVA_HOME=/path`.
TEMURIN21 := /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
ifneq ($(wildcard $(TEMURIN21)),)
export JAVA_HOME ?= $(TEMURIN21)
endif

.PHONY: help up down logs ps build restart backend-test frontend-test backend-run frontend-run android backup restore key env

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

env: ## Create .env from .env.example if it does not exist yet
	@if [ ! -f .env ]; then cp .env.example .env && echo "Created .env from .env.example. Now run: make key"; else echo ".env already exists"; fi

# ---------- Docker stack ----------
up: ## Build images and start the whole stack in the background
	$(COMPOSE) up --build -d

down: ## Stop the stack (data volumes are kept)
	$(COMPOSE) down

logs: ## Tail logs of all services (SERVICE=backend to narrow)
	$(COMPOSE) logs -f --tail=200 $(SERVICE)

ps: ## Show service status
	$(COMPOSE) ps

build: ## Rebuild images without starting
	$(COMPOSE) build

restart: ## Restart backend + frontend (SERVICE=... to pick)
	$(COMPOSE) restart $(or $(SERVICE),backend frontend)

# ---------- Tests ----------
backend-test: ## Run backend tests (needs Docker for Testcontainers)
	cd backend && ./mvnw -q test

frontend-test: ## Run frontend unit tests once (headless)
	cd frontend && npm test -- --watch=false --browsers=ChromeHeadless

# ---------- Local development (backend/frontend on the Mac, infra in Docker) ----------
backend-run: ## Start postgres+mailpit in Docker, then run the backend locally with spring-boot:run
	$(COMPOSE) up -d postgres mailpit
	@set -a; [ -f .env ] && . ./.env; set +a; \
	export POSTGRES_HOST=localhost POSTGRES_PORT=5432 MAIL_HOST=localhost MAIL_PORT=1025; \
	export DOCSHELF_BLOB_DIR="$${DOCSHELF_BLOB_DIR:-$(CURDIR)/data/blobs}"; mkdir -p "$$DOCSHELF_BLOB_DIR"; \
	cd backend && ./mvnw spring-boot:run

frontend-run: ## Run `npm start` (ng serve) proxying /api to http://localhost:8080 via frontend/proxy.conf.json
	@if [ -f frontend/proxy.conf.json ]; then \
	  cd frontend && npx ng serve --proxy-config proxy.conf.json; \
	else \
	  echo "frontend/proxy.conf.json not found; running plain 'npm start' (set the API URL + token in the app's Settings screen instead)"; \
	  cd frontend && npm start; \
	fi

android: ## Build the web app, sync Capacitor and open the Android project (needs Android Studio)
	cd frontend && npm run cap:sync && npx cap open android

# ---------- Data ----------
backup: ## Dump Postgres + tar the blobs volume into ./backups/<timestamp>/
	./scripts/backup.sh

restore: ## Restore from a backup directory: make restore DIR=backups/<timestamp>
	./scripts/restore.sh $(DIR)

key: ## Generate a master key and write it into .env (creates .env from the example if missing)
	./scripts/gen-master-key.sh --write

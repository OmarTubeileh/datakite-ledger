COMPOSE    := docker compose
PORTS      := 3000 8080 5432
CONTAINERS := datakite-frontend datakite-backend datakite-postgres

.DEFAULT_GOAL := help
.PHONY: help up start down stop restart build logs ps kill-ports clean-db buildRun freshBuildRun

help: ## Show this help
	@echo "Targets:"
	@echo "  make up             - Start the stack (docker compose up --build)"
	@echo "  make down           - Stop the stack, keep the DB volume"
	@echo "  make restart        - down + up"
	@echo "  make kill-ports     - Force-free ports $(PORTS): stops this project's"
	@echo "                        containers and kills any stray local process still"
	@echo "                        bound to those ports"
	@echo "  make clean-db       - Stop the stack and wipe the Postgres volume (destructive)"
	@echo "  make buildRun       - kill-ports, then rebuild and start (keeps the DB)"
	@echo "  make freshBuildRun  - kill-ports + clean-db, then rebuild and start (fresh DB)"
	@echo "  make logs           - Tail logs for all services"
	@echo "  make ps             - Show status of this project's containers"

up: ## Start the stack (build if needed)
	$(COMPOSE) up --build

start: up ## Alias for 'up'

down: ## Stop the stack, keep the DB volume
	$(COMPOSE) down

stop: down ## Alias for 'down'

restart: down up ## Stop then start

build: ## Build images without starting
	$(COMPOSE) build

logs: ## Tail logs for all services
	$(COMPOSE) logs -f

ps: ## Show status of this project's containers
	$(COMPOSE) ps

kill-ports: ## Force-free $(PORTS): stop this project's containers + kill stray local processes
	@echo "Stopping any running project containers..."
	@$(COMPOSE) down --remove-orphans 2>/dev/null || true
	@for name in $(CONTAINERS); do \
		docker rm -f $$name >/dev/null 2>&1 || true; \
	done
	@echo "Freeing host ports: $(PORTS)"
	@for port in $(PORTS); do \
		if command -v fuser >/dev/null 2>&1; then \
			fuser -k $$port/tcp 2>/dev/null || true; \
		elif command -v lsof >/dev/null 2>&1; then \
			pid=$$(lsof -ti tcp:$$port 2>/dev/null); \
			if [ -n "$$pid" ]; then kill -9 $$pid 2>/dev/null || true; fi; \
		else \
			pid=$$(ss -ltnp 2>/dev/null | awk -v p=":$$port " '$$0 ~ p' | grep -oP 'pid=\K[0-9]+' | head -1); \
			if [ -n "$$pid" ]; then kill -9 $$pid 2>/dev/null || true; fi; \
		fi; \
	done
	@echo "Ports freed (or were already free)."

clean-db: ## Stop the stack and wipe the Postgres volume (destructive - all data lost)
	@echo "Stopping the stack and removing the Postgres volume..."
	$(COMPOSE) down -v --remove-orphans

buildRun: kill-ports ## kill-ports, then rebuild and start (keeps the DB)
	$(COMPOSE) up --build

freshBuildRun: kill-ports clean-db ## kill-ports + clean-db, then rebuild and start (fresh DB)
	$(COMPOSE) up --build

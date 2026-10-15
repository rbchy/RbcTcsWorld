# Short names for the commands in docs/HELP_BN.md. "make" or "make help" lists them.
# Needs: Docker, JDK 21 + Maven, Node.js 22 (Playwright), k6 (optional). macOS: make comes with the Xcode tools.
.DEFAULT_GOAL := help
COMPOSE := docker compose
# Maven needs JAVA_HOME. A terminal often has none, or one that points to a removed JDK ("The JAVA_HOME
# environment variable is not defined correctly"): on a Mac, take JDK 21 from /usr/libexec/java_home then.
ifeq ($(wildcard $(JAVA_HOME)/bin/java),)
  MAC_JDK21 := $(shell /usr/libexec/java_home -v 21 2>/dev/null)
  ifneq ($(MAC_JDK21),)
    export JAVA_HOME := $(MAC_JDK21)
  endif
endif

UI_URL ?= http://localhost:5173
BASE_URL ?= http://localhost:8081

.PHONY: help doctor up app down clean backend-test api-test ui-test test playwright playwright-ui phone-ios phone-android \
        k6 rtm demo-smoke

help: ## this list
	@grep -hE '^[a-zA-Z0-9_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  make %-15s %s\n", $$1, $$2}'

doctor: ## which Java, Maven, Docker, Node, Appium and simulators this Mac has
	@echo "JAVA_HOME = $(JAVA_HOME)"; "$(JAVA_HOME)/bin/java" -version 2>&1 | head -1 || echo "  no JDK 21: brew install --cask temurin@21"
	@mvn -v 2>/dev/null | head -1 || echo "  Maven missing: brew install maven"
	@docker --version 2>/dev/null || echo "  Docker missing: Docker Desktop"
	@node --version 2>/dev/null || echo "  Node.js missing: brew install node"
	@appium --version 2>/dev/null | sed 's/^/Appium /' || echo "  Appium missing: npm install -g appium@3.8.0"
	@xcrun simctl list devices booted 2>/dev/null | grep -i iphone || echo "  no iPhone simulator running: open -a Simulator"

up: ## PostgreSQL + Redis + Mailpit only (run the backend from Eclipse, the storefront with npm run dev)
	$(COMPOSE) up -d

app: ## the whole shop in Docker: http://localhost:5173 (e-mails: http://localhost:8025)
	$(COMPOSE) --profile app up -d --build --wait

down: ## stop everything (the database is kept)
	$(COMPOSE) --profile app down

clean: ## stop everything AND delete the database volume
	$(COMPOSE) --profile app down -v

backend-test: ## backend unit + integration tests with the coverage gate (no Docker needed)
	mvn -B -f backend/pom.xml verify

api-test: ## API, database, BDD and security tests against the running shop (make app first)
	mvn -B -f automation/pom.xml test -DexcludedGroups=ui,mobile -DbaseUrl=$(BASE_URL) -DuiUrl=$(UI_URL)

ui-test: ## Selenium UI + accessibility tests (BROWSER=chrome|firefox|edge|safari)
	mvn -B -f automation/pom.xml test -Dgroups=ui -Dbrowser=$(or $(BROWSER),chrome) -DbaseUrl=$(BASE_URL) -DuiUrl=$(UI_URL)

test: ## the whole Java automation suite (everything except phones)
	mvn -B -f automation/pom.xml test -DbaseUrl=$(BASE_URL) -DuiUrl=$(UI_URL)

playwright: ## Playwright in Chromium, Firefox, WebKit and two phones
	cd playwright && npm ci && npx playwright install chromium firefox webkit && UI_URL=$(UI_URL) npx playwright test

playwright-ui: ## Playwright UI mode: pick tests, watch every step
	cd playwright && UI_URL=$(UI_URL) npx playwright test --ui

phone-ios: ## Appium: Safari on the iOS Simulator (Simulator and "appium" must be running)
	mvn -B -f automation/pom.xml test -Dgroups=mobile -DexcludedGroups=none -Dbrowser=ios-safari -DuiUrl=$(UI_URL)

phone-android: ## Appium: Chrome on the Android emulator (emulator and "appium" must be running)
	mvn -B -f automation/pom.xml test -Dgroups=mobile -DexcludedGroups=none -Dbrowser=android-chrome -DuiUrl=http://10.0.2.2:5173

k6: ## performance smoke with SLO thresholds (k6 installed, or the grafana/k6 image)
	performance/k6run.sh performance/tests/smoke.js

rtm: ## every requirement in the traceability matrix has an existing test
	python3 docs/qa/check_rtm.py

demo-smoke: ## outside-in check of the public demo: make demo-smoke URL=https://...
	deploy/smoke.sh $(URL)

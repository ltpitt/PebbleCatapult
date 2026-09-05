# Thin wrapper around scripts/*.sh. Keep all real logic in the scripts so it
# stays runnable (and readable/debuggable) without make: `./scripts/x.sh`
# works identically to `make x`.
.DEFAULT_GOAL := help
.PHONY: help build build-release watchapp test check install clean \
        quick-release release releases

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

build: ## Build a debug APK (mobile/app/build/outputs/apk/debug)
	@scripts/build-debug.sh

build-release: ## Build a release APK locally
	@scripts/build-release.sh

watchapp: ## Build the Pebble watchapp (requires pebble-tool)
	@scripts/build-watchapp.sh

test: ## Run unit tests (same set as CI, minus screenshot tests)
	@scripts/test.sh

check: ## Run lint/detekt/buildHealth checks (same as CI's Lint step)
	@scripts/check.sh

install: ## Build the debug APK and install it via adb
	@scripts/install-debug.sh

clean: ## Clean all local build outputs
	@scripts/clean.sh

quick-release: ## Trigger quick-build on the current branch, wait, print the debug-latest URL
	@scripts/quick-release.sh

release: ## Trigger the full develop-build release (default ref: main)
	@scripts/release.sh $(ref)

releases: ## List recent GitHub Releases
	@scripts/releases.sh

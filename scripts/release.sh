#!/usr/bin/env bash
# Trigger the full develop-build release workflow (tests, lint, detekt,
# watchapp, versioning, GitHub Release) and wait for it to finish.
# Usage: scripts/release.sh [ref]   (defaults to main)
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd gh "Install the GitHub CLI: https://cli.github.com/"

ref="${1:-main}"
log "Dispatching develop-build on ref '$ref'"
gh workflow run develop-build --ref "$ref"

log "Waiting for the run to appear..."
sleep 5
run_id="$(gh run list --workflow=develop-build --branch "$ref" --limit 1 --json databaseId --jq '.[0].databaseId')"

log "Watching run $run_id (this takes several minutes: tests, lint, watchapp build)"
gh run watch "$run_id" --exit-status

log "Done. Latest release:"
gh release view --json url --jq '.url' 2>/dev/null || echo "  $(gh repo view --json url --jq '.url')/releases/latest"

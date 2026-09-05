#!/usr/bin/env bash
# Trigger the full develop-build release workflow (tests, lint, detekt,
# watchapp, versioning, GitHub Release) and wait for it to finish.
# Usage: scripts/release.sh [ref]   (defaults to main)
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd gh "Install the GitHub CLI: https://cli.github.com/"

repo="$(gh_repo)"
ref="${1:-main}"
log "Dispatching develop-build on ref '$ref' ($repo)"
gh workflow run develop-build --repo "$repo" --ref "$ref"

log "Waiting for the run to appear..."
sleep 5
run_id="$(gh run list --repo "$repo" --workflow=develop-build --branch "$ref" --limit 1 --json databaseId --jq '.[0].databaseId')"

log "Watching run $run_id (this takes several minutes: tests, lint, watchapp build)"
gh run watch "$run_id" --repo "$repo" --exit-status

log "Done. Latest release:"
echo "  https://github.com/$repo/releases/latest"

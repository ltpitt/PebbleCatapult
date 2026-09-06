#!/usr/bin/env bash
# Trigger the quick-build workflow on the current branch, wait for it to
# finish, and print the resulting debug-latest prerelease/asset URL.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd gh "Install the GitHub CLI: https://cli.github.com/"

repo="$(gh_repo)"
branch="$(git rev-parse --abbrev-ref HEAD)"
log "Dispatching quick-build on branch '$branch' ($repo)"
gh workflow run quick-build --repo "$repo" --ref "$branch"

log "Waiting for the run to appear..."
sleep 5
run_id="$(gh run list --repo "$repo" --workflow=quick-build --branch "$branch" --limit 1 --json databaseId --jq '.[0].databaseId')"

log "Watching run $run_id"
gh run watch "$run_id" --repo "$repo" --exit-status

assets="$(gh release view debug-latest --repo "$repo" --json assets --jq '[.assets[].name] | sort | join(",")')"
expected_assets="catapult-mobile.apk,catapult-watchapp.pbw"
if [[ "$assets" != "$expected_assets" ]]; then
  echo "error: expected quick release assets '$expected_assets', found '$assets'" >&2
  exit 1
fi

log "Done. Debug APK and Pebble watchapp PBW published at:"
echo "  https://github.com/$repo/releases/tag/debug-latest"

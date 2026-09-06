#!/usr/bin/env bash
# Trigger the quick-build workflow on the current branch, wait for it to
# finish, and print the resulting debug-latest prerelease/asset URL.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd gh "Install the GitHub CLI: https://cli.github.com/"

repo="$(gh_repo)"
branch="$(git rev-parse --abbrev-ref HEAD)"
commit="$(git rev-parse HEAD)"
log "Dispatching quick-build on branch '$branch' at $commit ($repo)"
gh workflow run quick-build --repo "$repo" --ref "$branch"

log "Waiting for the run to appear..."
run_id=""
for attempt in {1..30}; do
  if run_id="$(gh run list --repo "$repo" --workflow=quick-build --branch "$branch" --commit "$commit" --event workflow_dispatch --limit 1 --json databaseId --jq '.[0].databaseId')"; then
    if [[ -n "$run_id" ]]; then
      break
    fi
  fi
  if [[ "$attempt" -eq 30 ]]; then
    echo "error: quick-build run for commit $commit did not appear after 30 attempts" >&2
    exit 1
  fi
  sleep 2
done

log "Watching run $run_id"
gh run watch "$run_id" --repo "$repo" --exit-status

expected_assets="catapult-mobile.apk,catapult-watchapp.pbw"
is_prerelease="$(gh release view debug-latest --repo "$repo" --json isPrerelease --jq '.isPrerelease')"
assets="$(gh release view debug-latest --repo "$repo" --json assets --jq '[.assets[].name] | sort | join(",")')"
target_commit="$(gh release view debug-latest --repo "$repo" --json targetCommitish --jq '.targetCommitish // ""')"
body_commit="$(gh release view debug-latest --repo "$repo" --json body --jq 'try (.body | capture("Commit: `(?<commit>[0-9a-fA-F]{40})`").commit) catch ""')"

if [[ "$is_prerelease" != "true" ]]; then
  echo "error: debug-latest is not marked as a prerelease" >&2
  exit 1
fi
if [[ "$assets" != "$expected_assets" ]]; then
  echo "error: expected quick release assets '$expected_assets', found '$assets'" >&2
  exit 1
fi
if [[ "$target_commit" != "$commit" && "$body_commit" != "$commit" ]]; then
  echo "error: debug-latest does not target current commit $commit (target: '${target_commit:-unknown}', body: '${body_commit:-unknown}')" >&2
  exit 1
fi

log "Done. Debug APK and Pebble watchapp PBW published at:"
echo "  https://github.com/$repo/releases/tag/debug-latest"

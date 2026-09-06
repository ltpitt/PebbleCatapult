#!/usr/bin/env bash
# Trigger the quick-build workflow on the current branch, wait for it to
# finish, and print the resulting debug-latest prerelease/asset URL.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd gh "Install the GitHub CLI: https://cli.github.com/"
require_cmd jq "Install jq: https://jqlang.org/download/"

repo="$(gh_repo)"
branch="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$branch" == "HEAD" ]]; then
  echo "error: quick-release requires a branch checkout; detached HEAD cannot be dispatched" >&2
  exit 1
fi
commit="$(git rev-parse HEAD)"
dispatch_epoch="$(date +%s)"
log "Dispatching quick-build on branch '$branch' at $commit ($repo)"
gh workflow run quick-build --repo "$repo" --ref "$branch"

log "Waiting for the run to appear..."
run_id=""
for attempt in {1..30}; do
  if run_id="$(
    gh run list \
      --repo "$repo" \
      --workflow=quick-build \
      --event workflow_dispatch \
      --commit "$commit" \
      --limit 100 \
      --json databaseId,headSha,createdAt |
      jq -r --arg commit "$commit" --argjson dispatch_epoch "$dispatch_epoch" '
        map(select(
          .headSha == $commit and
          ((.createdAt | fromdateiso8601) >= $dispatch_epoch)
        ))
        | sort_by([.createdAt, .databaseId])
        | last
        | .databaseId // empty
      '
  )"; then
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

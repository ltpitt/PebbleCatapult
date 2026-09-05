#!/usr/bin/env bash
# Shared helpers sourced by every script in this directory. Not meant to be
# run directly.
set -euo pipefail

# Always operate from the repository root, regardless of where the script
# was invoked from.
repo_root() {
  git rev-parse --show-toplevel
}

cd "$(repo_root)"

log() {
  echo "==> $*"
}

require_cmd() {
  local cmd="$1"
  local hint="${2:-}"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "error: '$cmd' is required but not installed on PATH." >&2
    if [[ -n "$hint" ]]; then
      echo "       $hint" >&2
    fi
    exit 1
  fi
}

# Print "owner/repo" derived from the 'origin' git remote, regardless of
# whether `gh` has a default repository configured (`gh repo set-default`).
# Every gh(1) call in these scripts passes this explicitly via --repo so
# they work the same in a fresh clone/CI shell as they do after gh has been
# manually configured.
gh_repo() {
  local url
  url="$(git remote get-url origin)"
  # Handles both git@github.com:owner/repo.git and
  # https://github.com/owner/repo.git forms.
  echo "$url" | sed -E 's#^(git@|https://)github.com[:/]##; s#\.git$##'
}

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

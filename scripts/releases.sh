#!/usr/bin/env bash
# List recent GitHub Releases (proper releases and the debug-latest
# prerelease).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd gh "Install the GitHub CLI: https://cli.github.com/"

gh release list --limit 10

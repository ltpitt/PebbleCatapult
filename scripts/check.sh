#!/usr/bin/env bash
# Run the same checks CI's "Lint" step runs: detekt, module-graph
# assertion, dependency-analysis buildHealth, unused-file detection, and the
# Room database migration/schema check. Run this before pushing to catch
# what would otherwise only be caught by develop-build/quick-build in CI.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

log "Running lint/detekt/buildHealth checks (mirrors CI's Lint step)"
cd mobile
./gradlew lintRelease runDebugDetekt assertModuleGraph buildHealth detectTooManyFiles :app:verifyDebugDatabaseMigration reportMerge --continue

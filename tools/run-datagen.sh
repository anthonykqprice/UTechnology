#!/usr/bin/env bash
# Regenerate the addon's models, blockstates, loot tables and lang (addon/src/generated/resources).
# Data generation finishes in about a second, but a mod in the stack keeps the JVM alive afterwards, so this
# waits for Minecraft's "Caching: total files" line (written once every provider is done), then stops Gradle.
set -euo pipefail
cd "$(dirname "$0")/../addon"
log=$(mktemp); trap 'rm -f "$log"' EXIT
setsid ./gradlew --no-daemon ${GRADLE_ARGS:-} runData >"$log" 2>&1 &
pid=$!   # own process group, so stopping it can't touch the caller
for _ in $(seq 1 900); do
  if grep -q "HashCache\]: Caching: total files" "$log"; then
    grep "HashCache\]: Caching" "$log"
    kill -- -"$pid" 2>/dev/null || true   # the whole process group: Gradle and the game JVM it started
    wait "$pid" 2>/dev/null || true
    rm -rf src/generated/resources/.cache
    echo "data generation done: addon/src/generated/resources"
    exit 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then cat "$log"; echo "data generation failed" >&2; exit 1; fi
  sleep 1
done
cat "$log"; echo "data generation timed out" >&2; kill -- -"$pid"; exit 1

#!/usr/bin/env bash
# Regenerate the addon's models, blockstates, loot tables and lang (addon/src/generated/resources).
# Data generation finishes in about a second, but a mod in the stack keeps the JVM alive afterwards, so this
# waits for Minecraft's "Caching: total files" line (written once every provider is done), then stops Gradle.
# The output folder is cleared first, so renamed or removed content leaves nothing behind.
set -euo pipefail
cd "$(dirname "$0")/../addon"
log=$(mktemp)
pid=
cleanup() { [ -n "$pid" ] && { kill -- -"$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; }; rm -f "$log"; }
trap cleanup EXIT
rm -rf src/generated/resources
setsid ./gradlew --no-daemon ${GRADLE_ARGS:-} runData >"$log" 2>&1 &
pid=$!   # own process group, so stopping it can't touch the caller
for _ in $(seq 1 300); do
  if grep -q "HashCache\]: Caching: total files" "$log"; then
    grep "HashCache\]: Caching" "$log"
    rm -rf src/generated/resources/.cache
    echo "data generation done: addon/src/generated/resources"
    exit 0
  fi
  if grep -qE "\[main/ERROR\]|Exception in thread|BUILD FAILED" "$log"; then
    grep -E "ERROR|Exception|FAILED" "$log" | head -20; echo "data generation failed" >&2; exit 1
  fi
  if ! kill -0 "$pid" 2>/dev/null; then tail -40 "$log"; echo "data generation stopped without finishing" >&2; exit 1; fi
  sleep 1
done
tail -40 "$log"; echo "data generation timed out" >&2; exit 1

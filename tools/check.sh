#!/usr/bin/env bash
# Everything CI checks about the design data. Run from anywhere.
# Generated files must match what the generators produce from design/ (files are regenerated in place,
# so after a failure the working tree holds the fresh versions to commit).
set -euo pipefail
cd "$(dirname "$0")/.."
stale=0
before=$(mktemp -d); trap 'rm -rf "$before"' EXIT
cp design/sim/data.json design/sim/index.html "$before/"
python3 design/sim/build.py >/dev/null
for f in data.json index.html; do
  cmp -s "$before/$f" "design/sim/$f" || { echo "stale: design/sim/$f (run python3 design/sim/build.py)"; stale=1; }
done
python3 tools/generate.py --check || stale=1
python3 tools/validate.py --check || stale=1
[ "$stale" = 0 ] || { echo "design checks failed"; exit 1; }
python3 -m unittest discover -s tools -p 'test_*.py' -q
echo "design checks passed"

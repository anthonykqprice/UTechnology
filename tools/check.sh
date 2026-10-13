#!/usr/bin/env bash
# Everything CI checks about the design data. Run from the repository root.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 design/sim/build.py >/dev/null
git diff --exit-code -- design/sim || { echo "design/sim/data.json or index.html is stale: run python3 design/sim/build.py"; exit 1; }
if [ -f tools/generate.py ]; then
  python3 tools/generate.py
  git diff --exit-code -- pack addon/src || { echo "Generated files are stale: run python3 tools/generate.py"; exit 1; }
  # untracked generated files count as stale too
  test -z "$(git status --porcelain --untracked-files=all -- pack addon/src)" || { git status --short -- pack addon/src; echo "Generated files are not committed"; exit 1; }
fi
if [ -f tools/validate.py ]; then python3 tools/validate.py; fi
echo "design checks passed"

#!/usr/bin/env bash
# Export the pack as a Modrinth .mrpack that includes the addon jar.
# Usage: tools/export-pack.sh <addon-jar> [output.mrpack]
# The addon isn't on Modrinth, so its jar goes into the pack's overrides/mods/ folder.
set -euo pipefail
[ $# -ge 1 ] || { echo "usage: $0 <addon-jar> [output.mrpack]" >&2; exit 2; }
abs() { case "$1" in /*) echo "$1" ;; *) echo "$PWD/$1" ;; esac; }
jar=$(abs "$1"); out=$(abs "${2:-UTechnology.mrpack}")
[ -f "$jar" ] || { echo "no such jar: $jar" >&2; exit 1; }
cd "$(dirname "$0")/.."
packwiz=${PACKWIZ:-packwiz}
command -v "$packwiz" >/dev/null || packwiz=~/go/bin/packwiz
rm -f "$out"
(cd pack && "$packwiz" refresh && "$packwiz" modrinth export -o "$out")
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/overrides/mods" && cp "$jar" "$tmp/overrides/mods/"
(cd "$tmp" && zip -qr "$out" overrides)
echo "exported $out with $(basename "$jar")"

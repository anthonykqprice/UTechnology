#!/usr/bin/env bash
# Export the pack as a Modrinth .mrpack that includes the addon jar.
# Usage: tools/export-pack.sh <addon-jar> [output.mrpack]
# The addon isn't on Modrinth, so its jar goes into the pack's overrides/mods/ folder.
set -euo pipefail
cd "$(dirname "$0")/.."
jar=$(realpath "$1"); out=$(realpath -m "${2:-UTechnology.mrpack}")
packwiz=${PACKWIZ:-packwiz}
command -v "$packwiz" >/dev/null || packwiz=~/go/bin/packwiz
(cd pack && "$packwiz" refresh && "$packwiz" modrinth export -o "$out")
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/overrides/mods" && cp "$jar" "$tmp/overrides/mods/"
(cd "$tmp" && zip -qr "$out" overrides)
echo "exported $out with $(basename "$jar")"

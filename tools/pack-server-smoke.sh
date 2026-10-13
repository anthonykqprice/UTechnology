#!/usr/bin/env bash
# Boots a dedicated server from the exported pack (every server-side mod, the configs, the KubeJS scripts and
# the addon), runs /utechkit check, and fails on KubeJS errors, missing kit items or a crash.
# Usage: tools/pack-server-smoke.sh <UTechnology.mrpack> [work-dir]
# Needs java 17, python3, curl, unzip; downloads Forge's installer (maven.minecraftforge.net), which fetches
# Minecraft's server, and the mods from cdn.modrinth.com (checked against the pack's hashes).
set -euo pipefail
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
pack=$(realpath "$1"); work=${2:-$(mktemp -d)}
mkdir -p "$work"; cd "$work"
mc=1.20.1
forge=$(unzip -p "$pack" modrinth.index.json | python3 -c "import json,sys; print(json.load(sys.stdin)['dependencies']['forge'])")
echo "Forge $mc-$forge in $work"
if [ ! -f run.sh ]; then
  curl -fsSLo installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/$mc-$forge/forge-$mc-$forge-installer.jar"
  java -jar installer.jar --installServer >installer.log 2>&1 || { tail -20 installer.log; exit 1; }
fi
mkdir -p mods
unzip -p "$pack" modrinth.index.json | python3 -I -c '
import hashlib, json, os, sys, urllib.request
idx = json.load(sys.stdin)
for f in idx["files"]:
    if f.get("env", {}).get("server") == "unsupported":
        continue
    path = f["path"]
    if os.path.exists(path) and hashlib.sha512(open(path, "rb").read()).hexdigest() == f["hashes"]["sha512"]:
        continue
    data = urllib.request.urlopen(f["downloads"][0]).read()
    if hashlib.sha512(data).hexdigest() != f["hashes"]["sha512"]:
        sys.exit("hash mismatch: " + path)
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    open(path, "wb").write(data)
    print("mod", path)
'
rm -rf kubejs config/gtceu.yaml
unzip -oq "$pack" 'overrides/*' -d overrides-tmp && cp -r overrides-tmp/overrides/. . && rm -rf overrides-tmp
echo "eula=true" > eula.txt   # accepting Minecraft's EULA for this throwaway test server
printf 'online-mode=false\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-monsters=false\n' > server.properties
rm -f logs/latest.log
fifo=$(mktemp -u); mkfifo "$fifo"
( bash run.sh nogui < "$fifo" > server.out 2>&1 ) &
pid=$!
exec 3>"$fifo"
for _ in $(seq 1 900); do
  grep -q 'Done (' server.out 2>/dev/null && break
  kill -0 "$pid" 2>/dev/null || break
  sleep 1
done
if grep -q 'Done (' server.out; then
  echo "utechkit check" >&3; sleep 3; echo "stop" >&3
fi
wait "$pid" || true
exec 3>&-; rm -f "$fifo"
log=logs/latest.log
fail=0
grep -q 'Done (' server.out || { echo "server did not start"; tail -40 server.out; fail=1; }
grep -E "utechkit check: 0 missing" "$log" >/dev/null || { echo "kit check failed:"; grep "utechkit check" "$log" || true; fail=1; }
if grep -E "\[KubeJS (Startup|Server)/\]" "$log" | grep -E "ERROR|Error" ; then echo "KubeJS errors"; fail=1; fi
grep -E "Added [0-9]+ recipes, removed [0-9]+ recipes" "$log" || true
grep -E "Upgrade data loaded" "$log" || { echo "upgrade data not loaded"; fail=1; }
[ "$fail" = 0 ] && echo "pack server smoke test passed" || { echo "pack server smoke test FAILED (log: $work/$log)"; exit 1; }

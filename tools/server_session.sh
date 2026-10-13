#!/usr/bin/env bash
# Usage: tools/server_session.sh <commands-file> <log-file>
# Starts runServer, waits for "Done", feeds one console command per line (a line "sleep N" waits N s).
set -u
cmds=$1; log=$2
fifo=$(mktemp -u); mkfifo "$fifo"
( ./gradlew --no-daemon --max-workers=1 runServer < "$fifo" > "$log" 2>&1 ) &
exec 3>"$fifo"
for i in $(seq 1 600); do grep -q 'Done (' "$log" 2>/dev/null && break; sleep 1; done
while IFS= read -r line; do
  case "$line" in sleep*) sleep "${line#sleep }";; *) echo "$line" >&3; sleep 1;; esac
done < "$cmds"
wait
exec 3>&-; rm -f "$fifo"

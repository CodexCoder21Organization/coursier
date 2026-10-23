#!/usr/bin/env bash
set -eu
repo_dir=$1
evidence_dir=$(cd "$(dirname "$0")" && pwd)
slot=/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/out/full-suite.lock
deadline=$(( $(cat "$evidence_dir/cache-slot-start.epoch") + 10800 ))
while ! mkdir "$slot" 2>/dev/null; do
  if (( $(date +%s) >= deadline )); then
    date -u '+%Y-%m-%d %H:%M:%S UTC PROBLEM: cache-module slot wait reached three hours' >> "$evidence_dir/cache-slot.log"
    exit 1
  fi
  date -u '+%Y-%m-%d %H:%M:%S UTC waiting for cache-module memory slot' >> "$evidence_dir/cache-slot.log"
  sleep 60
done
printf '%s\n' FCOURS4 > "$slot/owner"
trap 'rm -rf "$slot"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
date -u '+%Y-%m-%d %H:%M:%S UTC acquired cache-module memory slot' >> "$evidence_dir/cache-slot.log"
python3 "$evidence_dir/run-command.py" "$repo_dir" cache-module ./mill --no-server -j1 'cache.jvm[2.13.16].test'
python3 - "$evidence_dir" <<'VERIFY'
import sys,json
from pathlib import Path
result=json.loads((Path(sys.argv[1])/'cache-module-result.json').read_text())
sys.exit(0 if result['exit']==0 else 1)
VERIFY

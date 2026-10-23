#!/usr/bin/env bash
set -euo pipefail
repo=$1
here=$(cd "$(dirname "$0")" && pwd)
cd "$repo"
for i in $(seq -w 1 30); do
  git fetch origin
  git rebase origin/main
  bash "$here/run-large.sh" "round-$i" "$repo"
  python3 "$here/audit-phase.py" "$repo" "round-$i" 3
  python3 - "$here/round-$i-audit.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1]));assert r['pass']==3 and r['fail']==0,r
PY
done

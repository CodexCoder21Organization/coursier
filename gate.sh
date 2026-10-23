#!/usr/bin/env bash
set -eu
root_dir=$1
evidence_dir=$(cd "$(dirname "$0")" && pwd)
lock_dir=/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/out/full-suite.lock
deadline=$((SECONDS+10800))
until mkdir "$lock_dir" 2>/dev/null; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo 'PROBLEM: memory slot deadline reached' > "$evidence_dir/gate-lock-result.txt"
    exit 1
  fi
  sleep 60
done
trap 'rm -rf "$lock_dir"' EXIT
echo FCOURS5 > "$lock_dir/owner"
date -u '+%F %T UTC' > "$evidence_dir/gate-lock-acquired.txt"
python3 "$evidence_dir/gates.py" "$root_dir"

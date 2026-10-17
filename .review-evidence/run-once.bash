#!/usr/bin/env bash
set -uo pipefail
lane_root=/tmp/claude-501/-code/a549f21f-144a-44f6-8143-0e07f6d01615/scratchpad/lanes
cd "$lane_root/workspace/revcoursier3"
slot_path=
limit=$((SECONDS+900))
while [[ -z "$slot_path" && $SECONDS -lt $limit ]]; do
  for i in 1 2 3 4; do
    if mkdir "$lane_root/out/build-slot-$i" 2>/dev/null; then
      slot_path="$lane_root/out/build-slot-$i"
      printf 'revcoursier3 %s\n' "$(date -u +%H:%M)" > "$slot_path/owner"
      break
    fi
  done
  [[ -n "$slot_path" ]] || sleep 30
done
[[ -n "$slot_path" ]] || exit 75
trap 'rm -r "$slot_path"' EXIT
df -h / > .review-evidence/disk-before-run.txt
export PATH="$PWD/.review-tools:$PATH"
export LC_ALL=C.UTF-8
timeout --signal=TERM --kill-after=10s 900 ./mill -i 'cache.jvm[2.13.16].test.testOnly' coursier.cache.FileCacheLocationOwnershipTests coursier.cache.FileCacheRetrySettingsTests > .review-evidence/module-run.log 2>&1
result=$?
printf '%s\n' "$result" > .review-evidence/module-exit.txt
exit "$result"

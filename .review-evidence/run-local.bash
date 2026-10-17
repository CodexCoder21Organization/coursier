#!/bin/bash
set -u
base=/tmp/claude-501/-code/a549f21f-144a-44f6-8143-0e07f6d01615/scratchpad/lanes/out
slot=''
for attempt in $(seq 1 30); do
  for i in 1 2 3 4; do
    if mkdir "$base/build-slot-$i" 2>/dev/null; then
      slot="$base/build-slot-$i"
      echo "revcoursier2 $(date -u +%H:%M)" > "$slot/owner"
      echo "$i" > "$base/revcoursier2-slot"
      break
    fi
  done
  test -n "$slot" && break
  sleep 30
done
if test -z "$slot"; then echo 'No local build slot available'; exit 1; fi
trap 'rm -r "$slot"; rm -f "$base/revcoursier2-slot"' EXIT
export PATH="$PWD/.review-tools:$PATH"
export LC_ALL=C.UTF-8
export JAVA_OPTS='-Xmx2g'
./mill -i 'cache.jvm[2.13.16].test.testOnly' coursier.cache.FileCacheLocationOwnershipTests
result=$?
mkdir -p .review-evidence/local
cp out/cache/jvm/2.13.16/test/testOnly.dest/test-report.xml .review-evidence/local/test-report.xml 2>/dev/null || true
printf '%s\n' "$result" > .review-evidence/local/exit-code.txt
exit "$result"

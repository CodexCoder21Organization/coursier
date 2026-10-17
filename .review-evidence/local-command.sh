#!/usr/bin/env bash
set -u
slot_root=/tmp/claude-501/-code/a549f21f-144a-44f6-8143-0e07f6d01615/scratchpad/lanes/out
slot_path=
limit=$((SECONDS+900))
while [ -z "$slot_path" ] && [ "$SECONDS" -lt "$limit" ]; do
  for i in 1 2 3 4; do
    if mkdir "$slot_root/build-slot-$i" 2>/dev/null; then
      slot_path="$slot_root/build-slot-$i"
      echo "revcoursier1 $(date -u +%H:%M)" > "$slot_path/owner"
      echo "$i" > "$slot_root/revcoursier1-slot"
      break
    fi
  done
  if [ -z "$slot_path" ]; then sleep 30; fi
done
if [ -z "$slot_path" ]; then echo 'NO SLOT'; exit 75; fi
trap 'rm -r "$slot_path"; rm -f "$slot_root/revcoursier1-slot"' EXIT
export PATH="$PWD/.review-bin:$PATH"
export LC_ALL=C.UTF-8
export JAVA_OPTS=-Xmx2g
export COURSIER_REPOSITORIES=https://repo.maven.apache.org/maven2
./mill -i 'cache.jvm[2.13.16].test.testOnly' coursier.cache.FileCacheLocationOwnershipTests

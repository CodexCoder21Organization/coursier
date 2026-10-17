#!/usr/bin/env bash
set -u
cd /tmp/claude-501/-code/a549f21f-144a-44f6-8143-0e07f6d01615/scratchpad/lanes/workspace/coursierfetch4
run_name=$1
shift
lane_out=../../out
slot_path=
finish() { if [ -n "$slot_path" ]; then rm -r "$slot_path"; rm -f "$lane_out/coursierfetch4-slot"; fi; }
trap finish EXIT INT TERM
end=$((SECONDS+900))
while [ -z "$slot_path" ] && [ "$SECONDS" -lt "$end" ]; do
  for i in 1 2 3 4; do
    if mkdir "$lane_out/build-slot-$i" 2>/dev/null; then
      slot_path="$lane_out/build-slot-$i"
      echo "coursierfetch4 $(date -u +%H:%M)" > "$slot_path/owner"
      echo "$i" > "$lane_out/coursierfetch4-slot"
      break
    fi
  done
  if [ -z "$slot_path" ]; then sleep 30; fi
done
if [ -z "$slot_path" ]; then echo 'NOT READY: no local slot within 15 minutes'; exit 2; fi
mkdir -p ".lane-tools/$run_name"
df -h / > ".lane-tools/$run_name/disk.txt"
free_kb=$(df --output=avail / | tail -1)
if [ "$free_kb" -lt 20971520 ]; then echo 'NOT READY: disk below 20 GB'; exit 2; fi
git rev-parse HEAD > ".lane-tools/$run_name/source-sha.txt"
LC_ALL=C.UTF-8 PATH="$PWD/.lane-tools/bin:$PATH" JAVA_TOOL_OPTIONS=-Xmx2g timeout 15m ./mill -i 'cache.jvm[2.13.16].test.testOnly' "$@" > ".lane-tools/$run_name/test.log" 2>&1
rc=$?
echo "$rc" > ".lane-tools/$run_name/exit-code.txt"
python3 - "$run_name" <<'PY'
from pathlib import Path
import shutil,sys,xml.etree.ElementTree as E
out=Path('.lane-tools')/sys.argv[1]
for p in Path('out/cache/jvm/2.13.16/test/testOnly.dest').glob('*.xml'):
 shutil.copy2(p,out/p.name)
 r=E.parse(p).getroot(); rows=r.findall('.//testcase'); bad=[x for x in rows if x.find('failure') is not None or x.find('error') is not None]
 print(sys.argv[1],p.name,'rows',len(rows),'bad',len(bad))
 for x in bad:
  n=x.find('failure')
  if n is None: n=x.find('error')
  print(x.attrib, n.attrib)
PY
exit "$rc"

#!/usr/bin/env bash
set -euo pipefail
repo=$1
here=$(cd "$(dirname "$0")" && pwd)
cd "$repo"
python3 - "$here" <<'PY'
import json,sys
from pathlib import Path
root=Path(sys.argv[1]);rows=[]
for i in range(1,31):
 r=json.loads((root/f'round-{i:02d}-audit.json').read_text())
 assert r['pass']==3 and r['fail']==0 and r['retries']==0,r
 rows.extend(r['rows'])
from collections import Counter
assert sorted(Counter(r['name'] for r in rows).values())==[30,30,30]
PY
git fetch origin
git rebase origin/main
python3 "$here/run-command.py" "$repo" framing python3 "$here/mill-direct.py" --no-server -j1 'cache.jvm[2.13.16].test.testOnly' \
  coursier.cache.DownloadLengthIntegrityTests coursier.cache.DownloadResponseFramingTests coursier.cache.DownloadBufferCapacityTests
python3 "$here/audit-phase.py" "$repo" framing 103
python3 - "$here/framing-audit.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1]));assert r['pass']==103 and r['fail']==0,r
PY
git fetch origin
git rebase origin/main
COURSIER_CUSTOMPROTOCOL_BASE="$repo" python3 "$here/run-command.py" "$repo" cache python3 "$here/mill-direct.py" --no-server -j1 'cache.jvm[2.13.16].test.testLocal'
python3 "$here/audit-phase.py" "$repo" cache 214
python3 - "$here/cache-audit.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1]));assert r['pass']==214 and r['fail']==0,r
PY

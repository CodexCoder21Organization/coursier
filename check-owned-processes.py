"""Check recorded process identities without saving process command lines."""
from pathlib import Path
from datetime import datetime, timezone
import os, json, subprocess

E = Path(__file__).resolve().parent
R = Path('/tmp/I419i-runtime')
def stat(pid):
    try:
        fields = Path('/proc/' + str(pid) + '/stat').read_text().rsplit(')', 1)[1].split()
        return {'parent': int(fields[1]), 'start': int(fields[19]), 'state': fields[0]}
    except (OSError, ValueError, IndexError):
        return None

identities = set()
cleanup_records = []
for base in [E, R]:
    for file in base.rglob('*cleanup.json'):
        record = json.loads(file.read_text())
        cleanup_records.append({'file': str(file), 'reportedRemaining': record.get('remaining', [])})
        for item in record.get('history', []):
            identities.update((int(pid), int(start)) for pid, start in item.get('identities', {}).items())
live = []
zombies = []
for pid, start in sorted(identities):
    current = stat(pid)
    if current is not None and current['start'] == start:
        (zombies if current['state'] == 'Z' else live).append({'pid': pid, 'start': start})

ancestors = set()
pid = os.getpid()
while pid and pid not in ancestors:
    ancestors.add(pid)
    current = stat(pid)
    if current is None:
        break
    pid = current['parent']
scripts = {str(file) for file in E.glob('*.py')}
unrecorded = []
# Required ps check: inspect its output only in memory; never write the rows.
ps = subprocess.check_output('ps -eo pid,args -ww | grep -v grep', shell=True, text=True)
for row in ps.splitlines()[1:]:
    pid = int(row.strip().split(None, 1)[0])
    if pid in ancestors:
        continue
    try:
        args = Path('/proc/' + str(pid) + '/cmdline').read_bytes().decode().split('\0')
    except (OSError, UnicodeError):
        continue
    current = stat(pid)
    if current is None or current['state'] == 'Z':
        continue
    owned_marker = any(arg in scripts or arg.startswith(str(R) + '/') or
                       (':' + str(R) + '/') in arg for arg in args)
    if owned_marker and (pid, current['start']) not in identities:
        unrecorded.append({'pid': pid, 'start': current['start']})
report = {'utc': datetime.now(timezone.utc).isoformat(), 'identityCount': len(identities),
          'cleanupRecords': cleanup_records, 'liveOwnedIdentities': live,
          'ownedZombies': zombies, 'unrecordedLaneProcesses': unrecorded,
          'rawPsOrCommandLinesSaved': False}
(E / 'final-process-cleanup.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({key: report[key] for key in ['identityCount', 'liveOwnedIdentities',
                                             'ownedZombies', 'unrecordedLaneProcesses']}))
assert not live and not zombies and not unrecorded

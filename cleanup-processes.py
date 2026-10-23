"""Audit only recorded children by PID and start time; never store command lines."""
from pathlib import Path
import json,subprocess,os,signal,time
out=Path(__file__).resolve().parent
owned={}
for p in out.rglob('*-owned.json'):
 owned.update(json.loads(p.read_text()))
p=out/'cancelled-slot-wait.json'
if p.exists(): owned.update(json.loads(p.read_text()))
rows={}
for p in Path('/proc').iterdir():
 if p.name.isdigit():
  try:
   f=(p/'stat').read_text().rsplit(')',1)[1].split()
   rows[p.name]={'state':f[0],'start':f[19]}
  except (OSError,ValueError,IndexError): pass
alive=[int(pid) for pid,born in owned.items() if pid in rows and rows[pid]['start']==born and rows[pid]['state']!='Z']
# This script is run only after every tracked command session has completed.
for pid in alive: os.kill(pid,signal.SIGTERM)
if alive: time.sleep(1)
remaining=[]
for pid in alive:
 try:
  f=(Path('/proc')/str(pid)/'stat').read_text().rsplit(')',1)[1].split()
  if f[19]==owned[str(pid)] and f[0]!='Z': remaining.append(pid)
 except (OSError,ValueError,IndexError): pass
for pid in remaining: os.kill(pid,signal.SIGKILL)
if remaining: time.sleep(1)
live_after=[]
for pid,born in owned.items():
 try:
  f=(Path('/proc')/pid/'stat').read_text().rsplit(')',1)[1].split()
  if f[19]==born and f[0]!='Z': live_after.append(int(pid))
 except (OSError,ValueError,IndexError): pass
assert not live_after, 'A recorded process remains alive after cleanup'
# Required ps audit is read in memory, with no raw process arguments written or printed.
raw=subprocess.check_output(['bash','-c','ps -eo pid,args -ww | grep -v grep'],text=True)
ids={line.split(None,1)[0] for line in raw.splitlines()[1:] if line.split()}
report={'recordedProcessCount':len(owned),'liveRecordedBeforeCleanup':alive,
 'terminatedRecordedProcesses':alive,'killedRecordedProcesses':remaining,
 'liveRecordedAfterCleanup':live_after,'requiredPsAuditRan':True,'rawArgumentsSaved':False}
(out/'process-cleanup.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))

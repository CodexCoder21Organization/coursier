"""Check and stop only recorded PID/start-time pairs; save no process command lines."""
from pathlib import Path
import json, os, signal, subprocess, time
root=Path(__file__).resolve().parent
owned={}
for p in root.glob('*-owned.json'):
    owned.update({int(pid):born for pid,born in json.loads(p.read_text()).items()})
def live():
    found=[]
    for pid,born in owned.items():
        p=Path('/proc')/str(pid)/'stat'
        try:
            fields=p.read_text().rsplit(')',1)[1].split()
            if fields[19]==born and fields[0]!='Z': found.append(pid)
        except (OSError,IndexError): pass
    return found
before=live()
for pid in before: os.kill(pid,signal.SIGTERM)
deadline=time.monotonic()+30
while live() and time.monotonic()<deadline: time.sleep(0.2)
remaining=live()
for pid in remaining: os.kill(pid,signal.SIGKILL)
# Perform the requested ps audit in memory, retaining only PID membership counts.
rows=subprocess.check_output(['bash','-c','ps -eo pid,args -ww | grep -v grep'],text=True)
ps_pids={int(line.split(None,1)[0]) for line in rows.splitlines()[1:] if line.strip()}
after=live()
result={'recordedPidStartPairs':len(owned),'ownedLiveBeforeCleanup':before,'sentTerm':before,'sentKill':remaining,'ownedLiveAfterCleanup':after,'psAuditPerformed':True,'ownedLiveInPs':[pid for pid in after if pid in ps_pids]}
(root/'cleanup.json').write_text(json.dumps(result,indent=2)+'\n')
assert not after,result
print(json.dumps(result))

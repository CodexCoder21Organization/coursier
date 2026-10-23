"""Check only this lane's recorded process identities without retaining process arguments."""
import argparse,json,os,signal,subprocess,time
from pathlib import Path
from datetime import datetime,timezone
p=argparse.ArgumentParser();p.add_argument('--work-dir',required=True,type=Path);p.add_argument('--repo',required=True,type=Path);a=p.parse_args()
identities=[json.loads((a.work_dir/'build-process.json').read_text())]+json.loads((a.work_dir/'publisher-processes.json').read_text())
tracked={int(x['pid']):str(x['startTicks']) for x in identities}
ancestors=set();pid=os.getpid()
while pid>1:
 ancestors.add(pid)
 try:pid=int(Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split()[1])
 except FileNotFoundError:break

def inspect():
 found=[]
 for entry in Path('/proc').iterdir():
  if not entry.name.isdigit() or int(entry.name) in ancestors:continue
  try:
   fields=(entry/'stat').read_text().rsplit(')',1)[1].split(); pid=int(entry.name)
   same=pid in tracked and fields[19]==tracked[pid]
   own_group=int(fields[2]) in tracked
   cwd=(entry/'cwd').resolve()
   source_cwd=cwd==a.repo.resolve() or a.repo.resolve() in cwd.parents
   if same or own_group or source_cwd:found.append(dict(pid=pid,startTicks=fields[19],group=int(fields[2]),state=fields[0]))
  except (FileNotFoundError,ProcessLookupError,PermissionError):pass
 return found
before=inspect();terminated=[]
for row in before:
 assert row['group'] in tracked or (row['pid'] in tracked and row['startTicks']==tracked[row['pid']]),row
 terminated.append(row['pid']);os.kill(row['pid'],signal.SIGTERM)
cap=time.monotonic()+30
while inspect() and time.monotonic()<cap:time.sleep(1)
remaining=inspect()
# Inspect the required ps view in memory only; never retain process arguments.
view=subprocess.check_output(['bash','-c','ps -eo pid,args -ww | grep -v grep'],text=True)
ps_ids={int(line.strip().split(None,1)[0]) for line in view.splitlines()[1:] if line.strip()}
alive=[pid for pid,ticks in tracked.items() if Path(f'/proc/{pid}/stat').exists() and Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split()[19]==ticks]
result=dict(checkedAtUtc=datetime.now(timezone.utc).isoformat(),trackedProcesses=len(tracked),trackedIdentities=identities,terminatedPids=terminated,remainingProcesses=remaining,remainingTrackedIdentities=alive,psCheckPerformed=True,psTrackedPidsPresent=sorted(set(alive)&ps_ids),oomEvents=Path('/sys/fs/cgroup/memory.events').read_text())
assert not remaining and not alive,result
Path(__file__).resolve().with_name('cleanup-check.json').write_text(json.dumps(result,indent=2)+'\n')
print('Cleanup passed:',len(tracked),'recorded identities absent; no remaining process groups or source-checkout processes.')

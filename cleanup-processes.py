"""Check only recorded process identities and the lane's own source directory."""
import argparse
from pathlib import Path
import datetime
import json
import os
import subprocess

p=argparse.ArgumentParser()
p.add_argument('--work-dir',type=Path,required=True)
p.add_argument('--source-dir',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
records=[]
for filename in ['build-process.json','build-process-identities.json','publisher-processes.json',
                 'publish-root-processes.json','tracker-pid.json']:
    path=a.work_dir/filename
    if path.exists():
        data=json.loads(path.read_text())
        records.extend(data if isinstance(data,list) else [data])
identities={(r['pid'],str(r['startTicks'])) for r in records}
snapshot={}
for path in Path('/proc').iterdir():
    if path.name.isdigit():
        try:
            fields=(path/'stat').read_text().rsplit(')',1)[1].split()
            snapshot[int(path.name)]={'startTicks':fields[19],'group':int(fields[2]),'session':int(fields[3])}
        except (FileNotFoundError,PermissionError,ProcessLookupError): pass
remaining=[pid for pid,ticks in identities if pid in snapshot and snapshot[pid]['startTicks']==ticks]
sessions={r['pid'] for r in records}
remaining_group_members=[pid for pid,info in snapshot.items() if info['session'] in sessions or info['group'] in sessions]
source_cwd=[]
for pid in snapshot:
    if pid!=os.getpid():
        try:
            if (Path('/proc')/str(pid)/'cwd').resolve()==a.source_dir.resolve(): source_cwd.append(pid)
        except (FileNotFoundError,PermissionError,ProcessLookupError): pass
# Inspect the requested ps output in memory; never write or print process arguments.
ps=subprocess.Popen(['ps','-eo','pid,args','-ww'],stdout=subprocess.PIPE,text=True)
filtered=subprocess.run(['grep','-v','grep'],stdin=ps.stdout,stdout=subprocess.PIPE,text=True,check=True)
ps.stdout.close()
assert ps.wait()==0
visible={int(line.strip().split(None,1)[0]) for line in filtered.stdout.splitlines()[1:] if line.strip()}
assert not (set(remaining)|set(remaining_group_members)) & visible
assert not remaining and not remaining_group_members and not source_cwd
result={'checkedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'recordedIdentityCount':len(identities),'remainingTrackedPids':remaining,
        'remainingGroupMembers':remaining_group_members,'remainingSourceCwdPids':source_cwd,
        'psOutputCheckedWithoutSavingArguments':True,'cleanupNeeded':False,
        'cgroupMemoryEvents':Path('/sys/fs/cgroup/memory.events').read_text()}
a.output.write_text(json.dumps(result,indent=2)+'\n')
print(f'Cleanup verified: {len(identities)} recorded identities absent; no lane process remains.')

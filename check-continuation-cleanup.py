"""Check recorded numeric process identities without saving process command lines."""
from pathlib import Path
import json,subprocess,os
out=Path(__file__).resolve().parent;owned={}
for p in out.glob('*-owned.json'):owned.update(json.loads(p.read_text()))
rows={}
for p in Path('/proc').iterdir():
    if p.name.isdigit():
        try:
            f=(p/'stat').read_text().rsplit(')',1)[1].split()
            rows[int(p.name)]=(int(f[1]),f[19],f[0])
        except (OSError,ValueError,IndexError):pass
live=[];zombies=[]
for pid,born in owned.items():
    item=rows.get(int(pid))
    if item and item[1]==born:(zombies if item[2]=='Z' else live).append(int(pid))
ancestors={os.getpid()};cursor=os.getpid()
while cursor in rows and rows[cursor][0] not in ancestors:
    cursor=rows[cursor][0];ancestors.add(cursor)
ps=subprocess.Popen(['ps','-eo','pid,args','-ww'],stdout=subprocess.PIPE)
filtered=subprocess.check_output(['grep','-v','grep'],stdin=ps.stdout,text=True)
ps.stdout.close();ps.wait()
root=out.parent/'FCOURS3-coursier-20261020';extra=[]
for line in filtered.splitlines()[1:]:
    pair=line.strip().split(None,1)
    if len(pair)==2 and int(pair[0]) not in ancestors and (str(root) in pair[1] or str(out) in pair[1]):extra.append(int(pair[0]))
result={'liveOwned':live,'ownedZombies':zombies,'matchingUnrecordedPids':extra,'ownedPidCount':len(owned),'psCheckRan':True,'rawProcessOutputSaved':False,'memoryEvents':Path('/sys/fs/cgroup/memory.events').read_text()}
(out/'process-cleanup.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result));assert not live and not zombies and not extra

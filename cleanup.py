from pathlib import Path
import os,signal,subprocess,json,time
out=Path(__file__).resolve().parent
clonepath='/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/RCOURS3-coursier-20261020'
def snapshot():
 result={}
 for p in Path('/proc').iterdir():
  if p.name.isdigit():
   try:
    tail=(p/'stat').read_text().rsplit(')',1)[1].split()
    result[int(p.name)]=(int(tail[1]),tail[19],tail[0])
   except (OSError,ValueError,IndexError):pass
 return result
owned={}
for p in Path('/proc').iterdir():
 if p.name.isdigit():
  try:
   args=(p/'cmdline').read_bytes().decode().split('\0')
   if Path(args[0]).name=='gh' and args[1:3]==['repo','clone'] and clonepath in args:
    owned[int(p.name)]=snapshot()[int(p.name)][1]
  except (OSError,ValueError,IndexError):pass
rows=snapshot(); parents=set(owned)
changed=True
while changed:
 changed=False
 for pid,(ppid,start,state) in rows.items():
  if ppid in parents and pid not in parents:
   parents.add(pid);owned[pid]=start;changed=True
(out/'clone-owned.json').write_text(json.dumps(owned,indent=2)+'\n')
terminated=[]
for pid,born in reversed(list(owned.items())):
 row=snapshot().get(pid)
 if row and row[1]==born and row[2]!='Z':
  os.kill(pid,signal.SIGTERM);terminated.append(pid)
end=time.monotonic()+10
while time.monotonic()<end:
 rows=snapshot()
 live=[pid for pid,born in owned.items() if pid in rows and rows[pid][1]==born and rows[pid][2]!='Z']
 if not live:break
 time.sleep(.2)
for pid in live:os.kill(pid,signal.SIGKILL)
allowned=dict(owned)
for f in out.glob('*-owned.json'):
 allowned.update({int(k):str(v) for k,v in json.loads(f.read_text()).items()})
rows=snapshot()
live=[pid for pid,born in allowned.items() if pid in rows and rows[pid][1]==born and rows[pid][2]!='Z']
# Required process-table audit; raw arguments are never written or printed.
ps=subprocess.run('ps -eo pid,args -ww | grep -v grep',shell=True,stdout=subprocess.PIPE,check=True,text=True)
seen={int(line.split(None,1)[0]) for line in ps.stdout.splitlines()[1:] if line.split(None,1)[0].isdigit()}
result={'trackedProcessCount':len(allowned),'terminatedOptionalClonePids':terminated,'liveTrackedPids':live,'psAuditCompleted':True,'liveTrackedInPs':[pid for pid in live if pid in seen],'rawArgumentsSaved':False}
(out/'process-cleanup.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))

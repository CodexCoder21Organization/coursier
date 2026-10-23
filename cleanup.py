from pathlib import Path
import json,os,signal,subprocess,time
p=Path(__file__).resolve().parent
owned={}
for f in p.glob('*-owned.json'):
 owned.update(json.loads(f.read_text()))
def live():
 rows=[]
 for pid,born in owned.items():
  d=Path('/proc')/pid
  try:
   fields=(d/'stat').read_text().rsplit(')',1)[1].split()
   if fields[19]==born and fields[0]!='Z': rows.append(int(pid))
  except (OSError,IndexError): pass
 return rows
before=live();killed=[]
for pid in before:
 os.kill(pid,signal.SIGTERM);killed.append(pid)
deadline=time.monotonic()+10
while live() and time.monotonic()<deadline: time.sleep(.1)
for pid in live(): os.kill(pid,signal.SIGKILL)
deadline=time.monotonic()+10
while live() and time.monotonic()<deadline: time.sleep(.1)
# Inspect the requested process list in memory; never retain command lines.
ps=subprocess.check_output('ps -eo pid,args -ww | grep -v grep',shell=True,text=True)
ps_pids={int(line.split(None,1)[0]) for line in ps.splitlines() if line.split(None,1)[0].isdigit()}
remaining=live()
root=p.parent/'RCOURS4-coursier-20261020'
workspace_java=[]
for line in ps.splitlines():
 parts=line.split(None,1)
 if len(parts)!=2 or not parts[0].isdigit(): continue
 pid=int(parts[0])
 try: comm=(Path('/proc')/str(pid)/'comm').read_text().strip()
 except OSError: continue
 if comm=='java' and (str(root) in parts[1] or str(p) in parts[1]): workspace_java.append(pid)
assert workspace_java==[],workspace_java
result={'trackedPidStartPairs':len(owned),'liveOwnedBefore':before,'terminatedRecordedPids':killed,'liveOwnedAfter':remaining,'requestedPsCheckPerformed':True,'liveOwnedInPsAfter':[pid for pid in remaining if pid in ps_pids],'rawProcessCommandLinesStored':False,'javaProcessesForReviewWorkspace':workspace_java}
assert remaining==[],result
(p/'process-cleanup.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))

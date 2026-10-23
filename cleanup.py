"""Check only recorded children and this lane's exact helper entry points."""
from pathlib import Path
import json,os,signal,subprocess,time
OUT=Path(__file__).resolve().parent
owned={}
for f in OUT.glob("*-owned.json"):
 owned.update({int(k):v for k,v in json.loads(f.read_text()).items()})
helpers={str(OUT/name) for name in ["gate.sh","gates.py","run-command.py","mill-direct.py"]}
for d in Path("/proc").iterdir():
 if not d.name.isdigit():continue
 try:
  args=(d/"cmdline").read_bytes().split(b"\0")
  fields=(d/"stat").read_text().rsplit(")",1)[1].split()
  if len(args)>1 and args[1].decode(errors="replace") in helpers:
   owned[int(d.name)]=fields[19]
 except (OSError,ValueError,IndexError):pass

def live(pid,born):
 try:
  fields=(Path("/proc")/str(pid)/"stat").read_text().rsplit(")",1)[1].split()
  return fields[19]==born and fields[0]!="Z"
 except (OSError,ValueError,IndexError):return False
remaining=[pid for pid,born in owned.items() if live(pid,born)]
for pid in remaining:os.kill(pid,signal.SIGTERM)
deadline=time.monotonic()+10
while any(live(pid,owned[pid]) for pid in remaining) and time.monotonic()<deadline:time.sleep(.1)
for pid in remaining:
 if live(pid,owned[pid]):os.kill(pid,signal.SIGKILL)
ps=subprocess.Popen(["ps","-eo","pid,args","-ww"],stdout=subprocess.PIPE,text=True)
filtered=subprocess.Popen(["grep","-v","grep"],stdin=ps.stdout,stdout=subprocess.PIPE,text=True)
ps.stdout.close()
visible=set()
for line in filtered.stdout:
 token=line.split(None,1)[0] if line.split(None,1) else ""
 if token.isdigit():visible.add(int(token))
filtered.wait();ps.wait()
left=[pid for pid,born in owned.items() if pid in visible and live(pid,born)]
result={"trackedPids":len(owned),"terminatedRecordedPids":remaining,"liveOwnedPids":left,"psCommandChecked":True,"rawCommandLinesSaved":False}
(OUT/"process-cleanup.json").write_text(json.dumps(result,indent=2)+"\n")
assert not left,result
print(json.dumps(result))

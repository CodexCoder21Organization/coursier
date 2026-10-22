from pathlib import Path
from datetime import datetime,timezone
import subprocess,os,time,json,signal
import tempfile
ROOT=Path.cwd();OUT=Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/out');WORK=Path('/tmp/I419i-runtime')
HISTORY=[];OWNED={};GROUPS=set();SELF=os.getpid()

def facts():
 rows={}
 for p in Path('/proc').iterdir():
  if p.name.isdigit():
   try:
    s=(p/'stat').read_text().rsplit(')',1)[1].split();rows[int(p.name)]=(int(s[1]),int(s[19]),s[0],int(s[2]))
   except (OSError,ValueError,IndexError):pass
 return rows

def capture():
 rows=facts();parents={pid for pid,start in OWNED.items() if pid in rows and rows[pid][1]==start and rows[pid][2]!='Z'}|{SELF}
 # A numeric group is usable only while an exact previously owned member anchors it.
 anchored_groups={rows[pid][3] for pid in OWNED if pid in parents and pid!=SELF}
 GROUPS.intersection_update(anchored_groups)
 changed=True
 while changed:
  changed=False
  for pid,(parent,start,state,group) in rows.items():
   if pid!=SELF and (parent in parents or group in GROUPS) and (pid not in OWNED or OWNED[pid]!=start):
    OWNED[pid]=start;parents.add(pid);changed=True
 snapshot=WORK/'control-processes.json'
 fd,temp_path=tempfile.mkstemp(prefix='.control-processes.',dir=WORK)
 try:
  with os.fdopen(fd,'w') as stream:
   json.dump(OWNED,stream)
   stream.flush()
   os.fsync(stream.fileno())
  os.replace(temp_path,snapshot)
 except BaseException:
  try:os.close(fd)
  except OSError:pass
  try:os.unlink(temp_path)
  except FileNotFoundError:pass
  raise
 return rows

def alive():
 rows=capture()
 return [pid for pid,start in OWNED.items() if pid in rows and rows[pid][1]==start and rows[pid][2]!='Z']

def send(pid,sig):
 rows=facts()
 if pid not in rows or rows[pid][1]!=OWNED[pid] or rows[pid][2]=='Z':return
 try:
  fd=os.pidfd_open(pid)
  try:
   rows=facts()
   if pid in rows and rows[pid][1]==OWNED[pid]:signal.pidfd_send_signal(fd,sig)
  finally:os.close(fd)
 except ProcessLookupError:pass

def cleanup():
 for pid in reversed(alive()):send(pid,signal.SIGTERM)
 end=time.monotonic()+10
 while alive() and time.monotonic()<end:time.sleep(1)
 for pid in reversed(alive()):send(pid,signal.SIGKILL)
 end=time.monotonic()+10
 while alive() and time.monotonic()<end:time.sleep(1)
 return alive()

def note(message):
 with (Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/out/I419i-evidence/findings.md')).open('a') as f:f.write('\n'+datetime.now(timezone.utc).isoformat()+' OBSERVED: '+message+' INFER: Next checkpoint is phase completion and exact XML/attempt audit.\n')

def memory():
 end=time.monotonic()+600
 while True:
  subprocess.run(['free','-m'],check=True)
  value=int(next(x.split()[1] for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:')))//1024
  if value>=4000:return
  if time.monotonic()>=end:note('Memory wait reached10minutes; using the single required next command.');return
  time.sleep(60)

def run(cmd,name,env=None,cap=None):
 # This helper is used only for targeted local commands.
 memory();child=None;started=time.monotonic();next_note=started+300
 with (WORK/(name+'.log')).open('w') as log:
  try:
   child=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT,env=env,start_new_session=True)
   initial=facts().get(child.pid)
   if initial is not None:OWNED[child.pid]=initial[1];GROUPS.add(initial[3])
   capture()
   while child.poll() is None:
    capture()
    if cap is not None and time.monotonic()-started>=cap:raise TimeoutError('Targeted command cap reached')
    if time.monotonic()>=next_note:
     headings=[s for s in (OUT/'full-I419i.md').read_text().splitlines() if s.startswith('## Addendum') or s.startswith('## RESUME')]
     note(name+' remains active; addenda rechecked '+repr(headings)+'.');next_note+=300
    time.sleep(2)
   code=child.wait();(WORK/(name+'.exit')).write_text(str(code)+'\n')
  finally:
   remaining=cleanup()
   if child is not None and child.poll() is None:
    try:child.wait(timeout=2)
    except subprocess.TimeoutExpired:raise RuntimeError('Owned root process could not be reaped within the cleanup bound')
   if remaining:raise RuntimeError('Owned process cleanup did not finish; retain ownership evidence')
   HISTORY.append({'phase':name,'identities':dict(OWNED)})
   (WORK/'control-history.json').write_text(json.dumps(HISTORY))
   OWNED.clear();GROUPS.clear()
 if code!=0:raise RuntimeError(name+' failed with exit '+str(code))

def interrupt(signum,frame):raise InterruptedError('Targeted controller received signal '+str(signum))
for sig in [signal.SIGTERM,signal.SIGINT,signal.SIGHUP]:signal.signal(sig,interrupt)

import sys
try:
 run(sys.argv[2:],sys.argv[1],cap=3600)
finally:
 remaining=cleanup()
 (WORK/(sys.argv[1]+"-cleanup.json")).write_text(json.dumps({"remaining":remaining,"history":HISTORY},indent=2))

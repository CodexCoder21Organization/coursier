"""Run the original acceptance cohort after the independent one-worker preflight."""
from pathlib import Path
from datetime import datetime,timezone
import subprocess,sys,time,json,os,signal
E=Path(__file__).resolve().parent;R=Path('/tmp/I419i-runtime');CORE=Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-kompile-core-20261020');SLOT=E.parent/'full-suite.lock'
(E/'gate-controller.pid').write_text(str(os.getpid())+'\n')
owned=False;child=None
label='I419i four-worker acceptance gate pid='+str(os.getpid())+'\n'
def note(s):
 with (E/'findings.md').open('a') as f:f.write('\n'+datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')+' — OBSERVED: '+s+' INFER: Next checkpoint is all thirty complete iteration markers and the exact XML/attempt audit.\n')
def stop(signum,frame):
 if child is not None and child.poll() is None:child.terminate();child.wait()
 raise InterruptedError('Gate controller received signal '+str(signum))
for sig in [signal.SIGTERM,signal.SIGINT,signal.SIGHUP]:signal.signal(sig,stop)
try:
 assert (R/'consumer2.exit').read_text().strip()=='0','Corrected consumer must pass before the gate'
 deadline=json.loads((E/'gate-slot-deadline.json').read_text())['deadlineEpoch'];next_note=0
 while True:
  try:SLOT.mkdir();owned=True;break
  except FileExistsError:
   if time.time()>=deadline:raise TimeoutError('Original three-hour shared-slot wait deadline reached; no gate started')
   if time.monotonic()>=next_note:
    holder=(SLOT/'owner').read_text().strip() if (SLOT/'owner').exists() else 'owner not yet written'
    note('Gate waiting for the shared slot, held by '+holder+'.');next_note=time.monotonic()+300
   time.sleep(60)
 (SLOT/'owner').write_text(label)
 note('Acquired the shared slot for the four-worker thirty-iteration gate after a passing one-worker consumer check.')
 subprocess.run(['free','-m'],check=True)
 child=subprocess.Popen([sys.executable,str(E/'supervise.py'),'gate-controller',sys.executable,str(E/'run-gate.py'),'fixed-cache'],cwd=CORE)
 status=child.wait();child=None
 (E/'gate-controller.exit').write_text(str(status)+'\n')
 if status:raise RuntimeError('Gate controller returned a nonzero verdict; audit raw terminal counts before classifying it')
except BaseException as ex:
 note(type(ex).__name__+': '+str(ex)+'.')
 (E/'gate-controller-memory.events').write_text(Path('/sys/fs/cgroup/memory.events').read_text())
 raise
finally:
 if owned:
  assert (SLOT/'owner').read_text()==label,'Shared slot owner changed unexpectedly'
  (SLOT/'owner').unlink();SLOT.rmdir()
  note('Released the shared slot after the gate controller exited.')

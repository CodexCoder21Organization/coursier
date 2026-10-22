"""Own the shared slot for the local manager build and exact four-worker gate."""
from pathlib import Path
from datetime import datetime,timezone
import subprocess,sys,time,json,os,signal,hashlib,zipfile
E=Path(__file__).resolve().parent
CORE=Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-kompile-core-20261020')
RT=Path('/tmp/I419i-runtime')
SLOT=E.parent/'full-suite.lock'
child=None;owned=False
(E/'measurement-controller.pid').write_text(str(os.getpid())+'\n')
def note(s):
 with (E/'findings.md').open('a') as f:f.write('\n'+datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')+' — OBSERVED: '+s+' INFER: Next checkpoint is child byte verification then the exact thirty-iteration gate.\n')
def stop(signum,frame):
 global child
 if child is not None and child.poll() is None:child.terminate();child.wait()
 raise InterruptedError('Measurement controller received signal '+str(signum))
for sig in [signal.SIGTERM,signal.SIGINT,signal.SIGHUP]:signal.signal(sig,stop)
def run(name,cmd):
 global child
 child=subprocess.Popen([sys.executable,str(E/'supervise.py'),name,*cmd],cwd=CORE)
 status=child.wait();child=None
 if status:raise RuntimeError(name+' failed; inspect tracked local logs, no retry is scheduled')
def free():subprocess.run(['free','-m'],check=True)
try:
 deadline=time.monotonic()+10800;next_note=0
 while True:
  try:SLOT.mkdir();owned=True;break
  except FileExistsError:
   if time.monotonic()>=deadline:raise TimeoutError('Shared slot wait reached three hours; stop without the gate')
   if time.monotonic()>=next_note:
    holder=(SLOT/'owner').read_text().strip() if (SLOT/'owner').exists() else 'owner not yet written'
    note('Waiting for full-suite.lock, held by '+holder+'.');next_note=time.monotonic()+300
   time.sleep(60)
 (SLOT/'owner').write_text('I419i local snapshot build and gate pid='+str(os.getpid())+'\n')
 note('Acquired full-suite.lock for the manager build, consumer verification and four-worker gate.')
 RT.joinpath('launcher').mkdir(exist_ok=True);RT.joinpath('empty').mkdir(exist_ok=True)
 subprocess.run(['git','fetch','origin'],cwd=CORE,check=True)
 subprocess.run(['git','rebase','origin/main'],cwd=CORE,check=True)
 (RT/'manager-source-head.txt').write_text(subprocess.check_output(['git','rev-parse','HEAD'],cwd=CORE,text=True))
 free()
 run('compile-launchers',['javac','-cp',str(RT/'manager.jar'),'-d',str(RT/'launcher'),str(E/'CoreBuild.java'),str(E/'ControlAttempts.java')])
 free()
 run('build-manager',['java','-cp',str(RT/'launcher')+':'+str(RT/'manager.jar'),'CoreBuild',str(CORE),str(RT/'build-cache'),str(RT/'manager-built.jar')])
 with zipfile.ZipFile(RT/'cache-snapshot.jar') as fixed,zipfile.ZipFile(RT/'manager-built.jar') as manager:
  b=json.loads((E/'final-snapshot-binding.json').read_text())
  for name,row in b['changedClasses'].items():
   assert manager.read(name)==fixed.read(name),name
 (RT/'manager-built.jar').replace(RT/'manager.jar')
 note('Outer manager rebuilt locally and all five changed download class entries equal the final cache snapshot.')
 free()
 run('consumer2',['taskset','-c','0,1','java','-Dcontrol.sourceRoot='+str(RT/'empty'),'-cp',str(RT/'launcher')+':'+str(RT/'manager.jar'),'ControlAttempts',str(CORE),str(RT/'gate-cache'),str(RT/'consumer-raw-2'),'1','1',str(CORE/'tests/testSmallResolverResponsesBoundTransferBuffers.kts')])
 note('Consumer buffer reproducer passed one single attempt with all five loaded download class hashes checked.')
 free()
 run('gate-controller',[sys.executable,str(E/'run-gate.py'),'fixed-cache'])
 (E/'measurement.exit').write_text('0\n')
except BaseException as ex:
 note(type(ex).__name__+': '+str(ex)+'.')
 (E/'measurement.exit').write_text('1\n')
 (E/'measurement-memory.events').write_text(Path('/sys/fs/cgroup/memory.events').read_text())
 raise
finally:
 if owned:
  (SLOT/'owner').unlink();SLOT.rmdir()
  note('Released the shared slot after the measurement controller reached its terminal state.')

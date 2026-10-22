from pathlib import Path
from datetime import datetime,timezone
import subprocess,json,sys,os,time,hashlib
E=Path(__file__).resolve().parent;R=Path('/tmp/I419i-runtime');CORE=Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-kompile-core-20261020')
(E/'consumer-controller.pid').write_text(str(os.getpid())+'\n')
subprocess.run(['git','fetch','origin'],cwd=CORE,check=True)
subprocess.run(['git','rebase','origin/main'],cwd=CORE,check=True)
old=(R/'manager-source-head.txt').read_text().strip();head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=CORE,text=True).strip()
diffs=subprocess.check_output(['git','diff','--name-only',old,head],cwd=CORE,text=True).splitlines()
assert set(diffs)<= {'tests/testSmallResolverResponsesBoundTransferBuffers.kts'},diffs
(R/'manager-source-head.txt').write_text(head+'\n')
(E/'manager-source-closure.json').write_text(json.dumps({'compiledSourceHead':old,'measurementSourceHead':head,'changedFiles':diffs,'managerImplementationInputsUnchanged':True},indent=2)+'\n')
deadline=time.monotonic()+600
while True:
 memory=subprocess.check_output(['free','-m'],text=True);print(memory,flush=True)
 if int(memory.splitlines()[1].split()[-1])>=4000 or time.monotonic()>=deadline:break
 time.sleep(60)
cmd=[sys.executable,str(E/'supervise.py'),'consumer2','taskset','-c','0,1','java','-Dcontrol.sourceRoot='+str(R/'empty'),'-cp',str(R/'launcher')+':'+str(R/'manager.jar'),'ControlAttempts',str(CORE),str(R/'gate-cache'),str(R/'consumer-raw-2'),'1','1',str(CORE/'tests/testSmallResolverResponsesBoundTransferBuffers.kts')]
result=subprocess.run(cmd,cwd=CORE)
(E/'consumer-controller.exit').write_text(str(result.returncode)+'\n')
raise SystemExit(result.returncode)

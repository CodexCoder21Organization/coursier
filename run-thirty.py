"""Run thirty independent, single-attempt utest invocations; stop at the first failure."""
from pathlib import Path
import json, subprocess, sys
root=Path(sys.argv[1]).resolve()
out=Path(__file__).resolve().parent
suite='coursier.cache.DownloadEncodingIntegrityTests'
summary=[]
for iteration in range(1,31):
    phase=f'round-{iteration:02}'
    command=[sys.executable,str(out/'run-command.py'),str(root),phase,
             sys.executable,str(out/'mill-direct.py'),'--no-server','-j1',
             'cache.jvm[2.13.16].test.testOnly',suite]
    with (out/(phase+'-launch.log')).open('w') as log:
        subprocess.run(command,cwd=root,stdout=log,stderr=subprocess.STDOUT,check=True)
    subprocess.run([sys.executable,str(out/'audit-phase.py'),str(root),phase,'16'],check=True)
    result=json.loads((out/(phase+'-audit.json')).read_text())
    assert result['pass']==16 and result['fail']==0,result
    summary.append({k:v for k,v in result.items() if k!='rows'})
    (out/'thirty-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(f'Completed {iteration}/30 rounds, 16/16 single attempts, no retries',flush=True)

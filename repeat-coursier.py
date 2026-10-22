"""Run each Mill utest case once per measured run; preserve individual XML reports."""
from pathlib import Path
import subprocess, sys, json, time, xml.etree.ElementTree as ET
root=Path(sys.argv[1]); evidence=Path(__file__).resolve().parent
phase=sys.argv[2]; count=int(sys.argv[3]); results=[]
for i in range(1,count+1):
    subprocess.run(['git','fetch','origin'],cwd=root,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    subprocess.run(['git','rebase','--autostash','origin/main'],cwd=root,check=True,stdout=subprocess.DEVNULL)
    deadline=time.monotonic()+600
    while True:
        memory=subprocess.check_output(['free','-m'],text=True)
        if int(memory.splitlines()[1].split()[-1])>=4000 or time.monotonic()>=deadline: break
        time.sleep(60)
    with (evidence/f'{phase}-{i:02}.log').open('w') as log:
        log.write(memory);log.flush()
        r=subprocess.run(['./mill','--no-server','cache.jvm[2.13.16].test.testOnly','coursier.cache.DownloadBufferCapacityTests'],cwd=root,stdout=log,stderr=subprocess.STDOUT)
    if r.returncode==137:
        raise RuntimeError('Run exited 137; stop and inspect cgroup memory.events')
    xml=root/'out/cache/jvm/2.13.16/test/testOnly.dest/test-report.xml'
    target=evidence/f'{phase}-{i:02}.xml';target.write_bytes(xml.read_bytes())
    tests=[]
    for c in ET.parse(target).iter('testcase'):
        f=c.find('failure')
        tests.append({'name':c.attrib['name'],'failed':f is not None,'message':None if f is None else f.attrib.get('message')})
    if len(tests)!=13: raise RuntimeError(f'Expected thirteen cases, got {len(tests)}')
    if len(set(t['name'] for t in tests))!=13: raise RuntimeError('Duplicate cases imply more than one attempt')
    results.append({'iteration':i,'exit':r.returncode,'tests':tests,'attemptsPerCase':1})
    (evidence/f'{phase}-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(f'{phase} {i}/{count}: {sum(not t["failed"] for t in tests)} pass, {sum(t["failed"] for t in tests)} fail',flush=True)

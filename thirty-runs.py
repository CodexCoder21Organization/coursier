"""Thirty independent single-attempt targeted runs, stopping on the first failure."""
from pathlib import Path
import subprocess, time, json, sys, shutil
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
root = Path(sys.argv[1]).resolve()
out = Path(__file__).resolve().parent
summaries = []
for iteration in range(1, 31):
    label = f'repeat-{iteration:02d}'
    subprocess.run(['git', 'fetch', 'origin'], cwd=root, check=True, stdout=subprocess.DEVNULL)
    subprocess.run(['git', 'rebase', 'origin/main'], cwd=root, check=True, stdout=subprocess.DEVNULL)
    deadline = time.monotonic() + 600
    while True:
        memory = subprocess.check_output(['free', '-m'], text=True)
        if int(memory.splitlines()[1].split()[-1]) >= 4000 or time.monotonic() >= deadline:
            break
        time.sleep(60)
    (out / (label + '-memory.txt')).write_text(memory)
    head = subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
    started = time.monotonic()
    with (out / (label + '.log')).open('w') as log:
        result = subprocess.run(['./mill', '--no-server', '-j1', 'cache.jvm[2.13.16].test.testOnly',
            'coursier.cache.DownloadResponseFramingTests', 'coursier.cache.DownloadLengthIntegrityTests',
            'coursier.cache.DownloadBufferCapacityTests'], cwd=root, stdout=log, stderr=subprocess.STDOUT)
    xml_path = root/'out/cache/jvm/2.13.16/test/testOnly.dest/test-report.xml'
    shutil.copyfile(xml_path, out/(label+'.xml'))
    cases = ET.parse(xml_path).getroot().findall('.//testcase')
    failures = [c.attrib['classname']+'.'+c.attrib['name'] for c in cases
        if c.find('failure') is not None or c.find('error') is not None or c.find('skipped') is not None]
    names = [c.attrib['classname']+'.'+c.attrib['name'] for c in cases]
    summary = dict(iteration=iteration, head=head, exit=result.returncode, cases=len(cases),
        uniqueCases=len(set(names)), failures=failures, seconds=time.monotonic()-started,
        firstAttempts=len(cases), retries=0, completedAt=datetime.now(timezone.utc).isoformat())
    summaries.append(summary)
    (out/'thirty-runs-audit.json').write_text(json.dumps(summaries,indent=2)+'\n')
    print(json.dumps(summary),flush=True)
    if result.returncode in (137, -9):
        (out/(label+'-memory.events')).write_text(Path('/sys/fs/cgroup/memory.events').read_text())
    if result.returncode or failures or len(cases) != 94 or len(set(names)) != 94:
        sys.exit(1)

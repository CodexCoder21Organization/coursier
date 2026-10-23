"""Publish this one seven-module release once, refusing every claimed coordinate."""
import argparse
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import os
import time
import subprocess
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET

VERSION = '2.1.31-buffer6'
MODULES = ['cache-util', 'coursier-paths', 'coursier-util_2.13', 'coursier-core_2.13',
           'coursier-cache_2.13', 'coursier-proxy-setup', 'coursier_2.13']
p = argparse.ArgumentParser()
p.add_argument('--work-dir', type=Path, required=True)
p.add_argument('--evidence', type=Path, required=True)
a = p.parse_args()
BASE = a.work_dir.resolve()
EVIDENCE = a.evidence.resolve()

def utc():
    return datetime.now(timezone.utc).isoformat()

def status(url):
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            return response.status
    except urllib.error.HTTPError as failure:
        return failure.code

def digest(data):
    return hashlib.sha256(data).hexdigest()

def absence(module):
    url = f'https://kotlin.directory/io/get-coursier/{module}/{VERSION}/'
    files = sorted((BASE/'artifacts'/module).iterdir())
    checks = [{'url':u,'status':status(u)} for u in [url]+[url+f.name for f in files]]
    assert all(c['status'] == 404 for c in checks), checks
    metadata_url = f'https://kotlin.directory/io/get-coursier/{module}/maven-metadata.xml'
    with urllib.request.urlopen(metadata_url, timeout=30) as response:
        metadata = ET.fromstring(response.read())
    assert VERSION not in [v.text for v in metadata.findall('./versioning/versions/version')]
    return {'checkedAtUtc':utc(),'checks':checks,'metadataHasVersion':False,'metadataUrl':metadata_url}

ledger = EVIDENCE/'release-verification.json'
assert not ledger.exists(), 'A publication ledger already exists; inspect it and never repeat uploads.'
local = json.loads((EVIDENCE/'local-artifacts.json').read_text())
assert all(r['coordinate'].endswith(':'+VERSION) for r in local)
assert len(local) == 21 and {r['coordinate'].split(':')[1] for r in local} == set(MODULES)
for record in local:
    path=BASE/'artifacts'/record['coordinate'].split(':')[1]/record['file']
    assert digest(path.read_bytes()) == record['sha256'], path
proof = json.loads((EVIDENCE/'class-proof-before-publish.json').read_text())
assert proof['source'] == '119e39d993a0e5f96a489c9e9eddcd0e0acf9a4a'
preflight = {module:absence(module) for module in MODULES}
(EVIDENCE/'all-modules-prepublish-check.json').write_text(json.dumps(preflight,indent=2)+'\n')
records=[]
processes=[]
for module in MODULES:
    directory=BASE/'artifacts'/module
    files=sorted(directory.iterdir())
    assert len(files)==3 and all(f.is_file() for f in files)
    record={'coordinate':f'io.get-coursier:{module}:{VERSION}',
            'immediateAbsenceCheck':absence(module),'publishStartedAtUtc':utc(),
            'publishInvocations':1,'publishExitCode':None,'files':[]}
    records.append(record)
    ledger.write_text(json.dumps(records,indent=2)+'\n')
    with (BASE/f'publish-{module}.log').open('w') as log:
        deadline=time.monotonic()+600
        while True:
            memory=subprocess.check_output(['free','-m'],text=True)
            log.write(memory); log.flush()
            available=int(next(line.split()[6] for line in memory.splitlines() if line.startswith('Mem:')))
            if available>=4000 or time.monotonic()>=deadline: break
            time.sleep(60)
        process=subprocess.Popen(['coursier','launch',
            'community.kotlin.maven.artifact.publishing:community-kotlin-maven-artifact-publishing:0.0.6',
            '-r','https://kotlin.directory','--','--artifact-dir',str(directory),
            '--api-url','https://api.kotlin.directory/upload','--force'],
            stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        processes.append({'pid':process.pid,'startedAtUtc':utc(),
            'startTicks':Path(f'/proc/{process.pid}/stat').read_text().rsplit(')',1)[1].split()[19]})
        (BASE/'publisher-processes.json').write_text(json.dumps(processes,indent=2)+'\n')
        try:
            result=process.wait(timeout=600)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid,15)
            process.wait(timeout=30)
            record['deadlineExceeded']=True
            ledger.write_text(json.dumps(records,indent=2)+'\n')
            raise
    record['publishExitCode']=result
    record['publishFinishedAtUtc']=utc()
    ledger.write_text(json.dumps(records,indent=2)+'\n')
    assert result==0, f'Publisher failed for {module}; inspect the saved log without repeating the upload.'
    downloaded=BASE/'downloaded'/module
    downloaded.mkdir(parents=True,exist_ok=False)
    url=f'https://kotlin.directory/io/get-coursier/{module}/{VERSION}/'
    for path in files:
        with urllib.request.urlopen(url+path.name,timeout=60) as response:
            data=response.read()
        (downloaded/path.name).write_bytes(data)
        local_hash=digest(path.read_bytes())
        remote_hash=digest(data)
        record['files'].append({'url':url+path.name,'file':path.name,'size':len(data),
            'localSha256':local_hash,'downloadedSha256':remote_hash,'match':local_hash==remote_hash})
        ledger.write_text(json.dumps(records,indent=2)+'\n')
        assert local_hash==remote_hash, path.name
    with (EVIDENCE/'findings.md').open('a') as f:
        f.write(f'\n{utc()} — OBSERVED: Published {record["coordinate"]} once with exit 0 after immediate directory/binary/POM/source 404 checks and metadata absence. OBSERVED: All three downloaded SHA-256 values equal the local build. INFER: Next checkpoint is the remaining module uploads or final class proof.\n')
    print(f'Published and verified {module}: 3/3 files match.',flush=True)
assert len(records)==7 and sum(len(r['files']) for r in records)==21
print('Published seven coordinates once each; verified all 21 files.',flush=True)

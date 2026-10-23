"""Independently audit the release ledger against the actual local/downloaded files."""
import argparse
from pathlib import Path
import hashlib
import json

p=argparse.ArgumentParser()
p.add_argument('--evidence',type=Path,required=True)
p.add_argument('--work-dir',type=Path,required=True)
a=p.parse_args()
expected={'cache-util','coursier-paths','coursier-util_2.13','coursier-core_2.13',
          'coursier-cache_2.13','coursier-proxy-setup','coursier_2.13'}
version='2.1.31-buffer6'
ledger=json.loads((a.evidence/'release-verification.json').read_text())
manifest=json.loads((a.evidence/'local-artifacts.json').read_text())
assert len(ledger)==7 and len(manifest)==21
assert {r['coordinate'] for r in ledger}=={f'io.get-coursier:{m}:{version}' for m in expected}
files=0
for r in ledger:
    assert r['publishInvocations']==1 and r['publishExitCode']==0
    check=r['immediateAbsenceCheck']
    assert len(check['checks'])==4 and all(c['status']==404 for c in check['checks'])
    assert check['metadataHasVersion'] is False
    assert len(r['files'])==3
    module=r['coordinate'].split(':')[1]
    assert {f['file'] for f in r['files']}=={f['file'] for f in manifest if f['coordinate']==r['coordinate']}
    for f in r['files']:
        local=a.work_dir/'artifacts'/module/f['file']
        downloaded=a.work_dir/'downloaded'/module/f['file']
        original=next(m for m in manifest if m['coordinate']==r['coordinate'] and m['file']==f['file'])
        sha=hashlib.sha256(local.read_bytes()).hexdigest()
        assert sha==hashlib.sha256(downloaded.read_bytes()).hexdigest()==original['sha256']==f['localSha256']==f['downloadedSha256']
        assert len(downloaded.read_bytes())==original['size']==f['size'] and f['match'] is True
        files+=1
proof=json.loads((a.evidence/'class-proof.json').read_text())
assert proof['publishedVerified'] is True and proof['classCount']==len(proof['classes']) and proof['classCount']>179
assert all(c['compiledSha256']==c['stagedSha256']==c['downloadedSha256'] for c in proof['classes'])
assert len(proof['requiredChangedClasses'])==4 and len(proof['newClasses'])==2
assert all(c['compiledSha256']!=c['buffer5Sha256'] for c in proof['changedClasses'])
assert proof['source']=='119e39d993a0e5f96a489c9e9eddcd0e0acf9a4a'
result={'passed':True,'version':version,'modules':7,'publisherInvocations':7,'publisherRetries':0,
        'filesVerified':files,'filesMismatched':0,'immediate404Checks':28,
        'metadataAbsenceChecks':7,'compiledCacheClassMatches':proof['classCount'],
        'existingClassDifferencesFromBuffer5':sum(c['buffer5Sha256'] is not None for c in proof['changedClasses']),
        'newEncodingClasses':sum(c['buffer5Sha256'] is None for c in proof['changedClasses'])}
(a.evidence/'independent-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print('Independent audit passed: 7 uploads, 21 file matches,',proof['classCount'],'compiled/downloaded class matches.')

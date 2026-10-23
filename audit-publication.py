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
version='2.1.31-buffer5'
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
assert proof['publishedVerified'] is True and proof['classCount']==179==len(proof['classes'])
assert all(c['compiledSha256']==c['stagedSha256']==c['downloadedSha256'] for c in proof['classes'])
assert sum(c.get('different',False) for c in proof['changedClasses'])==4
assert sum(c.get('added',False) for c in proof['changedClasses'])==2
result={'passed':True,'version':version,'modules':7,'publisherInvocations':7,'publisherRetries':0,
        'filesVerified':files,'filesMismatched':0,'immediate404Checks':28,
        'metadataAbsenceChecks':7,'compiledCacheClassMatches':179,
        'existingClassDifferencesFromBuffer4':4,'newFramingClasses':2}
(a.evidence/'independent-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print('Independent audit passed: 7 uploads, 21 file matches, 179 compiled/downloaded class matches.')

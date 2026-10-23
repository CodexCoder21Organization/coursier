"""Compare compiled cache classes, staged JARs, downloaded JARs and buffer4."""
import argparse
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

p = argparse.ArgumentParser()
p.add_argument('--repo', type=Path, required=True)
p.add_argument('--work-dir', type=Path, required=True)
p.add_argument('--published', action='store_true')
a = p.parse_args()
version = '2.1.31-buffer5'
module = 'coursier-cache_2.13'
source = '3788263afb4b8977cfd5ebc2a557de566e0c801c'

def sha(data):
    return hashlib.sha256(data).hexdigest()

assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=a.repo,text=True).strip() == source
local = a.work_dir/'artifacts'/module/f'{module}-{version}.jar'
compiled = a.repo/'out/cache/jvm/2.13.16/compile.dest/classes'
rows = []
with zipfile.ZipFile(local) as staged:
    for name in sorted(staged.namelist()):
        if name.endswith('.class'):
            raw = (compiled/name).read_bytes()
            digest = sha(staged.read(name))
            assert sha(raw) == digest, name
            rows.append({'class':name,'compiledSha256':sha(raw),'stagedSha256':digest})
    names = ['coursier/cache/internal/Downloader.class',
             'coursier/cache/internal/Downloader$.class',
             'coursier/cache/CacheUrl.class','coursier/cache/CacheUrl$.class']
    new_names = ['coursier/cache/internal/Downloader$ResponseInputStream.class',
                 'coursier/cache/internal/Downloader$DecodingInputStream.class']
    with zipfile.ZipFile(a.work_dir/'buffer4-cache.jar') as prior:
        differences = []
        for name in names:
            old = sha(prior.read(name))
            new = sha(staged.read(name))
            assert old != new, name
            differences.append({'class':name,'buffer4Sha256':old,'buffer5Sha256':new,'different':True})
        for name in new_names:
            assert name not in prior.namelist(), name
            assert name in staged.namelist(), name
            differences.append({'class':name,'buffer4Sha256':None,'buffer5Sha256':sha(staged.read(name)), 'added':True})
    markers = ['Incomplete response from ', 'Overlong response from ', 'Invalid response framing from ']
    response = staged.read(new_names[0])
    for marker in markers:
        assert marker.encode() in response, marker
    source_rows = []
    with zipfile.ZipFile(local.with_name(f'{module}-{version}-sources.jar')) as sources:
        for relative in ['coursier/cache/internal/Downloader.scala','coursier/cache/CacheUrl.scala']:
            src = 'modules/cache/jvm/src/main/scala/'+relative
            raw = subprocess.check_output(['git','show',source+':'+src],cwd=a.repo)
            assert raw == sources.read(relative), relative
            source_rows.append({'path':src,'gitSourceSha256':sha(raw),'sourceJarSha256':sha(sources.read(relative))})
if a.published:
    with zipfile.ZipFile(a.work_dir/'downloaded'/module/local.name) as downloaded:
        for row in rows:
            digest = sha(downloaded.read(row['class']))
            assert digest == row['compiledSha256'], row['class']
            row['downloadedSha256'] = digest
result = {'source':source,'version':version,'publishedVerified':a.published,
          'classCount':len(rows),'classes':rows,'changedClasses':differences,
          'responseErrorMarkers':markers,'sources':source_rows,
          'buffer4JarSha256':sha((a.work_dir/'buffer4-cache.jar').read_bytes())}
output = 'class-proof.json' if a.published else 'class-proof-before-publish.json'
(a.work_dir/output).write_text(json.dumps(result,indent=2)+'\n')
print(f'Verified {len(rows)} compiled cache classes; four existing classes differ from buffer4, two framing classes are new; published={a.published}.')

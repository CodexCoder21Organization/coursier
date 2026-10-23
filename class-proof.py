"""Verify compiled cache classes and the encoding changes in the release JAR."""
import argparse,hashlib,json,subprocess,zipfile
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--repo',required=True,type=Path)
p.add_argument('--work-dir',required=True,type=Path)
p.add_argument('--published',action='store_true')
a=p.parse_args()
source='119e39d993a0e5f96a489c9e9eddcd0e0acf9a4a'
version='2.1.31-buffer6'; module='coursier-cache_2.13'
def sha(data): return hashlib.sha256(data).hexdigest()
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=a.repo,text=True).strip()==source
assert not subprocess.check_output(['git','status','--porcelain'],cwd=a.repo,text=True).strip()
jar=a.work_dir/'artifacts'/module/f'{module}-{version}.jar'
compiled=a.repo/'out/cache/jvm/2.13.16/compile.dest/classes'
rows=[]; changed=[]; sources=[]
with zipfile.ZipFile(jar) as local, zipfile.ZipFile(a.work_dir/'buffer5-cache.jar') as old:
    for name in sorted(n for n in local.namelist() if n.endswith('.class')):
        digest=sha(local.read(name)); raw=sha((compiled/name).read_bytes())
        assert digest==raw,name
        previous=sha(old.read(name)) if name in old.namelist() else None
        row=dict(classFile=name,compiledSha256=raw,stagedSha256=digest,buffer5Sha256=previous)
        rows.append(row)
        if previous!=digest: changed.append(row.copy())
    required_changed=['coursier/cache/internal/Downloader.class','coursier/cache/internal/Downloader$.class','coursier/cache/internal/Downloader$ResponseInputStream.class']
    required_new=['coursier/cache/internal/GzipInputStream.class','coursier/cache/internal/Downloader$DeflateInputStream.class']
    for name in required_changed:
        assert name in old.namelist() and sha(old.read(name))!=sha(local.read(name)),name
    for name in required_new: assert name not in old.namelist() and name in local.namelist(),name
    marker_map={
        'coursier/cache/internal/GzipInputStream.class':['Corrupt GZIP header','Corrupt GZIP trailer','Unsupported GZIP flags:','java/util/zip/CRC32','getBytesWritten','java/io/PushbackInputStream'],
        'coursier/cache/internal/Downloader$DeflateInputStream.class':['Deflate dictionary required: expected a decoded body without a preset dictionary','Unexpected trailing deflate data: expected end of encoded layer, received additional bytes','getRemaining'],
        'coursier/cache/internal/Downloader.class':['coursier/cache/internal/GzipInputStream','coursier/cache/internal/Downloader$DeflateInputStream'],
        'coursier/cache/internal/Downloader$.class':['Content-Encoding']}
    for name,markers in marker_map.items():
        for marker in markers: assert marker.encode() in local.read(name),(name,marker)
    with zipfile.ZipFile(jar.with_name(f'{module}-{version}-sources.jar')) as src:
        for name in ['coursier/cache/internal/Downloader.scala','coursier/cache/internal/GzipInputStream.scala']:
            path='modules/cache/jvm/src/main/scala/'+name
            data=subprocess.check_output(['git','show',source+':'+path],cwd=a.repo)
            assert data==src.read(name),name
            sources.append(dict(path=path,gitSha256=sha(data),sourceJarSha256=sha(src.read(name))))
if a.published:
    with zipfile.ZipFile(a.work_dir/'downloaded'/module/jar.name) as remote:
        assert set(n for n in remote.namelist() if n.endswith('.class'))=={row['classFile'] for row in rows}
        for row in rows:
            row['downloadedSha256']=sha(remote.read(row['classFile']))
            assert row['downloadedSha256']==row['compiledSha256'],row['classFile']
result=dict(source=source,version=version,publishedVerified=a.published,classCount=len(rows),classes=rows,changedClasses=changed,requiredChangedClasses=required_changed,newClasses=required_new,encodingClassMarkers=marker_map,sources=sources,buffer5JarSha256=sha((a.work_dir/'buffer5-cache.jar').read_bytes()))
output='class-proof.json' if a.published else 'class-proof-before-publish.json'
Path(__file__).resolve().with_name(output).write_text(json.dumps(result,indent=2)+'\n')
print(f'Class proof passed: {len(rows)} compiled cache classes; {len(changed)} differ from buffer5 (including new classes); published={a.published}.')

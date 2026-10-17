from pathlib import Path
import subprocess, hashlib, json, xml.etree.ElementTree as E
candidate='d5eec8d10bf59305b4263bb96e741e45408c9f85'
baseline='5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3'
root=Path('.lane-tools/final-gate/artifacts')
expected={**{f'ordinary{i}':4 for i in range(1,4)},**{f'property{i}':4 for i in range(1,4)},'multiplier':1,'config':4,'neighbors':6,'mutation':1}
filecache='modules/cache/jvm/src/main/scala/coursier/cache/FileCache.scala'
summary=[]
for name,count in expected.items():
 p=root/f'coursierfetch4-gate-{name}'
 assert p.is_dir(), f'{name}: artifact absent'
 proof=(p/'source-proof.txt').read_text(); assert proof.splitlines()[0]==candidate, (name,proof)
 hashes={}
 for row in (p/'file-hashes.txt').read_text().splitlines():
  h,path=row.split(None,1); hashes[path]=h
 assert len(hashes)==6,(name,len(hashes))
 for path,h in hashes.items():
  revision=baseline if name=='mutation' and path==filecache else candidate
  data=subprocess.check_output(['git','show',f'{revision}:{path}'])
  assert h==hashlib.sha256(data).hexdigest(),(name,path)
 xmls=list(p.rglob('test-report.xml')); assert len(xmls)==1,(name,xmls)
 r=E.parse(xmls[0]).getroot(); rows=r.findall('.//testcase'); assert len(rows)==count,(name,len(rows),count)
 histories=[x.tag for x in r.iter() if 'attempt' in x.tag.lower() or 'history' in x.tag.lower()]
 assert not histories,(name,histories)
 skipped=[x for x in rows if x.find('skipped') is not None]
 assert not skipped,(name,'skipped')
 bad=[x for x in rows if x.find('failure') is not None or x.find('error') is not None]
 rc=int((p/'exit-code.txt').read_text())
 details=[]
 if name=='mutation':
  assert len(bad)==1 and rc==1,(name,len(bad),rc)
  f=bad[0].find('failure')
  if f is None: f=bad[0].find('error')
  assert f.get('type')=='utest.AssertionError', f.attrib
  assert 'assert(waiterEntered.await(30, TimeUnit.SECONDS))' in f.get('message',''), f.attrib
  details=[{'test':bad[0].get('name'), 'failure':f.attrib}]
 else:
  assert not bad and rc==0,(name,[x.attrib for x in bad],rc)
 invocation=(p/'invocation.patch').read_text()
 if name.startswith('ordinary') or name=='neighbors': assert not invocation, name
 elif name.startswith('property') or name=='mutation': assert '-Dcoursier.exception-retry-backoff-initial-delay=1m' in invocation,name
 elif name=='multiplier':
  for v in ['-Dcoursier.exception-retry=0','-Dcoursier.exception-retry-backoff-initial-delay=1ms','-Dcoursier.exception-retry-backoff-multiplier=10000000']: assert v in invocation,(name,v)
 elif name=='config': assert '-Dscala-cli.config=' in invocation,name
 summary.append({'job':name, 'rows':len(rows),'passed':len(rows)-len(bad),'failed':len(bad),'priorAttempts':len(histories),'skipped':len(skipped),'exit':rc,'details':details,'hashes':'all six match expected source','tests':[{'name':x.get('name'),'duration':x.get('time')} for x in rows]})
assert sum(x['passed'] for x in summary if x['job']!='mutation')==35
Path('.lane-tools/final-gate/audit.json').write_text(json.dumps(summary,indent=2))
for x in summary: print(x['job'],f"{x['passed']}/{x['rows']}",'first attempts, exit',x['exit'])
print('POSITIVE 35/35 FIRST ATTEMPTS; MUTATION 1/1 EXPECTED ASSERTION FAILURE')

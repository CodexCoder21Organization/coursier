from pathlib import Path
import xml.etree.ElementTree as E
import subprocess,hashlib,json
head='d5eec8d10bf59305b4263bb96e741e45408c9f85'
base='5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3'
summary=[]
for p in sorted(Path('.review-evidence/final-gate').iterdir()):
 name=p.name.removeprefix('coursierfetch4-gate-')
 proof=(p/'source-proof.txt').read_text().splitlines()[0]
 assert proof==head,(name,proof)
 hashes=(p/'file-hashes.txt').read_text().splitlines()
 assert len(hashes)==6
 for line in hashes:
  checksum,path=line.split(None,1)
  rev=base if name=='mutation' and path.endswith('/FileCache.scala') else head
  data=subprocess.check_output(['git','show',rev+':'+path])
  assert hashlib.sha256(data).hexdigest()==checksum,(name,path)
 reports=list(p.rglob('*.xml'));assert len(reports)==1
 root=E.parse(reports[0]).getroot()
 rows=root.findall('.//testcase')
 assert rows
 earlier=[x.tag for x in root.iter() if 'attempt' in x.tag.lower() or 'history' in x.tag.lower()]
 skipped=[x for x in rows if x.find('skipped') is not None]
 bad=[x for x in rows if x.find('failure') is not None or x.find('error') is not None]
 assert not earlier and not skipped
 rc=int((p/'exit-code.txt').read_text())
 expected=1 if name in ('mutation','multiplier') else 6 if name=='neighbors' else 4
 assert len(rows)==expected,(name,len(rows))
 if name=='mutation':
  assert rc==1 and len(bad)==1
  fail=bad[0].find('failure')
  if fail is None:fail=bad[0].find('error')
  assert fail.get('type')=='utest.AssertionError'
  assert 'assert(waiterEntered.await(30, TimeUnit.SECONDS))' in fail.get('message','')
  details={'type':fail.get('type'),'assertion':'assert(waiterEntered.await(30, TimeUnit.SECONDS))'}
 else:
  assert rc==0 and not bad
  details={}
 patch=(p/'invocation.patch').read_text()
 if name.startswith('ordinary') or name=='neighbors':assert not patch
 else:assert 'mill-build/src/coursierbuild/modules/CacheJvmBase.scala' in patch
 summary.append({'job':name,'rows':len(rows),'pass':len(rows)-len(bad),'fail':len(bad),'exit':rc,'history':len(earlier),'skipped':len(skipped),'six_hashes':'match expected revisions','details':details})
assert sum(x['pass'] for x in summary)==35
Path('.review-evidence/native-audit.json').write_text(json.dumps(summary,indent=2))
for x in summary: print(x['job'],f"{x['pass']}/{x['rows']}", 'exit',x['exit'],'history',x['history'])

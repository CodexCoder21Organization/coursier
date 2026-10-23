"""Audit saved remote results against the freshly fetched product head; does not run tests."""
from pathlib import Path
from collections import Counter
import hashlib,json,re,subprocess,xml.etree.ElementTree as ET
out=Path(__file__).resolve().parent
repo=out.parent/'coursier'
def git(*args):
 return subprocess.check_output(['git',*args],cwd=repo,text=True).strip()
head=git('rev-parse','HEAD')
assert head=='ac74fa03ab018e66a67f293dbae7a4fe3a7bd367'
assert git('merge-base','HEAD','origin/main')==git('rev-parse','origin/main')
for saved,name in [('final-DownloadResponseFramingTests.scala','DownloadResponseFramingTests.scala'),('final-DownloadBufferCapacityTests.scala','DownloadBufferCapacityTests.scala')]:
 assert (out/saved).read_bytes()==(repo/'modules/cache/jvm/src/test/scala/coursier/cache'/name).read_bytes()
subprocess.run(['git','apply','--reverse','--check',str(out/'product-final.patch')],cwd=repo,check=True,capture_output=True)
required=[
 'valid mixed case range unit completes the artifact',
 'chunked range must consume the closing chunk before publishing',
 'chunked range rejects data beyond its stated range',
 'unsolicited 304 cannot publish an empty artifact',
 'cut off chunked response removes temporary bytes',
 'cut off gzip response removes temporary bytes',
 'short other encoded response cannot publish partial bytes',
 'correct mixed case gzip decodes the body']
before=ET.parse(out/'before-framing.xml').getroot().findall('.//testcase')
failed={c.attrib['name'] for c in before if c.find('failure') is not None}
assert set(required)<=failed
expanded=ET.parse(out/'before-expanded.xml').getroot().findall('.//testcase')
assert any(c.attrib['name']=='deflate content encoding retains the default maximum' and c.find('failure') is not None for c in expanded)
counts=Counter()
rounds=json.loads((out/'thirty-runs-audit.json').read_text())
for i,record in enumerate(rounds,1):
 assert record['head']==head and record['exit']==0 and record['retries']==0 and record['cases']==95
 cases=ET.parse(out/f'repeat-{i:02d}.xml').getroot().findall('.//testcase')
 keys=[(c.attrib['classname'],c.attrib['name']) for c in cases]
 assert len(keys)==len(set(keys))==95
 assert all(c.find(n) is None for c in cases for n in ['failure','error','skipped'])
 counts.update(keys)
 assert set(required)<={c.attrib['name'] for c in cases}
assert len(rounds)==30 and len(counts)==95 and set(counts.values())=={30}
module=ET.parse(out/'cache-module.xml').getroot().findall('.//testcase')
assert len(module)==len({(c.attrib['classname'],c.attrib['name']) for c in module})==187
assert all(c.find(n) is None for c in module for n in ['failure','error','skipped'])
assert json.loads((out/'cache-module-result.json').read_text())['exit']==0
module_audit=json.loads((out/'cache-module-attempt-audit.json').read_text())
assert module_audit['head']==head and module_audit['qualifiedEvents']==187 and module_audit['retries']==0
sources=list((repo/'modules/cache/jvm/src/test').rglob('*.scala'))
assert not any(re.search(r'\butestWrap\b|TestSuite\.Retries|\butestRetryCount\b',p.read_text()) for p in sources)
report={'productHead':head,'main':git('rev-parse','origin/main'),'testRunsRepeatedOnResume':False,
 'baselineRequiredFailures':required,'before':{'cases':len(before),'failed':len(failed),'firstAttempts':len(before),'retries':0},
 'finalRepeated':{'rounds':30,'casesPerRound':95,'firstAttempts':2850,'retries':0,'failures':0},
 'cacheModule':{'passed':187,'cases':187,'firstAttempts':187,'retries':0},
 'evidenceTestSourcesMatchProduct':True,'productPatchReverseCheck':True,
 'sourceFilesWithoutRetryOverrides':len(sources),
 'sourceHashes':{str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [repo/'modules/cache/jvm/src/main/scala/coursier/cache/CacheUrl.scala',repo/'modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala']+list(repo.joinpath('modules/cache/jvm/src/test/scala/coursier/cache').glob('Download*Tests.scala'))}}
(out/'resumed-evidence-audit.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k not in ['sourceHashes','baselineRequiredFailures']}))

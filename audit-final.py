from pathlib import Path
import xml.etree.ElementTree as ET
import hashlib,json
p=Path(__file__).resolve().parent
expected={'head-existing':(95,95,0),'head-review':(7,1,6),'main-comparison':(102,35,67),'main-decoder-ends':(3,0,3),'head-decoder-ends':(3,1,2)}
register={}
for phase,(total,passes,fails) in expected.items():
 cases=ET.parse(p/(phase+'.xml')).findall('.//testcase')
 assert len(cases)==total
 assert len({(c.get('classname'),c.get('name')) for c in cases})==total
 assert sum(c.find('failure') is None and c.find('error') is None for c in cases)==passes
 assert sum(c.find('failure') is not None for c in cases)==fails
 assert all(c.find('error') is None and c.find('skipped') is None for c in cases)
 for c in cases:
  group=c.get('classname').replace('Rcours3BufferReviewTests','DownloadBufferCapacityTests')
  label='head' if phase.startswith('head-') else 'main'
  name=group+'.'+c.get('name')
  row=register.setdefault(name,{})
  assert label not in row, (name,label,'duplicate test attempt')
  row[label]='FAIL' if c.find('failure') is not None else 'PASS'
 assert not json.loads((p/(phase+'-result.json')).read_text())['deadlineReached']
assert len(register)==105
assert all(set(value)=={'main','head'} for value in register.values())
for f in ['DownloadLengthIntegrityTests.scala','DownloadResponseFramingTests.scala','DownloadBufferCapacityTests.scala','Rcours3ReviewTests.scala','Rcours3DecoderEndTests.scala']:
 assert 'utestWrap' not in (p/f).read_text()
 assert 'retry' not in (p/f).read_text().lower() or '.withRetry(0)' in (p/f).read_text()
assert 'runBody\n' in (p/'utest-0.9.1-Executor.scala').read_text()
result={'matchedCases':105,'head':{'pass':97,'fail':8,'firstAttempts':105,'retries':0},'main':{'pass':35,'fail':70,'firstAttempts':105,'retries':0},'candidateTests':{key:value for key,value in register.items() if '.Rcours3' in key},'existingTestsEditedByReview':0,'readingHashes':{name:hashlib.sha256((p/name).read_bytes()).hexdigest() for name in ['PHILOSOPHY.md','TESTING.md'] if (p/name).exists()}}
(p/'final-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='candidateTests'}))

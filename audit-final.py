from pathlib import Path
import json,re,xml.etree.ElementTree as ET
from collections import Counter
out=Path(__file__).resolve().parent
rounds=json.loads((out/'thirty-runs-audit.json').read_text())
counts=Counter();events=[];summaries=[]
for i in range(1,31):
 cases=ET.parse(out/f'repeat-{i:02d}.xml').getroot().findall('.//testcase')
 keys=[c.attrib['classname']+'.'+c.attrib['name'] for c in cases]
 assert len(keys)==95 and len(set(keys))==95
 assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
 counts.update(keys)
 t=re.sub(r'\x1b\[[0-9;]*m','',(out/f'repeat-{i:02d}.log').read_text())
 lines=[line for line in t.splitlines() if re.search(r'\[\d+\]\s+\+ ',line)]
 events.append(sum('coursier.cache.' in line for line in lines))
 summaries.append(sum('coursier.cache.' not in line for line in lines))
assert len(rounds)==30 and set(counts.values())=={30} and set(events)=={95} and set(summaries)=={95}
audit={'rounds':30,'casesPerRound':95,'firstAttempts':2850,'retries':0,'failures':0,'errors':0,'skips':0,
 'heads':sorted(set(r['head'] for r in rounds)),'caseRuns':dict(counts),
 'qualifiedPassingEvents':events,'summaryRenderings':summaries,
 'basis':'Plain utest 0.9.1 TestSuite/Executor; no retry mixins or utestWrap overrides in cache module. One XML case and one qualified event per scenario; MasterRunner.done adds separate summary renderings.'}
assert audit['heads']==['ac74fa03ab018e66a67f293dbae7a4fe3a7bd367']
p=out/'cache-module.xml'
if p.exists():
 cases=ET.parse(p).getroot().findall('.//testcase')
 failures=[c.attrib['classname']+'.'+c.attrib['name'] for c in cases if c.find('failure') is not None or c.find('error') is not None or c.find('skipped') is not None]
 keys=[c.attrib['classname']+'.'+c.attrib['name'] for c in cases]
 audit['cacheModule']={'cases':len(cases),'uniqueCases':len(set(keys)),'firstAttempts':len(cases),'retries':0,'failedOrSkipped':failures}
(out/'final-attempt-audit.json').write_text(json.dumps(audit,indent=2)+'\n')
print(json.dumps({k:v for k,v in audit.items() if k not in ['caseRuns','qualifiedPassingEvents','summaryRenderings','basis']}))

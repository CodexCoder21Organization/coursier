from pathlib import Path
import json,re,sys,xml.etree.ElementTree as ET
root=Path(sys.argv[1]);phase=sys.argv[2];expected=int(sys.argv[3])
p=Path(__file__).resolve().parent
task='test' if phase=='cache' else 'testOnly'
reports=list((root/('out/cache/jvm/2.13.16/test/'+task+'.dest')).rglob('*.xml'))
assert len(reports)==1,reports
output=p/(phase+'.xml');output.write_bytes(reports[0].read_bytes())
cases=ET.parse(output).findall('.//testcase')
keys=[(c.get('classname'),c.get('name')) for c in cases]
if expected == 0: expected=len(cases)
assert len(cases)==expected and len(set(keys))==expected,(len(cases),keys)
log=re.sub(r"\x1b\[[0-9;]*m",'',(p/(phase+'.log')).read_text())
events=re.findall(r"^\[\d+\] [X+] (coursier\..*)",log,re.M)
assert len(events)==expected,(len(events),expected)
rows=[]
for case,(suite,name) in zip(cases,keys):
 full=suite+'.'+name
 occurrences=sum(e.startswith(full+' ') for e in events)
 assert occurrences==1,(full,occurrences)
 assert case.find('error') is None and case.find('skipped') is None,full
 rows.append({'suite':suite,'name':name,'result':'FAIL' if case.find('failure') is not None else 'PASS','attempts':occurrences,'seconds':case.get('time')})
result=json.loads((p/(phase+'-result.json')).read_text())
assert not result['deadlineReached'],result
fails=sum(r['result']=='FAIL' for r in rows)
assert result['exit']==(1 if fails else 0),result
java=json.loads((p/(phase+'-java-count.json')).read_text())
assert java['maxObservedJavaProcesses']<=2,java
summary={'phase':phase,'cases':expected,'pass':expected-fails,'fail':fails,'errors':0,'skips':0,'firstAttempts':expected,'qualifiedEvents':len(events),'retries':0,'maxObservedJavaProcesses':java['maxObservedJavaProcesses'],'rows':rows}
(p/(phase+'-audit.json')).write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:v for k,v in summary.items() if k!='rows'}))

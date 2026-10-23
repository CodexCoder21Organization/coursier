from pathlib import Path
import json,subprocess,hashlib
p=Path(__file__).resolve().parent
root=Path(__import__('sys').argv[1])
phases={name:json.loads((p/(name+'-audit.json')).read_text()) for name in ['head-review','head-blank','head-a1','main-comparison']}
for v in phases.values():
 assert v['firstAttempts']==v['cases']==v['qualifiedEvents']
 assert v['retries']==v['errors']==v['skips']==0
 assert all(r['attempts']==1 for r in v['rows'])
head={r['suite']+'.'+r['name']:r['result'] for phase in ['head-review','head-blank'] for r in phases[phase]['rows']}
main={r['suite']+'.'+r['name']:r['result'] for r in phases['main-comparison']['rows']}
assert set(head)==set(main) and len(head)==26
introduced=[name for name in head if head[name]=='FAIL' and main[name]=='PASS']
inheritedCases=[name for name in head if head[name]==main[name]=='FAIL']
assert introduced==[]
assert len(inheritedCases)==8
assert phases['head-review']['pass']==4 and phases['head-review']['fail']==6
assert phases['head-blank']['pass']==14 and phases['head-blank']['fail']==2
assert phases['head-a1']['pass']==8
for name in ['empty content encoding means an unencoded body','empty list elements do not change identity encoding']:
 key='coursier.cache.Rcours3ReviewTests.'+name
 assert head[key]==main[key]=='PASS'
for name in ['Rcours3ReviewTests.scala','Rcours3DecoderEndTests.scala']:
 old=subprocess.check_output(['git','show','origin/wip/RCOURS3-evidence-20261023:'+name],cwd=root)
 assert old==(p/name).read_bytes()==(root/'modules/cache/jvm/src/test/scala/coursier/cache'/name).read_bytes()
assert subprocess.check_output(['git','diff','origin/main','HEAD','--','modules/cache/jvm/src/main'],cwd=root)==b''
static=json.loads((p/'static-audit.json').read_text())
assert len(static['modifiedMainTestFiles'])==1
assert not any(static['addedLineScan'].values())
summary={'comparedCases':26,'introducedDefectCases':introduced,'inheritedDefectCases':inheritedCases,'inheritedDefectGroups':4,'verdicts':{'A':'APPROVE','introduced':0,'inherited':4,'B':'GAPS 4','C':'FAIL: one documented main-test contract edit'},'gateCounts':{k:{key:value for key,value in v.items() if key!='rows'} for k,v in phases.items()},'totalFirstAttempts':sum(v['cases'] for v in phases.values()),'totalRetries':0,'totalErrors':0,'totalSkips':0,'reviewEditsToExistingTests':0,'recorderQualification':'First head phase used recovered recorder with incomplete later-child traversal; structural direct Mill bound was two JVMs. Later phases use corrected recorder and observe at most two.'}
(p/'final-audit.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:v for k,v in summary.items() if k not in ['gateCounts','inheritedDefectCases']}))

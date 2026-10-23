from pathlib import Path
import json,re,subprocess,xml.etree.ElementTree as ET
out=Path(__file__).resolve().parent
root=Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/FCOURS4-coursier-20261020')
cases=ET.parse(out/'cache-module.xml').getroot().findall('.//testcase')
keys=[c.attrib['classname']+'.'+c.attrib['name'] for c in cases]
failed=[k for c,k in zip(cases,keys) if c.find('failure') is not None or c.find('error') is not None or c.find('skipped') is not None]
t=re.sub(r'\x1b\[[0-9;]*m','',(out/'cache-module.log').read_text())
lines=[line for line in t.splitlines() if re.search(r'\[\d+\]\s+\+ ',line)]
qualified=sum(bool(re.search(r'\+ coursier\.',line)) for line in lines)
report={'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
 'cases':len(cases),'uniqueCases':len(set(keys)),'firstAttempts':len(cases),'retries':0,
 'failedOrSkipped':failed,'qualifiedEvents':qualified,'summaryRenderings':len(lines)-qualified,
 'runnerSourceAudit':'cache-runner-source-audit.json',
 'basis':'Pinned plain utest executes once. Qualified events cover coursier.cache plus other cache-module packages; MasterRunner repeats result summaries.'}
assert len(cases)==187 and len(set(keys))==187 and not failed
assert qualified==187 and len(lines)-qualified==187
(out/'cache-module-attempt-audit.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))

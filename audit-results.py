from pathlib import Path
import json, shutil, sys, xml.etree.ElementTree as E
root=Path(sys.argv[1]);phase=sys.argv[2]
xml=root/'out/cache/jvm/2.13.16/test/testOnly.dest/test-report.xml'
shutil.copyfile(xml,phase+'.xml')
rows=[]
for c in E.parse(xml).iter('testcase'):
    rows.append({'class':c.attrib.get('classname'),'name':c.attrib.get('name'),
      'failed':c.find('failure') is not None,'skipped':c.find('skipped') is not None,
      'seconds':c.attrib.get('time')})
assert len({(r['class'],r['name']) for r in rows})==len(rows), 'Duplicate test names: inspect attempts'
assert not any(r['skipped'] for r in rows), 'Unexpected skipped test'
Path(phase+'-cases.json').write_text(json.dumps(rows,indent=2)+'\n')
summary={}
for cls in sorted({r['class'] for r in rows}):
    cases=[r for r in rows if r['class']==cls]
    summary[cls]={'passed':sum(not r['failed'] for r in cases),'failed':sum(r['failed'] for r in cases),
      'attempts':len(cases),'uniqueCases':len(cases)}
Path(phase+'-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary))
print('Failed names: '+', '.join(r['name'] for r in rows if r['failed']))

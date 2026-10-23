"""Run thirty separate local integrity invocations and audit every first attempt."""
from pathlib import Path
import collections
import hashlib
import json
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1]).resolve()
evidence = Path(__file__).resolve().parent
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
source = root / 'modules/cache/jvm/src/test/scala/coursier/cache/DownloadLengthIntegrityTests.scala'
source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
assert 'extends TestSuite' in source.read_text()
assert 'utestWrap' not in source.read_text()
assert 'Retries' not in source.read_text()
results = []
counts = collections.Counter()
expected_names = None
for iteration in range(1, 31):
    phase = f'integrity-30-{iteration:02d}'
    subprocess.run([
        sys.executable, str(evidence / 'run-command.py'), str(root), phase,
        './mill', '--no-server', '-j2', 'cache.jvm[2.13.16].test.testOnly',
        'coursier.cache.DownloadLengthIntegrityTests',
    ], check=True)
    result = json.loads((evidence / (phase + '-result.json')).read_text())
    assert result['exit'] == 0 and not result['deadlineReached'], result
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == head
    assert hashlib.sha256(source.read_bytes()).hexdigest() == source_hash
    xml_path = evidence / (phase + '.xml')
    shutil.copyfile(root / 'out/cache/jvm/2.13.16/test/testOnly.dest/test-report.xml', xml_path)
    cases = list(ET.parse(xml_path).iter('testcase'))
    names = [(case.get('classname'), case.get('name')) for case in cases]
    assert len(cases) == 42 and len(set(names)) == 42, names
    assert all(case.find('failure') is None and case.find('error') is None and
               case.find('skipped') is None for case in cases)
    if expected_names is None:
        expected_names = set(names)
    assert set(names) == expected_names
    log = re.sub(r'\x1b\[[0-9;]*m', '', (evidence / (phase + '.log')).read_text())
    for _, name in names:
        assert log.count('coursier.cache.DownloadLengthIntegrityTests.' + name + ' ') == 1, name
    counts.update(names)
    results.append({'iteration': iteration, 'passed': len(cases), 'attempts': len(cases),
                    'retries': 0, 'head': head, 'seconds': result['seconds']})
    summary = {'head': head, 'sourceSha256': source_hash, 'completedRuns': len(results),
               'passed': sum(counts.values()), 'retries': 0, 'runs': results,
               'perCase': [{'class': cls, 'name': name, 'passed': n, 'attempts': n}
                           for (cls, name), n in sorted(counts.items())]}
    (evidence / 'integrity-30-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(f'Completed run {iteration}/30: {len(cases)}/{len(cases)}, single attempts', flush=True)
assert set(counts.values()) == {30}

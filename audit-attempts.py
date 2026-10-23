"""Audit one utest terminal execution per unique XML case, without retry wrappers."""
from pathlib import Path
import collections
import json
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent
summary = {}
for phase in ('head-original', 'head-boundaries', 'main-comparison', 'head-chunked-range', 'main-chunked-range'):
    cases = list(ET.parse(root / (phase + '.xml')).iter('testcase'))
    log = re.sub(r'\x1b\[[0-9;]*m', '', (root / (phase + '.log')).read_text())
    keys = [(case.get('classname'), case.get('name')) for case in cases]
    assert len(keys) == len(set(keys)), keys
    terminal_counts = {}
    for cls, name in keys:
        lines = [line for line in log.splitlines() if re.search(r'\[\d+\] [+X] ' + re.escape(cls + '.' + name) + r' ', line)]
        # Large failure reports render their already-recorded result twice.
        expected_failure = next(case for case in cases if case.get('classname') == cls and case.get('name') == name).find('failure') is not None
        expected_renderings = 2 if phase == 'main-comparison' and expected_failure else 1
        assert len(lines) == expected_renderings, (phase, cls, name, len(lines))
        assert len(set(lines)) == 1, (phase, cls, name, 'non-identical repeated rendering')
        terminal_counts[cls + '.' + name] = len(lines)
    assert not any(case.find('skipped') is not None for case in cases)
    counts = collections.Counter()
    for case in cases:
        state = 'failed' if case.find('failure') is not None or case.find('error') is not None else 'passed'
        counts[state] += 1
    summary[phase] = dict(cases=len(cases), passed=counts['passed'], failed=counts['failed'], attempts=len(cases), retries=0, terminalLinesPerCase=terminal_counts)
    if 'boundaries' in phase or phase == 'main-comparison':
        observations = [line for line in log.splitlines() if re.match(r'\[\d+\] RCOURS2 url=', line)]
        assert len(observations) == 12, (phase, len(observations))
    if 'chunked-range' in phase:
        observations = [line for line in log.splitlines() if re.match(r'\[\d+\] RCOURS2 url=', line)]
        assert len(observations) == 2, (phase, len(observations))
(root / 'attempt-audit.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps({phase: {key:value for key,value in result.items() if key != 'terminalLinesPerCase'} for phase, result in summary.items()}, indent=2))

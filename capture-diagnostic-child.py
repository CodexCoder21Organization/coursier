"""Capture derived Java frame observations for one explicitly supplied test PID."""
from pathlib import Path
import os, sys, time, json, subprocess, re

root = int(sys.argv[1])
destination = Path(sys.argv[2])

def facts():
    result = {}
    for entry in Path('/proc').iterdir():
        if entry.name.isdigit():
            try:
                fields = (entry / 'stat').read_text().rsplit(')', 1)[1].split()
                result[int(entry.name)] = (int(fields[1]), int(fields[19]), fields[0])
            except (OSError, ValueError, IndexError):
                pass
    return result

initial = facts().get(root)
if initial is None:
    destination.write_text(json.dumps({'rootGoneBeforeObservation': True}) + '\n')
    raise SystemExit(0)
started = time.monotonic()
observations = []
def same_process(actual, expected):
    return actual is not None and actual[1] == expected[1] and actual[2] != 'Z'

for delay in [5, 10, 15, 20, 25]:
    while time.monotonic() - started < delay:
        if not same_process(facts().get(root), initial):
            break
        time.sleep(max(0, min(0.25, delay - (time.monotonic() - started))))
    current = facts()
    if not same_process(current.get(root), initial):
        break
    tree = {root}
    changed = True
    while changed:
        changed = False
        for pid, (parent, start, state) in current.items():
            if parent in tree and pid not in tree:
                tree.add(pid)
                changed = True
    for pid in sorted(tree - {root}):
        try:
            if Path(os.readlink('/proc/' + str(pid) + '/exe')).name != 'java':
                continue
        except OSError:
            continue
        identity = current[pid]
        if not same_process(facts().get(pid), identity):
            continue
        entry = {'offsetSeconds': time.monotonic() - started, 'pid': pid,
                 'parentPid': identity[0], 'startTicks': identity[1]}
        try:
            capture = subprocess.run(['jcmd', str(pid), 'Thread.print', '-l'],
                                     capture_output=True, text=True, timeout=4)
            entry['returncode'] = capture.returncode
            threads = []
            active = None
            for line in capture.stdout.splitlines():
                if line.startswith('"'):
                    name = line.split('"', 2)[1]
                    active = {'name': re.sub(r'[^A-Za-z0-9 _.-]', '', name), 'frames': []}
                    threads.append(active)
                elif active is not None and line.strip().startswith('java.lang.Thread.State:'):
                    active['state'] = line.strip().split(':', 1)[1].strip()
                elif active is not None and line.strip().startswith('at '):
                    active['frames'].append(line.strip()[3:])
            entry['threads'] = [thread for thread in threads if thread['name'] == 'main' or
                                any(re.search(r'coursier|kompile|org.jetbrains.kotlin', frame)
                                    for frame in thread['frames'])]
        except subprocess.TimeoutExpired:
            entry['captureDeadlineReached'] = True
        observations.append(entry)
        destination.write_text(json.dumps({'rootPid': root, 'rootStartTicks': initial[1],
                                          'observations': observations}, indent=2) + '\n')
destination.write_text(json.dumps({'rootPid': root, 'rootStartTicks': initial[1],
                                  'observations': observations, 'terminal': True}, indent=2) + '\n')

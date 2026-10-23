"""Run one local command once; record owned PIDs and a bounded result without command-line dumps."""
from pathlib import Path
import subprocess, time, json, os, signal, sys
from datetime import datetime, timezone
ROOT = Path(sys.argv[1]); PHASE = sys.argv[2]; COMMAND = sys.argv[3:]
OUT = Path(__file__).resolve().parent
owned = {}
max_java = 0
def processes():
    rows = {}
    for entry in Path('/proc').iterdir():
        if entry.name.isdigit():
            try:
                fields = (entry/'stat').read_text().rsplit(')', 1)[1].split()
                rows[int(entry.name)] = (int(fields[1]), fields[19], fields[0], (entry/'stat').read_text().split('(',1)[1].rsplit(')',1)[0])
            except (OSError, ValueError, IndexError): pass
    return rows

def capture(child):
    global max_java
    rows = processes(); parents = {child.pid}
    changed = True
    while changed:
        changed = False
        for pid, (parent, start, state, name) in rows.items():
            if pid not in parents and parent in parents:
                parents.add(pid); changed = True
            if pid in parents:
                owned[pid] = start
    live_java = sum(
        1 for pid, born in owned.items()
        if pid in rows and rows[pid][1] == born and rows[pid][2] != 'Z'
        and rows[pid][3] == 'java'
    )
    max_java = max(max_java, live_java)
    (OUT/(PHASE+'-java-count.json')).write_text(json.dumps({'maxObservedJavaProcesses':max_java})+'\n')
    (OUT/(PHASE+'-owned.json')).write_text(json.dumps(owned, indent=2)+'\n')
    return rows

subprocess.run(['git','fetch','origin'], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
subprocess.run(['git','rebase','origin/main'], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
head = subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
memory_deadline = time.monotonic()+600
while True:
    memory = subprocess.check_output(['free','-m'], text=True)
    if int(memory.splitlines()[1].split()[-1]) >= 4000 or time.monotonic() >= memory_deadline: break
    time.sleep(60)
(OUT/(PHASE+'-memory.txt')).write_text(memory)
start = time.monotonic()
with (OUT/(PHASE+'.log')).open('w') as log:
    child = subprocess.Popen(COMMAND, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    # The requested complete cache-module gate has no independent wall-clock cap.
    while child.poll() is None and (PHASE == "cache" or time.monotonic()-start < 2400):
        capture(child)
        time.sleep(2)
    rows = capture(child)
    if child.poll() is None:
        for pid, born in reversed(list(owned.items())):
            if pid in rows and rows[pid][1] == born and rows[pid][2] != 'Z': os.kill(pid, signal.SIGTERM)
        child.wait(timeout=30)
        result = {'deadlineReached':True,'exit':child.returncode}
    else: result = {'deadlineReached':False,'exit':child.wait()}
result.update(head=head, seconds=time.monotonic()-start, attempts=1, completedAt=datetime.now(timezone.utc).isoformat())
if result['exit'] in (137, -9):
    (OUT/(PHASE+'-memory.events')).write_text(Path('/sys/fs/cgroup/memory.events').read_text())
(OUT/(PHASE+'-result.json')).write_text(json.dumps(result, indent=2)+'\n')
print(PHASE+': '+json.dumps(result), flush=True)

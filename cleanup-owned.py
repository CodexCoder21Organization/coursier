"""Check and stop only recorded PID/start-time pairs; keep process command lines out of evidence."""
from pathlib import Path
import json,os,signal,subprocess,time
out=Path(__file__).resolve().parent
owned={}
for path in out.glob('*-owned.json'):
    entries=json.loads(path.read_text())
    owned.update({int(pid):str(start) for pid,start in entries.items()})
def state(pid):
    try:
        fields=Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split()
        return fields[19],fields[0]
    except (OSError,IndexError): return None

def alive():
    return [pid for pid,start in owned.items() if (s:=state(pid)) and s[0]==start and s[1]!='Z']
stopped=[]
for pid in alive():
    os.kill(pid,signal.SIGTERM);stopped.append(pid)
deadline=time.monotonic()+30
while alive() and time.monotonic()<deadline: time.sleep(.2)
for pid in alive(): os.kill(pid,signal.SIGKILL)
# Run the prescribed process check, but retain only matching numeric IDs and counts.
listing=subprocess.run(['bash','-c','ps -eo pid,args -ww | grep -v grep'],capture_output=True,text=True,check=True)
listed={int(line.split(None,1)[0]) for line in listing.stdout.splitlines()[1:] if line.strip()}
remaining=[pid for pid in alive() if pid in listed]
result={'recordedPidStartPairs':len(owned),'stoppedOwnedPids':stopped,'remainingOwnedLivePids':remaining,'psCheckPerformed':True,'rawProcessListingSaved':False}
(out/'process-cleanup.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
assert not remaining,result

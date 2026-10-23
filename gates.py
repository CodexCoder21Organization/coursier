from pathlib import Path
import subprocess,sys,json,re,xml.etree.ElementTree as ET
ROOT=Path(sys.argv[1]); OUT=Path(__file__).resolve().parent
names=[
 "empty content encoding means an unencoded body",
 "empty list elements do not change identity encoding",
 "empty list elements do not allow unknown encodings",
 "only empty encoding list elements mean an unencoded body",
 "empty list elements preserve compressed decoding",
 "empty encoding list elements preserve length validation",
 "empty encoding list elements preserve saved prefix handling",
 "empty list elements preserve encoded resume rejection"
]
SUITE="coursier.cache.DownloadResponseFramingTests"
query=SUITE+".{"+",".join(names)+"}"
def run(phase,arguments,expected):
 subprocess.run([sys.executable,str(OUT/"run-command.py"),str(ROOT),phase,
  "./mill","--no-server","-j1","cache.jvm[2.13.16].test.testOnly",*arguments],check=True)
 result=json.loads((OUT/(phase+"-result.json")).read_text())
 reports=list((ROOT/"out/cache/jvm/2.13.16/test/testOnly.dest").rglob("*.xml"))
 assert len(reports)==1, reports
 output=OUT/(phase+".xml"); output.write_bytes(reports[0].read_bytes())
 cases=ET.parse(output).findall(".//testcase")
 keys=[(c.get("classname"),c.get("name")) for c in cases]
 assert len(cases)==expected and len(set(keys))==expected, keys
 assert result["exit"]==0 and not result["deadlineReached"], result
 assert all(c.find("failure") is None and c.find("error") is None and c.find("skipped") is None for c in cases)
 log=re.sub(r"\x1b\[[0-9;]*m", "", (OUT/(phase+".log")).read_text())
 # Plain utest emits one result event for each qualified test; no retry mixin.
 events=re.findall(r"^\[\d+\] [X+] coursier\.cache\.",log,re.M)
 assert len(events)==expected, (phase,len(events),expected)
 audit={"phase":phase,"cases":expected,"passes":expected,"qualifiedEvents":len(events),"firstAttempts":expected,"retries":0}
 (OUT/(phase+"-audit.json")).write_text(json.dumps(audit,indent=2)+"\n")
 return keys
for iteration in range(1,31):
 keys=run("after-%02d"%iteration,[SUITE,"--",query],len(names))
 assert set(keys)=={(SUITE,name) for name in names},keys
keys=run("register",["coursier.cache.DownloadLengthIntegrityTests",SUITE,"coursier.cache.DownloadBufferCapacityTests"],103)
prior=ET.parse(OUT/"prior-register.xml").findall(".//testcase")
prior_keys={(c.get("classname"),c.get("name")) for c in prior}
prior_keys.remove((SUITE,"unknown and empty encodings cannot publish response bytes"))
prior_keys.add((SUITE,"unknown encodings cannot publish response bytes"))
assert set(keys)==prior_keys|{(SUITE,name) for name in names}
(OUT/"gates-summary.json").write_text(json.dumps({"newTests":8,"repetitionsEach":30,"repeatPasses":240,"registerPasses":103,"registerTotal":103,"firstAttempts":343,"retries":0},indent=2)+"\n")

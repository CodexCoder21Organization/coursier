from pathlib import Path
import subprocess, json, re, sys, xml.etree.ElementTree as ET
OUT=Path(__file__).resolve().parent; ROOT=Path(sys.argv[1])
def git(*args): return subprocess.check_output(["git",*args],cwd=ROOT,text=True).strip()
SUITE="coursier.cache.DownloadResponseFramingTests"
source="modules/cache/jvm/src/test/scala/coursier/cache/DownloadResponseFramingTests.scala"
base=git("show","ac74fa03:"+source)
current=(ROOT/source).read_text().strip()
start=current.index('    test("empty content encoding means an unencoded body")')
end=current.index('    test("unknown encodings cannot publish response bytes")')
restored=(current[:start]+current[end:]).replace('test("unknown encodings cannot publish response bytes")','test("unknown and empty encodings cannot publish response bytes")').replace('Seq("custom", "br", "gzip, custom")','Seq("custom", "br", "gzip, custom", "")')
assert restored==base,"Unexpected pre-existing test edit"
production="modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala"
old=git("show","ac74fa03:"+production)
new=(ROOT/production).read_text().strip()
assert new.replace("            .filter(_.nonEmpty)\n", "",1)==old
assert set(git("diff","--name-only","ac74fa03").splitlines())=={source,production}
assert not git("status","--porcelain"),"Product worktree must be clean"
assert git("merge-base","HEAD","origin/main")==git("rev-parse","origin/main")
canonical=[]
for phase in ["before","before-boundaries","before-encoded-resume"]:
 cases=ET.parse(OUT/(phase+".xml")).findall(".//testcase")
 if phase=="before-boundaries":
  cases=[c for c in cases if c.get("name")!="empty list elements preserve encoded resume rejection"]
 canonical+=cases
assert len(canonical)==len({c.get("name") for c in canonical})==8
assert sum(c.find("failure") is not None for c in canonical)==7
for phase in ["after-%02d"%i for i in range(1,31)]+["register"]:
 audit=json.loads((OUT/(phase+"-audit.json")).read_text())
 expected=103 if phase=="register" else 8
 assert audit["passes"]==audit["cases"]==audit["qualifiedEvents"]==audit["firstAttempts"]==expected
 assert audit["retries"]==0
 assert json.loads((OUT/(phase+"-result.json")).read_text())["exit"]==0
assert "Final recheck" in (OUT/"independent-review.md").read_text()
summary={"head":git("rev-parse","HEAD"),"main":git("rev-parse","origin/main"),"baseline":{"canonicalCases":8,"fail":7,"pass":1,"firstAttempts":8,"additionalDraftSetupFailureAttempts":1,"runnerRetries":0},"repeat":{"cases":8,"perCasePasses":30,"firstAttempts":240,"retries":0},"register":{"pass":103,"total":103,"firstAttempts":103,"retries":0},"existingTestEdits":"Only wrong empty-field rejection moved to positive tests; all other pre-existing source and assertions byte-identical","productionEdit":"Only post-normalization empty-member filter"}
(OUT/"final-audit.json").write_text(json.dumps(summary,indent=2)+"\n")
print(json.dumps(summary))

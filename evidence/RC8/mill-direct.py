"""Run the same Mill 0.12.15 no-server entry with no idle Java client process."""
from pathlib import Path
import json, os, subprocess, sys, tempfile, time
ROOT=Path.cwd(); OUT=Path(__file__).resolve().parent
(ROOT/"out").mkdir(exist_ok=True)
args=sys.argv[1:]
assert args[0]=="--no-server" and "-j1" in args
assert not any((ROOT/name).exists() for name in [".mill-opts",".mill-jvm-opts",".mill-jvm-version"])
dist=Path("/home/leslie/.cache/mill/download/0.12.15")
assert dist.exists() and (ROOT/".mill-version").read_text().strip()=="0.12.15"
env=dict(os.environ);env["MILL_WORKSPACE_ROOT"]=str(ROOT)
with tempfile.TemporaryDirectory(prefix="FCOURS5-mill-",dir=OUT) as tmp:
 process_dir=Path(tmp);sandbox=process_dir/"sandbox";sandbox.mkdir()
 (process_dir/"terminfo").write_text("0 0")
 command=["java","-cp",str(dist),"mill.runner.MillMain",str(process_dir),*args[:1],"-D","mill.main.cli="+str(ROOT/"mill"),*args[1:]]
 result=subprocess.run(command,cwd=sandbox,env=env)
sys.exit(result.returncode)

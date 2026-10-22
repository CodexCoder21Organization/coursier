"""Verify the final thirteen-case source against unchanged main, then restore the PR."""
from pathlib import Path
import subprocess, sys, json, hashlib, os

E = Path(__file__).resolve().parent
C = Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-coursier-20261020')
(E / 'final13-controller.pid').write_text(str(os.getpid()) + '\n')
source = 'modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala'
def git(*args):
    return subprocess.check_output(['git', *args], cwd=C)
assert not git('status', '--porcelain').strip()
subprocess.run(['git', 'fetch', 'origin'], cwd=C, check=True)
subprocess.run(['git', 'rebase', 'origin/main'], cwd=C, check=True)
head = git('rev-parse', 'HEAD').decode().strip()
assert head == '2bf531d14bbd6fff9a1fa3d4f069f0aa504525d8'
fixed = (C / source).read_bytes()
original = git('show', 'origin/main:' + source)
(E / 'baseline-final13-source-binding.json').write_text(json.dumps({
    'productHead': head, 'mainHead': git('rev-parse', 'origin/main').decode().strip(),
    'testSha256': hashlib.sha256((C / 'modules/cache/jvm/src/test/scala/coursier/cache/DownloadBufferCapacityTests.scala').read_bytes()).hexdigest(),
    'unchangedMainDownloaderSha256': hashlib.sha256(original).hexdigest(),
    'fixedDownloaderSha256': hashlib.sha256(fixed).hexdigest(),
    'mode': 'temporary product-fix removal, final test source unchanged, ten first-attempt runs'
}, indent=2) + '\n')
try:
    (C / source).write_bytes(original)
    subprocess.run([sys.executable, str(E / 'supervise.py'), 'baseline-final13', sys.executable,
                    str(E / 'repeat-coursier.py'), str(C), 'baseline-final13', '10'], cwd=C, check=True)
finally:
    (C / source).write_bytes(fixed)
assert not git('status', '--porcelain').strip()
subprocess.run([sys.executable, str(E / 'supervise.py'), 'restored-final13', sys.executable,
                str(E / 'repeat-coursier.py'), str(C), 'restored-final13', '1'], cwd=C, check=True)
assert not git('status', '--porcelain').strip()
manifest = json.loads((E / 'final-snapshot-binding.json').read_text())
classes = C / 'out/cache/jvm/2.13.16/compile.dest/classes'
matches = {name: hashlib.sha256((classes / name).read_bytes()).hexdigest() == item['after']
           for name, item in manifest['changedClasses'].items()}
assert all(matches.values())
(E / 'restored-final13-binding.json').write_text(json.dumps({'productHead': head,
    'worktreeClean': True, 'allFiveClassesMatchMeasuredSnapshot': matches}, indent=2) + '\n')

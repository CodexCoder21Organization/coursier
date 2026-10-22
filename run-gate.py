"""Run the recorded fifteen-owner gate locally, once per named evidence phase."""
from pathlib import Path
from datetime import datetime, timezone
import subprocess, json, hashlib, sys
EVIDENCE=Path(__file__).resolve().parent
CORE=Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-kompile-core-20261020')
RUNTIME=Path('/tmp/I419i-runtime')
OWNERS=[
 'testPreparedAdmissionSingletonWorkersRetireBeforeReturn',
 'testPreparedAdmissionParallelWorkersRetireBeforeReturn',
 'testPreparedAdmissionInterruptedWorkerRetiresBeforeReturn',
 'testAttemptTimelineAttributedCompiler',
 'testPreparedBatchDoesNotRescanBuildScriptsOnExecute',
 'testPreparedBatchUsesPreparedWorkspaceRuleBody',
 'testR419aDecodedInputRemainsReusable',
 'testRepeatedPublicBuildCopiesDoNotRetainPayloads',
 'testReviewAdmissionFailureDrainsConcurrentSibling',
 'testAttemptTimelineAttributedPublication',
 'testAttemptTimelineUpstreamLockOwners',
 'testAttemptTimelineOlderHookLibrariesRemainUsable',
 'testAttemptTimelineJoinedWorkloadEvents',
 'testAttemptTimelinePreservesPartialOutputAcrossChildEvents',
 'testAttemptTimelineUpstreamEvents',
]
def git(*args):return subprocess.check_output(['git',*args],cwd=CORE,text=True).strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def file_binding(p):
 p=p.resolve()
 return {'path':str(p),'size':p.stat().st_size,'sha256':digest(p)}
def launcher_classes():
 root=(RUNTIME/'launcher').resolve()
 if not root.is_dir():raise FileNotFoundError('The launcher class directory is missing.')
 paths=sorted(root.rglob('*.class'))
 if not paths:raise FileNotFoundError('The launcher class directory contains no class files.')
 return {p.relative_to(root).as_posix():{'size':p.stat().st_size,'sha256':digest(p)} for p in paths}
def artifact_bindings():
 launcher=(RUNTIME/'launcher').resolve()
 return {'managerJar':file_binding(RUNTIME/'manager.jar'),
         'controlAttemptsSource':file_binding(EVIDENCE/'ControlAttempts.java'),
         'launcherDirectory':str(launcher),'launcherClasses':launcher_classes()}
def snapshot_selectors(paths):
 try:return {name:digest(Path(path)) for name,path in paths.items()}
 except OSError:return None
def git_snapshot(name):
 try:return git('rev-parse',name)
 except (OSError,subprocess.CalledProcessError):return None
def worktree_clean():
 try:return not bool(git('status','--porcelain'))
 except (OSError,subprocess.CalledProcessError):return False
phase=sys.argv[1]
if not phase.replace('-','').isalnum():raise ValueError('A simple phase name is required')
if git('status','--porcelain'):raise RuntimeError('Product worktree must be clean')
subprocess.run(['git','fetch','origin'],cwd=CORE,check=True)
subprocess.run(['git','rebase','origin/main'],cwd=CORE,check=True)
if git('rev-parse','HEAD') != (RUNTIME/'manager-source-head.txt').read_text().strip():raise RuntimeError('Core source moved after the bound outer-manager build; rebuild before running the gate')
run=RUNTIME/phase;run.mkdir(exist_ok=False)
empty=run/'empty';empty.mkdir()
selectors=[CORE/'tests'/(owner+'.kts') for owner in OWNERS]
assert all(p.is_file() for p in selectors)
selector_paths={p.stem:str(p.resolve()) for p in selectors}
selector_hashes=snapshot_selectors(selector_paths)
if selector_hashes is None:raise RuntimeError('Could not hash every selected test source before launch.')
if selector_hashes != json.loads((EVIDENCE/'gate-selector-binding.json').read_text()):raise RuntimeError('A selected test changed after the prior gate; do not silently measure a different cohort')
artifacts=artifact_bindings()
pre={'schemaVersion':2,'startedAtUtc':datetime.now(timezone.utc).isoformat(),'head':git('rev-parse','HEAD'),'main':git('rev-parse','origin/main'),'iterations':30,'workers':4,'cpuAffinity':[0,1],'maxTestAttempts':1,'expectedCases':450,'managerSha256':artifacts['managerJar']['sha256'],'launcherSourceSha256':artifacts['controlAttemptsSource']['sha256'],'selectors':selector_hashes,'selectorPaths':selector_paths,'artifacts':artifacts,'mode':'local LocalBuildWorkspace.executeTestsWithTimeline','originalHarnessDifference':'Core dc3a851aa with the evidence snapshot-repository configuration patch. Outer manager and nested fixture repositories use the unpublished fixed cache. The fifteen test sources, repetitions, parallelism, CPU affinity and attempt cap match I419h. Gate build cache is new; the warm-up consumer uses a separate result directory in the same workspace cache.'}
(run/'preflight.json').write_text(json.dumps(pre,indent=2)+'\n')
command=[sys.executable,str(EVIDENCE/'supervise.py'),phase,'taskset','-c','0,1','java','-Dcontrol.sourceRoot='+str(empty),'-cp',str(RUNTIME/'launcher')+':'+str(RUNTIME/'manager.jar'),'ControlAttempts',str(CORE),str(RUNTIME/'gate-cache'),str(run/'raw'),'30','4',*map(str,selectors)]
with (run/'controller.log').open('w') as log:
 result=subprocess.run(command,cwd=CORE,stdout=log,stderr=subprocess.STDOUT)
post_selectors=snapshot_selectors(selector_paths)
try:
 post_artifacts=artifact_bindings()
except (OSError,ValueError):
 post_artifacts=None
post={'schemaVersion':2,'finishedAtUtc':datetime.now(timezone.utc).isoformat(),'head':git_snapshot('HEAD'),'main':git_snapshot('origin/main'),'returncode':result.returncode,'selectors':post_selectors,'sourceUnchanged':post_selectors==pre['selectors'],'worktreeClean':worktree_clean(),'artifacts':post_artifacts}
(run/'postflight.json').write_text(json.dumps(post,indent=2)+'\n')
print(json.dumps(post))
audit=subprocess.run([sys.executable,str(EVIDENCE/'audit-gate.py'),str(run),'--require-accepted'])
raise SystemExit(result.returncode or audit.returncode)

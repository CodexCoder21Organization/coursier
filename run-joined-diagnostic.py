"""Run a separate, single-worker diagnostic after the unchanged gate is terminal."""
from pathlib import Path
import subprocess, sys, json, hashlib

E = Path(__file__).resolve().parent
R = Path('/tmp/I419i-runtime')
C = Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-kompile-core-20261020')
phase = sys.argv[1] if len(sys.argv) > 1 else 'joined-diagnostic'
assert phase in ['joined-diagnostic', 'joined-late-handoff']
counts = json.loads((R / 'fixed-cache/counts.json').read_text())
assert counts['terminal'] and counts['completeIterations'] == 30 and counts['allExpectedCasesPresent']
subprocess.run(['git', 'fetch', 'origin'], cwd=C, check=True)
subprocess.run(['git', 'rebase', 'origin/main'], cwd=C, check=True)
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=C, text=True).strip() == (R / 'manager-source-head.txt').read_text().strip()
assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=C, text=True).strip()
subprocess.run(['free', '-m'], check=True)
subprocess.run(['javac', '-cp', str(R / 'manager.jar'), '-d', str(R / 'diagnostic-launcher'),
                str(E / 'DiagnosticAttempts.java')], check=True)
subprocess.run(['git', 'fetch', 'origin'], cwd=C, check=True)
subprocess.run(['git', 'rebase', 'origin/main'], cwd=C, check=True)
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=C, text=True).strip() == (R / 'manager-source-head.txt').read_text().strip()
subprocess.run(['free', '-m'], check=True)
source = R / ('diagnostic-sources/tests/testI419iJoinedLateHandoffDiagnostic.kts' if phase == 'joined-late-handoff'
              else 'diagnostic-sources/tests/testI419iJoinedWorkloadDiagnostic.kts')
preflight = {'iterations': 1, 'workers': 1, 'maxTestAttempts': 1,
             'phase': phase,
             'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
             'main': subprocess.check_output(['git', 'rev-parse', 'origin/main'], cwd=C, text=True).strip(),
             'note': 'Separate diagnostic copy; original 450-case gate is already complete and unchanged. '
                     'The inner test source, assertions and timeouts remain unchanged; added output and public Future reads expose progress. '
                     'A diagnostic observer records derived frames from descendant JVMs; this is not an acceptance measurement.'}
if phase == 'joined-late-handoff':
    preflight['forcedOrdering'] = 'The public readiness callback waits for the existing10-second accept Future to fail before returning and releasing the nested body; no original timeout or assertion changes.'
(E / (phase + '-preflight.json')).write_text(json.dumps(preflight, indent=2) + '\n')
command = ['taskset', '-c', '0,1', 'java',
           '-Dcontrol.sourceRoot=' + str(R / 'diagnostic-sources'),
           '-Ddiagnostic.captureScript=' + str(E / 'capture-diagnostic-child.py'),
           '-cp', str(R / 'diagnostic-launcher') + ':' + str(R / 'manager.jar'), 'DiagnosticAttempts',
           str(C), str(R / 'gate-cache'), str(R / phase / 'raw'), '1', '1', str(source)]
(R / phase).mkdir(exist_ok=False)
status = subprocess.run([sys.executable, str(E / 'supervise.py'), phase, *command], cwd=C).returncode
(E / (phase + '-controller.exit')).write_text(str(status) + '\n')
raise SystemExit(status)

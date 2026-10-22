"""Read the generated fixture archives and resolved cache artifacts after a run."""
from pathlib import Path
import hashlib, json, zipfile

E = Path(__file__).resolve().parent
R = Path('/tmp/I419i-runtime')
cache = R / 'gate-cache'
expected = hashlib.sha256((R / 'cache-snapshot.jar').read_bytes()).hexdigest()
entry = 'kompile-test-maven/io/get-coursier/coursier-cache_2.13/2.1.31-buffer3/coursier-cache_2.13-2.1.31-buffer3.jar'
fixtures = []
resolved = []
for artifact in cache.rglob('*.jar'):
    if artifact.name == 'coursier-cache_2.13-2.1.31-buffer3.jar':
        actual = hashlib.sha256(artifact.read_bytes()).hexdigest()
        resolved.append({'path': str(artifact), 'sha256': actual, 'matchesSnapshot': actual == expected})
    with zipfile.ZipFile(artifact) as archive:
        if entry in archive.namelist():
            actual = hashlib.sha256(archive.read(entry)).hexdigest()
            fixtures.append({'path': str(artifact), 'nestedCacheSha256': actual,
                             'matchesSnapshot': actual == expected})
assert fixtures and all(item['matchesSnapshot'] for item in fixtures)
assert resolved and all(item['matchesSnapshot'] for item in resolved)
(E / 'fixture-byte-bindings.json').write_text(json.dumps({'expectedSnapshotSha256': expected,
    'entry': entry, 'fixtureJars': fixtures}, indent=2) + '\n')
(E / 'gate-resolved-cache-bindings.json').write_text(json.dumps({'expectedSha256': expected,
    'cacheArtifacts': resolved}, indent=2) + '\n')

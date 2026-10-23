"""Stage and check exactly the seven existing release modules; publish nothing."""
import argparse
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

VERSION = '2.1.31-buffer6'
SOURCE = '119e39d993a0e5f96a489c9e9eddcd0e0acf9a4a'
MODULES = {
    'cache-util': 'cache-util',
    'coursier-paths': 'paths',
    'coursier-util_2.13': 'util/jvm/2.13.16',
    'coursier-core_2.13': 'core/jvm/2.13.16',
    'coursier-cache_2.13': 'cache/jvm/2.13.16',
    'coursier-proxy-setup': 'proxy-setup',
    'coursier_2.13': 'coursier/jvm/2.13.16',
}

def sha(data):
    return hashlib.sha256(data).hexdigest()

p = argparse.ArgumentParser()
p.add_argument('--repo', required=True, type=Path)
p.add_argument('--output', required=True, type=Path)
a = p.parse_args()
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=a.repo, text=True).strip() == SOURCE
assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=a.repo, text=True).strip()
records = []
for module, task in MODULES.items():
    target = a.output / 'artifacts' / module
    target.mkdir(parents=True, exist_ok=False)
    for kind, suffix in [('jar', '.jar'), ('pom', '.pom'), ('sourceJar', '-sources.jar')]:
        metadata = json.loads((a.repo / 'out' / task / (kind + '.json')).read_text())
        value = metadata['value']
        assert isinstance(value, str), (task, kind, value)
        source = Path(value[value.index(':/') + 1:])
        filename = f'{module}-{VERSION}{suffix}'
        shutil.copyfile(source, target / filename)
        records.append({'coordinate': f'io.get-coursier:{module}:{VERSION}', 'file': filename,
                        'millTask': task.replace('/', '.') + '.' + kind,
                        'buildOutput': str(source.relative_to(a.repo)),
                        'size': source.stat().st_size, 'sha256': sha(source.read_bytes())})
    root = ET.parse(target / f'{module}-{VERSION}.pom').getroot()
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    assert root.findtext('m:groupId', namespaces=ns) == 'io.get-coursier'
    assert root.findtext('m:artifactId', namespaces=ns) == module
    assert root.findtext('m:version', namespaces=ns) == VERSION
    for dep in root.findall('m:dependencies/m:dependency', ns):
        if dep.findtext('m:groupId', namespaces=ns) == 'io.get-coursier' and dep.findtext('m:artifactId', namespaces=ns) in MODULES:
            assert dep.findtext('m:version', namespaces=ns) == VERSION
assert len(records) == 21
(a.output / 'local-artifacts.json').write_text(json.dumps(records, indent=2) + '\n')
print(f'Staged {len(MODULES)} modules and {len(records)} files from {SOURCE}.')

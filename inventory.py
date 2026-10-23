import json,re,urllib.request,xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime,timezone
modules=['cache-util','coursier-paths','coursier-util_2.13','coursier-core_2.13','coursier-cache_2.13','coursier-proxy-setup','coursier_2.13']
evidence=Path('/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/PUBCOURS3-evidence-20261023')
rows=[]
for module in modules:
    base=f'https://kotlin.directory/io/get-coursier/{module}/'
    html=urllib.request.urlopen(base,timeout=30).read().decode()
    xml=urllib.request.urlopen(base+'maven-metadata.xml',timeout=30).read().decode()
    (evidence/f'versions-{module}.html').write_text(html)
    (evidence/f'versions-{module}.xml').write_text(xml)
    listing=set(re.findall(r'2\.1\.31-[A-Za-z0-9._-]+',html))
    metadata={v.text for v in ET.fromstring(xml).findall('./versioning/versions/version') if v.text.startswith('2.1.31-')}
    rows.append(dict(module=module,listingVersions=sorted(listing),metadataVersions=sorted(metadata),versions=sorted(listing|metadata)))
claimed={v for row in rows for v in row['versions']}
n=max([int(v.removeprefix('2.1.31-buffer')) for v in claimed if re.fullmatch(r'2\.1\.31-buffer\d+',v)]+[0])+1
version=f'2.1.31-buffer{n}'
assert version not in claimed
(evidence/'versions-inventory.json').write_text(json.dumps(dict(checkedAtUtc=datetime.now(timezone.utc).isoformat(),selectedVersion=version,modules=rows),indent=2)+'\n')
print(json.dumps(dict(selectedVersion=version,modules=rows),indent=2))

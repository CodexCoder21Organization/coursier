from pathlib import Path
import subprocess,sys
p=Path(__file__).resolve().parent
root=Path(sys.argv[1])
names=[
 'empty content encoding means an unencoded body',
 'empty list elements do not change identity encoding',
 'empty list elements do not allow unknown encodings',
 'only empty encoding list elements mean an unencoded body',
 'empty list elements preserve compressed decoding',
 'empty encoding list elements preserve length validation',
 'empty encoding list elements preserve saved prefix handling',
 'empty list elements preserve encoded resume rejection'
]
suite='coursier.cache.DownloadResponseFramingTests'
query=suite+'.{'+','.join(names)+'}'
subprocess.run([sys.executable,str(p/'run-command.py'),str(root),'head-a1',sys.executable,str(p/'mill-direct.py'),'--no-server','-j1','cache.jvm[2.13.16].test.testOnly',suite,'--',query],check=True)
subprocess.run([sys.executable,str(p/'audit-phase.py'),str(root),'head-a1','8'],check=True)

import subprocess,pathlib,os,time,json,sys
p=pathlib.Path(__file__).resolve().parent
targets=p.joinpath('compile-targets.txt').read_text().splitlines(); args=[]
for t in targets:
 if args:args.append('+')
 args.append(t)
env=dict(os.environ,PATH='/home/leslie/bin:'+os.environ['PATH'])
name=sys.argv[1]
with p.joinpath(name+'.log').open('w') as f:
 start=time.time();r=subprocess.run(['timeout','1200','./mill','--no-server','--disable-prompt','--color','false','-j1','-k',*args],stdout=f,stderr=subprocess.STDOUT,env=env)
p.joinpath(name+'-result.txt').write_text(f'exit={r.returncode} elapsed={time.time()-start:.1f}s\n');print(r.returncode)

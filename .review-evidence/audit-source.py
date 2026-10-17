import subprocess,re,json,difflib,hashlib
from pathlib import Path
base='5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3'
head='d5eec8d10bf59305b4263bb96e741e45408c9f85'
p='modules/cache/jvm/src/test/scala/coursier/cache/FileCacheLocationOwnershipTests.scala'
def show(rev,path): return subprocess.check_output(['git','show',rev+':'+path],text=True)
def tokens(s):
 pattern=r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/|[A-Za-z_$][\w$]*|\d+(?:\.\d+)?|[^\s]'
 return [t for t in re.findall(pattern,s) if not t.startswith(('//','/*'))]
def calls(ts,prefix):
 out=[]
 for i in range(len(ts)):
  if ts[i:i+len(prefix)]!=prefix:continue
  start=i+len(prefix)-1
  if ts[start]!='(':raise ValueError(prefix)
  level=0
  for j in range(start,len(ts)):
   if ts[j]=='(':level+=1
   if ts[j]==')':level-=1
   if level==0:
    out.append(ts[i:j+1]);break
 return out
a,b=tokens(show(base,p)),tokens(show(head,p))
result={'base':base,'head':head,'file':p,'whole_token_stream_equal':a==b,'comparisons':{}}
for name,prefix in [('assertions',['assert','(']),('constructors',['FileCache','[','Task',']','(']),('await_results',['Await','.','result','('])]:
 x,y=calls(a,prefix),calls(b,prefix)
 result['comparisons'][name]={'before':len(x),'after':len(y),'equal':x==y}
result['token_differences']=[{'operation':op,'before':a[i:j],'after':b[k:l]} for op,i,j,k,l in difflib.SequenceMatcher(a=a,b=b,autojunk=False).get_opcodes() if op!='equal']
paths=['modules/cache/jvm/src/main/scala/coursier/cache/CacheLocks.scala','modules/cache/jvm/src/main/scala/coursier/cache/internal/Downloader.scala']
result['destination_fix']={p:{'equal_91c1001b':show('91c1001b',p)==show(head,p),'equal_5470aee6':show(base,p)==show(head,p),'sha256':hashlib.sha256(show(head,p).encode()).hexdigest()} for p in paths}
print(json.dumps(result,indent=2))

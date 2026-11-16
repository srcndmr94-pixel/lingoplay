import ast
import json
import subprocess
import sys
import time
from pathlib import Path
root=Path(__file__).resolve().parents[1]
suite=(root/'test/run_all.py').read_text()
start=suite.index('TESTS = [')+len('TESTS = ')
end=suite.index('\n]\n',start)+2
names=ast.literal_eval(suite[start:end])
results=[]
with (root/'OCR299-ESKI-TAM-TEST-LOG.txt').open('w') as f:
    for i,name in enumerate(names,1):
        path=root/'test'/name
        before=time.monotonic()
        try:
            if not path.exists():
                record={'test':name,'state':'MISSING','seconds':0,'detail':'Test file absent from current source ZIP'}
            else:
                proc=subprocess.run([sys.executable,str(path)],cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=65)
                record={'test':name,'state':'PASS' if proc.returncode==0 else 'FAIL','seconds':round(time.monotonic()-before,2),'code':proc.returncode,'detail':proc.stdout[-8500:]}
        except subprocess.TimeoutExpired as exc:
            record={'test':name,'state':'TIMEOUT','seconds':round(time.monotonic()-before,2),'detail':str(exc)}
        results.append(record)
        f.write('\n=== '+str(i)+'/'+str(len(names))+' '+name+' '+record['state']+' ===\n'+record.get('detail','')+'\n');f.flush()
        print(f"{i:02d}/{len(names)} {name}: {record['state']} ({record['seconds']} s)",flush=True)
summary={state:sum(t['state']==state for t in results) for state in ('PASS','FAIL','MISSING','TIMEOUT')}
(root/'OCR299-ESKI-TAM-TEST-SONUC.json').write_text(json.dumps({'summary':summary,'results':results},ensure_ascii=False,indent=2))
print('FINAL '+json.dumps(summary),flush=True)

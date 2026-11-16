from pathlib import Path
import json, subprocess, sys, time, concurrent.futures
root=Path(__file__).resolve().parent
names=[x['test'] for x in json.loads((root/'OCR299-ESKI-TAM-TEST-SONUC.json').read_text())['results']]
def run(n):
 t=time.monotonic()
 try:
  p=subprocess.run([sys.executable,str(root/'test'/n)],cwd=root,capture_output=True,text=True,timeout=60)
  return {'test':n,'status':'PASS' if p.returncode==0 else 'FAIL','time':round(time.monotonic()-t,1),'tail':(p.stdout+'\n'+p.stderr)[-1500:]}
 except subprocess.TimeoutExpired:
  return {'test':n,'status':'TIMEOUT','time':round(time.monotonic()-t,1),'tail':'Timed out'}
results=[]
if 'test_denetleyici.py' in names:
 results.append(run('test_denetleyici.py'))
 names.remove('test_denetleyici.py')
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 for r in pool.map(run,names): results.append(r)
for o in results:
 print(o['status'],o['test'],o['time'])
 if o['status']!='PASS':print('   ',o['tail'][-500:].replace('\n',' '))
summary={v:sum(x['status']==v for x in results) for v in ['PASS','FAIL','TIMEOUT']}
print('COMPLETE',summary)
(root/'NATURAL198-61-TEST-RESULT.json').write_text(json.dumps({'summary':summary,'results':results},ensure_ascii=False,indent=2))

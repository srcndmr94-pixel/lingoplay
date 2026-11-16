import json, subprocess, concurrent.futures, pathlib, time, sys
root=pathlib.Path(__file__).resolve().parent
original=json.loads((root/'OCR299-ESKI-TAM-TEST-SONUC.json').read_text())
names=[x['test'] for x in original['results']]
print('RUNNING',len(names),'legacy scripts',flush=True)
def run_one(n):
 start=time.monotonic()
 try:
  p=subprocess.run([sys.executable,str(root/'test'/n)],cwd=str(root),capture_output=True,text=True,timeout=40)
  return dict(test=n,status='PASS' if p.returncode==0 else 'FAIL',exit_code=p.returncode,seconds=round(time.monotonic()-start,2),tail=(p.stdout+'\n'+p.stderr)[-1500:])
 except subprocess.TimeoutExpired as e:
  return dict(test=n,status='TIMEOUT',seconds=round(time.monotonic()-start,2),tail=str(e)[-500:])
with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
 futures={pool.submit(run_one,n):n for n in names}
 out=[]
 for f in concurrent.futures.as_completed(futures):
  r=f.result(); out.append(r); print(r['status'],r['test'],f"{r['seconds']}s",flush=True)
out.sort(key=lambda a:names.index(a['test']))
summary={state:sum(x['status']==state for x in out) for state in ('PASS','FAIL','TIMEOUT')}
(root/'MEANING-ESKI-TAM-TEST-SONUC.json').write_text(json.dumps(dict(summary=summary,results=out),ensure_ascii=False,indent=2))
print('COMPLETE',summary,flush=True)
for r in out:
 if r['status']!='PASS': print('ISSUE',r['test'],r['tail'][-350:].replace('\n',' '),flush=True)

#!/usr/bin/env python3
"""GameLingo 202 live benchmark: extract per-run engine attempts from copied working log.
Run: python3 benchmark202_log.py calisma_kaydi.txt
Uses the Python standard library only; doesn't send text over the network.
"""
import sys, re, csv, statistics
from pathlib import Path
from collections import defaultdict

def parse_line(line):
    if not line.startswith('BENCH202|'): return None
    d = {}
    for part in line.strip().split('|')[1:]:
        if '=' in part:
            k, v = part.split('=', 1)
            d[k] = v
    return d

def percentile(a, p):
    if not a: return ''
    a = sorted(a)
    x = (len(a) - 1) * p / 100
    lo = int(x)
    hi = min(lo+1, len(a)-1)
    return round(a[lo] + (a[hi]-a[lo]) * (x - lo), 1)

def main():
    if len(sys.argv)!=2:
        print(__doc__); return 2
    src=Path(sys.argv[1]); out=src.parent
    rows=[r for line in src.read_text(encoding='utf-8',errors='replace').splitlines()
          if (r:=parse_line(line))]
    if not rows:
        print('BENCH202 kaydi yok: 202 Benchmark APK ve yeni log gerekli.');return 1
    keys=['id','stage','engine','ms','since','accepted','reason','source','context','output','tag']
    dest=out/'motor_kayitlari_202.csv'
    with dest.open('w',newline='',encoding='utf-8-sig') as f:
        w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore'); w.writeheader();w.writerows(rows)
    engines=defaultdict(list)
    by_id=defaultdict(list)
    for r in rows:
        by_id[r.get('id','?')].append(r)
        if r.get('stage')=='ENGINE' and r.get('ms','').isdigit():
            engines[r.get('engine','?')].append(int(r['ms']))
    stats=[]
    for engine, times in sorted(engines.items()):
        stats.append({'motor':engine,'deneme':len(times),'p50_ms':percentile(times,50),
                      'p95_ms':percentile(times,95)})
    with (out/'motor_ozet_202.csv').open('w',newline='',encoding='utf-8-sig') as f:
        w=csv.DictWriter(f,fieldnames=['motor','deneme','p50_ms','p95_ms']); w.writeheader();w.writerows(stats)
    with (out/'replik_karsilastirma_202.csv').open('w',newline='',encoding='utf-8-sig') as f:
        fields=['run_id','kaynak','baglam','ilk_gosterilen','motor_denemeleri','kilitlenip_gosterilmeyen']
        w=csv.DictWriter(f,fieldnames=fields);w.writeheader()
        for run_id, rr in sorted(by_id.items(),key=lambda kv: int(kv[0]) if kv[0].isdigit() else -1):
            e=next((x for x in rr if x.get('source')),{})
            display=[x for x in rr if x.get('stage')=='DISPLAY_ACCEPTED']
            locked=[x for x in rr if x.get('stage')=='LOCKED']
            attempts=[x for x in rr if x.get('stage')=='ENGINE']
            w.writerow({'run_id':run_id,'kaynak':e.get('source',''),
                'baglam':e.get('context',''),
                'ilk_gosterilen':display[0].get('output','') if display else '',
                'motor_denemeleri':' / '.join(f"{x.get('engine','?')}: {x.get('ms','?')}ms -> {x.get('output','')}"
                    for x in attempts),
                'kilitlenip_gosterilmeyen':' / '.join(x.get('output','') for x in locked)})
    print(f'{len(by_id)} runId, {len(rows)} BENCH202 olay, {sum(map(len,engines.values()))} motor olcumu')
    print('CSV dosyalari:',dest.name,'motor_ozet_202.csv','replik_karsilastirma_202.csv')
    print('NOT: BENCH202 sureleri motor suresidir; OCR->ekran toplam gecikmesi degildir.')
    return 0
if __name__=='__main__': raise SystemExit(main())

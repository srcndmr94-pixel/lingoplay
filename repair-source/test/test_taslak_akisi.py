"""PC: önce hızlı taslak (gri) anında görünmeli, yapay zekânın doğrusu gelince üstüne yazmalı;
eski satırın geç gelen çevirisi yeni satırın üstüne yazmamalı."""
import importlib.util, os, sys, threading, time
PC_YOLU = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))
spec = importlib.util.spec_from_file_location("lingoplay", PC_YOLU)
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)
import tempfile
_d = tempfile.mkdtemp(); lp.MEMORY_PATH = os.path.join(_d, "hafiza.json"); lp.APP_DIR = _d

class Kutu:
    def __init__(self): self.olaylar = []
    def yaz(self, m, taslak=False): self.olaylar.append((round(time.time() - T0, 2), m, taslak))

class SahteKopru:
    def __init__(self, a, yz_sure=1.0, yz_cevap=True):
        self.son_hata = None; self.son_sure = 0; self.atlandi = False
        self.yz_sure = yz_sure; self.yz_cevap = yz_cevap
    def taslak(self, m): time.sleep(0.05); return "taslak:" + m
    def cevir(self, m):
        time.sleep(self.yz_sure); self.son_sure = self.yz_sure
        if not self.yz_cevap: self.son_hata = "model meşgul"; return None
        return "YZ:" + m

sorun = []
durumlar = []
# 1) Normal akış
T0 = time.time()
k = Kutu(); m = lp.Motor(dict(lp.VARSAYILAN, telefon_kod="1"), k, durumlar.append)
m.cevirmen = SahteKopru(None)
m.son_okunan = "Watch out behind you."
m._isle("Watch out behind you.")
print("1)", k.olaylar)
if len(k.olaylar) != 2: sorun.append(f"iki adım beklenirdi: {k.olaylar}")
else:
    (t1, y1, tas1), (t2, y2, tas2) = k.olaylar
    if not (tas1 and y1.startswith("taslak:") and t1 < 0.3): sorun.append(f"taslak hemen gelmedi: {k.olaylar[0]}")
    if tas2 or not y2.startswith("YZ:"): sorun.append(f"yapay zekâ üstüne yazmadı: {k.olaylar[1]}")
if not any("taslak" in d and "yapay zekâ" in d for d in durumlar): sorun.append(f"durum satırı: {durumlar}")
# hafızadan ikinci kez: anında, taslaksız
k.olaylar.clear(); T0 = time.time()
m._isle("Watch out behind you.")
if len(k.olaylar) != 1 or k.olaylar[0][2] or k.olaylar[0][0] > 0.1: sorun.append(f"hafıza anında gelmedi: {k.olaylar}")

# 2) Eski satırın geç çevirisi yeni satırın üstüne yazmamalı
T0 = time.time()
k2 = Kutu(); m2 = lp.Motor(dict(lp.VARSAYILAN, telefon_kod="1"), k2, lambda d: None)
m2.cevirmen = SahteKopru(None, yz_sure=0.8)
m2.son_okunan = "First line here."
th = threading.Thread(target=m2._isle, args=("First line here.",)); th.start()
time.sleep(0.3)
m2.son_okunan = "A completely different second line."   # yeni satır okundu
th.join()
print("2)", k2.olaylar)
if any(y.startswith("YZ:First") for _, y, _ in k2.olaylar): sorun.append("eski satırın çevirisi yeni satırın üstüne yazıldı")
if m2.hafiza.al("First line here.") != "YZ:First line here.": sorun.append("eski satırın çevirisi hafızaya girmedi")

# 3) Yapay zekâ başarısız: taslak kalıcı (beyaz) olmalı
T0 = time.time()
k3 = Kutu(); d3 = []
m3 = lp.Motor(dict(lp.VARSAYILAN, telefon_kod="1"), k3, d3.append)
m3.cevirmen = SahteKopru(None, yz_sure=0.1, yz_cevap=False)
m3.son_okunan = "No time left."
m3._isle("No time left.")
print("3)", k3.olaylar, d3)
if len(k3.olaylar) != 2 or k3.olaylar[1][2] or k3.olaylar[1][1] != "taslak:No time left.":
    sorun.append(f"başarısızlıkta taslak kalıcı olmadı: {k3.olaylar}")

# 4) Çalışma kaydı akışı anlatıyor mu
kayit = lp.KAYIT.metin()
print("Kayıttan:", kayit.splitlines()[-4:])
for beklenen in ("YAZILAN · taslak", "YAZILAN · yapay zekâ", "HATA: Yapay zekâ çeviremedi", "geç kaldı, hafızaya alındı"):
    if beklenen not in kayit: sorun.append(f"kayıtta yok: {beklenen}")
if "Yapay zekâ çeviremedi" not in lp.KAYIT.son_hata: sorun.append(f"son hata yanlış: {lp.KAYIT.son_hata}")

if sorun: print("BAŞARISIZ:", sorun); sys.exit(1)
print("Taslak akışı testi geçti.")

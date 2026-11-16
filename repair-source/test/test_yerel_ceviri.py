"""Bilgisayarın kendi çevirisi (YerelCeviri): model indirme, açma, yükleme, çeviri, motor akışı.
Gerçek ctranslate2/sentencepiece yerine sahteleri kullanılır; indirme yerel bir sahte sunucudan yapılır."""
import http.server, importlib.util, io, json, os, sys, tempfile, threading, time, types, zipfile

PC_YOLU = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))

# --- sahte çeviri motoru -----------------------------------------------------
SOZLUK = {"where are you going?": "Nereye gidiyorsun?", "he is waiting outside.": "Dışarıda bekliyor."}
cagri = {"cevir": 0, "yukle": []}

sp_mod = types.ModuleType("sentencepiece")
class SP:
    def __init__(self, model_file):
        assert os.path.exists(model_file), model_file
    def encode(self, metin, out_type=str):
        return ["▁" + w for w in metin.split()]
    def decode(self, parcalar):
        return " ".join(parcalar).replace("▁", "").strip()
sp_mod.SentencePieceProcessor = SP

ct_mod = types.ModuleType("ctranslate2")
class Translator:
    def __init__(self, yol, **k):
        assert os.path.exists(os.path.join(yol, "model.bin")), yol
        cagri["yukle"].append(k)
    def translate_batch(self, partiler, **k):
        cagri["cevir"] += 1
        time.sleep(0.02)
        cumle = " ".join(p.replace("▁", "") for p in partiler[0]).lower()
        hedef = SOZLUK.get(cumle, "çeviri: " + cumle)
        return [types.SimpleNamespace(hypotheses=[["▁" + w for w in hedef.split()]])]
ct_mod.Translator = Translator

import tkinter  # sahte
spec = importlib.util.spec_from_file_location("lingoplay", PC_YOLU)
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)

d = tempfile.mkdtemp()
lp.APP_DIR = d
lp.MODEL_DIR = os.path.join(d, "model_en_tr")
lp.MEMORY_PATH = os.path.join(d, "hafiza.json")
sorun = []

# --- 1) motor kitaplığı yoksa anlaşılır hata, program çökmez
y0 = lp.YerelCeviri({})
durumlar = []
if y0.hazirla(durumlar.append) or "Telefon seçeneğini" not in (y0.son_hata or ""):
    sorun.append(f"kitaplık yokken hata mesajı yanlış: {y0.son_hata}")
if y0.cevir("Hello there") is not None:
    sorun.append("hazır değilken çeviri döndü")
print("1) kitaplık yok:", durumlar[-1][:70])

sys.modules["ctranslate2"] = ct_mod
sys.modules["sentencepiece"] = sp_mod

# --- sahte argos paketi + sahte sunucu ---------------------------------------
def paket():
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w") as z:
        z.writestr("translate-en_tr-1_5/metadata.json", "{}")
        z.writestr("translate-en_tr-1_5/sentencepiece.model", "sp")
        z.writestr("translate-en_tr-1_5/model/model.bin", "bin")
        z.writestr("translate-en_tr-1_5/model/config.json", "{}")
    return b.getvalue()

PAKET = paket()
istekler = []
class Sunucu(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        istekler.append(self.path)
        if "Python-urllib" in self.headers.get("User-Agent", "") or self.path == "/yasak.argosmodel":
            self.send_response(403); self.end_headers(); return   # gerçek sunucular gibi
        if self.path == "/index.json":
            govde = json.dumps([
                {"code": "translate-en_tr", "from_code": "en", "to_code": "tr", "package_version": "1.0",
                 "links": [f"http://127.0.0.1:{port}/eski.argosmodel"]},
                {"code": "translate-en_tr", "from_code": "en", "to_code": "tr", "package_version": "1.5",
                 "links": [f"http://127.0.0.1:{port}/yasak.argosmodel", f"http://127.0.0.1:{port}/en_tr.argosmodel"]},
                {"code": "translate-tr_en", "from_code": "tr", "to_code": "en", "package_version": "9.0",
                 "links": [f"http://127.0.0.1:{port}/ters.argosmodel"]},
            ]).encode()
        elif self.path == "/en_tr.argosmodel":
            govde = PAKET
        else:
            self.send_response(404); self.end_headers(); return
        self.send_response(200)
        self.send_header("Content-Length", str(len(govde)))
        self.end_headers()
        self.wfile.write(govde)

srv = http.server.HTTPServer(("127.0.0.1", 0), Sunucu)
port = srv.server_address[1]
threading.Thread(target=srv.serve_forever, daemon=True).start()
lp.ARGOS_INDEKS = f"http://127.0.0.1:{port}/index.json"

# --- 2) ilk açılış: indirir, açar, yükler
y = lp.YerelCeviri({})
durumlar = []
if not y.hazirla(durumlar.append):
    sorun.append(f"model hazırlanamadı: {y.son_hata}")
if istekler[:3] != ["/index.json", "/yasak.argosmodel", "/en_tr.argosmodel"] or "/ters.argosmodel" in istekler:
    sorun.append(f"yanlış paket indirildi: {istekler}")
if not os.path.exists(os.path.join(lp.MODEL_DIR, "translate-en_tr-1_5", "model", "model.bin")):
    sorun.append("model klasöre açılmadı")
if any(n.endswith(".indiriliyor") for n in os.listdir(d)):
    sorun.append("geçici indirme dosyası kaldı")
if not any("indiriliyor" in m for m in durumlar) or not durumlar[-1].startswith("Dil paketi hazır"):
    sorun.append(f"durum satırları eksik: {durumlar}")
if cagri["yukle"][-1].get("intra_threads", 9) > 2 or cagri["yukle"][-1].get("compute_type") != "int8":
    sorun.append(f"model ağır ayarla yüklendi: {cagri['yukle'][-1]}")
print("2) ilk açılış:", " | ".join(durumlar))

# --- 3) çeviri ve önbellek
c1 = y.cevir("Where are you going?")
n = cagri["cevir"]
c2 = y.cevir("Where are you going?")
if c1 != "Nereye gidiyorsun?": sorun.append(f"çeviri yanlış: {c1}")
if c2 != c1 or cagri["cevir"] != n: sorun.append("önbellek çalışmıyor")
print("3) çeviri:", c1, f"({int(y.son_sure * 1000)} ms)")

# --- 4) ikinci açılış: tekrar indirmez
istekler.clear()
y2 = lp.YerelCeviri({})
if not y2.hazirla() or istekler:
    sorun.append(f"model varken yeniden indirildi: {istekler}")

# --- 5) internet yok ama .argosmodel elle konmuş
import shutil
shutil.rmtree(lp.MODEL_DIR)
lp.ARGOS_INDEKS = "http://127.0.0.1:1/index.json"
open(os.path.join(d, "translate-en_tr-1_5.argosmodel"), "wb").write(PAKET)
y3 = lp.YerelCeviri({})
if not y3.hazirla():
    sorun.append(f"elle konan paket kullanılmadı: {y3.son_hata}")
os.remove(os.path.join(d, "translate-en_tr-1_5.argosmodel"))

# --- 6) internet yok, paket yok: anlaşılır hata
shutil.rmtree(lp.MODEL_DIR)
y4 = lp.YerelCeviri({})
if y4.hazirla() or "telefon seçeneğini" not in (y4.son_hata or ""):
    sorun.append(f"internetsiz hata mesajı yanlış: {y4.son_hata}")
if lp.durum_rengi(y4.son_hata) != lp.RENK["kirmizi"]:
    sorun.append("hazırlanamadı mesajı kırmızı değil")
print("6) internet yok:", y4.son_hata[:80])

# --- 7) motor akışı: taslak yok, hafızaya yazmaz, hazır değilken hata saymaz
class Kutu:
    def __init__(self): self.yazilan = []
    def yaz(self, m, taslak=False): self.yazilan.append((m, taslak))

lp.KAYIT.temizle()
k = Kutu()
m = lp.Motor(dict(lp.VARSAYILAN), k, lambda s: None)
if not isinstance(m.cevirmen, lp.YerelCeviri): sorun.append("motor varsayılanda bilgisayar çevirisini seçmedi")
m.cevirmen = y
m.son_okunan = "Joel: He is waiting outside."
m._isle("Joel: He is waiting outside.")
if k.yazilan != [("Joel: Dışarıda bekliyor.", False)]:
    sorun.append(f"motor yanlış yazdı: {k.yazilan}")
if m.hafiza.al("Joel: He is waiting outside."):
    sorun.append("bilgisayar çevirisi hafızaya yazıldı")
if any("TASLAK" in s for s in lp.KAYIT.metin().splitlines()):
    sorun.append("bilgisayar çevirisinde taslak denendi")

hazir_degil = lp.YerelCeviri({})
m.cevirmen = hazir_degil
lp.KAYIT.temizle()
m.son_okunan = "Run to the truck."
m._isle("Run to the truck.")
if lp.KAYIT.son_hata or "BEKLİYOR" not in lp.KAYIT.metin():
    sorun.append(f"hazır değilken hata sayıldı: {lp.KAYIT.son_hata}")

# yeni_satir yolu (gerçek iş parçacıklarıyla)
m.cevirmen = y
k.yazilan.clear()
m.son_okunan = "Where are you going?"
m.yeni_satir("Where are you going?")
time.sleep(0.5)
if k.yazilan != [("Nereye gidiyorsun?", False)]:
    sorun.append(f"yeni_satir akışı yanlış: {k.yazilan}")
print("7) motor:", lp.KAYIT.metin().splitlines()[-1])

# --- 8) bağlantı seçimi yardımcıları
if lp.argos_baglantisi([{"from_code": "en", "to_code": "de", "links": ["x"]}]) is not None:
    sorun.append("yanlış dil çifti seçildi")
if lp.model_klasorunu_bul(d) != (None, None):
    sorun.append("boş klasörde model bulundu")

# --- 9) derleme sonrası model testi (EXE --model-testi)
lp.ARGOS_INDEKS = f"http://127.0.0.1:{port}/index.json"
lp._YEREL["nesne"] = None
cikti = os.path.join(d, "model-testi.txt")
if not lp.model_testi(cikti):
    sorun.append("model testi geçmedi")
icerik = open(cikti, encoding="utf-8").read()
if "SONUÇ: GEÇTİ" not in icerik or "Nereye gidiyorsun?" not in icerik:
    sorun.append(f"model testi çıktısı eksik: {icerik}")
print("9) model testi:", icerik.strip().splitlines()[-1])

# --- 10) arayüzdeki dil paketi düğmeleri: yeniden indir, dosyadan ekle
y5 = lp.YerelCeviri({})
if not y5.paketi_indir() or not y5.hazir or lp.YerelCeviri.paket_durumu()[0] is not True:
    sorun.append(f"yeniden indir çalışmadı: {y5.son_hata}")
y5.cevir("Where are you going?")
if not y5.onbellek: sorun.append("çeviri önbelleğe girmedi")
paket_yolu = os.path.join(tempfile.mkdtemp(), "secilen.argosmodel")
open(paket_yolu, "wb").write(PAKET)
if not y5.dosyadan_ekle(paket_yolu) or not y5.hazir or y5.onbellek:
    sorun.append(f"dosyadan ekle çalışmadı: {y5.son_hata}")
bozuk = os.path.join(tempfile.mkdtemp(), "bozuk.argosmodel")
open(bozuk, "wb").write(b"zip degil")
if y5.dosyadan_ekle(bozuk) or "açılamadı" not in (y5.son_hata or ""):
    sorun.append(f"bozuk dosya hatası yanlış: {y5.son_hata}")
if lp.YerelCeviri.paket_durumu()[0] is not True:
    sorun.append("bozuk dosya eski paketi sildi")
print("10) düğmeler:", y5.son_hata)

# --- 11) derlemenin dışa aktardığı paket, GitHub'ın zip'ine sarılı hâlde "Dosyadan ekle" ile kuruluyor mu
spec2 = importlib.util.spec_from_file_location("derle", os.path.join(os.path.dirname(PC_YOLU), "derle.py"))
derle = importlib.util.module_from_spec(spec2); spec2.loader.exec_module(derle)
cikis = tempfile.mkdtemp()
argos = os.path.join(cikis, "en_tr.argosmodel")
if not derle.paket_disa_aktar(lp.MODEL_DIR, argos): sorun.append("paket dışa aktarılmadı")
github_zip = os.path.join(cikis, "3b-Dil-Paketi.zip")
with zipfile.ZipFile(github_zip, "w") as z: z.write(argos, "en_tr.argosmodel")
for secilen in (argos, github_zip):
    shutil.rmtree(lp.MODEL_DIR)
    y6 = lp.YerelCeviri({})
    if not y6.dosyadan_ekle(secilen) or y6.cevir("Where are you going?") != "Nereye gidiyorsun?":
        sorun.append(f"{os.path.basename(secilen)} kurulamadı: {y6.son_hata}")
if derle.paket_disa_aktar(os.path.join(cikis, "yok"), argos): sorun.append("olmayan paket dışa aktarıldı")
print("11) dışa aktarılan paket ve GitHub zip'i kuruldu")

# --- 12) 403 ve yedek adres: hangi sunucunun reddettiği hata mesajında görünür
if "/yasak.argosmodel" not in istekler and not any("yasak" in x for x in lp.KAYIT.metin().splitlines()):
    pass
shutil.rmtree(lp.MODEL_DIR)
eski_baglantilar = lp.argos_baglantilari
lp.argos_baglantilari = lambda i: [f"http://127.0.0.1:{port}/yasak.argosmodel"]
y7 = lp.YerelCeviri({})
if y7.hazirla() or "403" not in (y7.son_hata or "") or "127.0.0.1" not in (y7.son_hata or ""):
    sorun.append(f"403 mesajı açık değil: {y7.son_hata}")
lp.argos_baglantilari = eski_baglantilar
print("12) 403:", y7.son_hata[:110])

srv.shutdown()
if sorun:
    print("BAŞARISIZ:", sorun); sys.exit(1)
print("Bilgisayar çevirisi testi geçti.")

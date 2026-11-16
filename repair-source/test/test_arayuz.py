"""Arayüz kuruluyor mu, her düğme çalışıyor mu, Gemini tamamen kalktı mı?"""
import importlib.util, json, os, sys, tempfile, time
PC_YOLU = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))
import tkinter as sahte_tk
spec = importlib.util.spec_from_file_location("lingoplay", PC_YOLU)
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)

d = tempfile.mkdtemp()
lp.APP_DIR = d
lp.CONFIG_PATH = os.path.join(d, "ayarlar.json")

motorlar = []
class SahteMotor:
    def __init__(self, *a): motorlar.append(self)
    def start(self): pass
    def dur(self): pass
lp.Motor = SahteMotor
class SahteKopru:
    def __init__(self, a): self.a = a
    def baglan(self):
        self.a["telefon_ip"] = "192.168.1.111"; return "Telefon bağlı ✓ (192.168.1.111)", True
lp.TelefonKopru = SahteKopru

def bul(metin):
    return next(b for b in reversed(sahte_tk.DUGMELER) if b.cget("text") == metin)

print("Sürüm:", lp.SURUM)
sorun = []

# ---- 1. açılış: eski sürümden Gemini ayarları kalmış, telefon kodu yok
json.dump({"ust_yuzde": 70, "alt_yuzde": 95, "tur": "Dizi", "api_key": "AIzaESKI",
           "model": "gemini-x", "kaynak": "Gemini"}, open(lp.CONFIG_PATH, "w"))
lp.main()
etiketler = [b.cget("text") for b in sahte_tk.DUGMELER]
print("Düğmeler:", etiketler)
for gerekli in ("Başlat", "Bilgisayar", "Telefonun yapay zekâsı", "Oyun", "Dizi", "Film", "Bölgeyi ekranda göster", "Telefonu bul ve bağlan",
                "Kaydı göster", "Kaydı kopyala"):
    if gerekli not in etiketler:
        sorun.append(f"'{gerekli}' düğmesi yok")
for yasak in ("Ayarlar ›", "Gemini (internet)", "Modelleri bul", "Çeviriyi test et"):
    if yasak in etiketler:
        sorun.append(f"'{yasak}' hâlâ duruyor")

if bul("Dizi").cget("bg") != lp.RENK["turkuaz"]:
    sorun.append("kayıtlı tür seçili görünmüyor")
bul("Film").invoke()
if bul("Film").cget("bg") != lp.RENK["turkuaz"] or bul("Dizi").cget("bg") == lp.RENK["turkuaz"]:
    sorun.append("tür düğmesi seçimi değişmiyor")

pencere_sayisi = len(sahte_tk.PENCERELER)
bul("Bölgeyi ekranda göster").invoke()
if len(sahte_tk.PENCERELER) != pencere_sayisi + 1:
    sorun.append("bölge çerçevesi açılmadı")

if bul("Bilgisayar").cget("bg") != lp.RENK["turkuaz"]:
    sorun.append("varsayılan çeviri kaynağı bilgisayar değil")
bul("Telefonun yapay zekâsı").invoke()
if bul("Telefonun yapay zekâsı").cget("bg") != lp.RENK["turkuaz"]:
    sorun.append("telefon seçilemedi")
ana_d = bul("Başlat")
ana_d.invoke()
if motorlar or ana_d.cget("text") != "Başlat":
    sorun.append("telefon seçiliyken kod yokken başladı")

kayit = json.load(open(lp.CONFIG_PATH))
for eski in ("api_key", "model", "kaynak"):
    if eski in kayit:
        sorun.append(f"eski '{eski}' ayarı silinmedi")
if kayit.get("tur") != "Film" or kayit.get("ust_yuzde") != 70 or kayit.get("alt_yuzde") != 95:
    sorun.append(f"ayarlar yanlış kaydedildi: {kayit}")

# ---- 2. açılış: kod kayıtlı
kayit["telefon_kod"] = "4321"
json.dump(kayit, open(lp.CONFIG_PATH, "w"))
lp.main()
bul("Telefonu bul ve bağlan").invoke()
time.sleep(0.5)   # arka plan iş parçacığı bitsin
kayit = json.load(open(lp.CONFIG_PATH))
if kayit.get("telefon_ip") != "192.168.1.111":
    sorun.append(f"bulunan adres kaydedilmedi: {kayit.get('telefon_ip')}")
ana_d = bul("Başlat")
ana_d.invoke()
if len(motorlar) != 1 or ana_d.cget("text") != "Durdur":
    sorun.append("kod varken başlamadı")
ana_d.invoke()
if ana_d.cget("text") != "Başlat":
    sorun.append("Durdur → Başlat olmadı")

# ---- 3. açılış: telefon kayıtlı seçim olarak açılıyor mu; bilgisayara geçince kodsuz başlıyor mu
kayit = json.load(open(lp.CONFIG_PATH))
if kayit.get("ceviri_kaynagi") != "telefon":
    sorun.append(f"çeviri kaynağı kaydedilmedi: {kayit.get('ceviri_kaynagi')}")
kayit["telefon_kod"] = ""
json.dump(kayit, open(lp.CONFIG_PATH, "w"))
lp.main()
if bul("Telefonun yapay zekâsı").cget("bg") != lp.RENK["turkuaz"]:
    sorun.append("kayıtlı telefon seçimi açılışta görünmüyor")
bul("Bilgisayar").invoke()
ana_d = bul("Başlat")
ana_d.invoke()
if len(motorlar) != 2 or ana_d.cget("text") != "Durdur":
    sorun.append("bilgisayar seçiliyken kodsuz başlamadı")
ana_d.invoke()
if json.load(open(lp.CONFIG_PATH)).get("ceviri_kaynagi") != "bilgisayar":
    sorun.append("bilgisayar seçimi kaydedilmedi")

# ---- dil paketi bölümü: indir / dosyadan ekle
lp.MODEL_DIR = os.path.join(d, "model_en_tr")
def sahte_model():
    os.makedirs(os.path.join(lp.MODEL_DIR, "p", "model"), exist_ok=True)
    open(os.path.join(lp.MODEL_DIR, "p", "model", "model.bin"), "wb").write(b"x" * (3 << 20))
    open(os.path.join(lp.MODEL_DIR, "p", "sentencepiece.model"), "w").write("sp")
cagrilar = []
def sahte_indir(self, yaz=lambda m: None):
    cagrilar.append("indir"); yaz("Çeviri modeli indiriliyor %50 (50 MB)"); sahte_model(); return True
def sahte_dosya(self, yol, yaz=lambda m: None):
    cagrilar.append(("dosya", yol)); sahte_model(); return True
lp.YerelCeviri.paketi_indir = sahte_indir
lp.YerelCeviri.dosyadan_ekle = sahte_dosya
import shutil; shutil.rmtree(lp.MODEL_DIR, ignore_errors=True)
lp.main()
if bul("Bilgisayar").cget("bg") != lp.RENK["turkuaz"]:
    sorun.append("bilgisayar seçimi kalıcı değil")
etiketler = [b.cget("text") for b in sahte_tk.DUGMELER]
if "Dil paketini indir" not in etiketler or "Dosyadan ekle" not in etiketler:
    sorun.append("dil paketi düğmeleri yok")
bul("Dil paketini indir").invoke()
for _ in range(40):
    if cagrilar and bul("Dosyadan ekle").cget("state") == "normal": break
    time.sleep(0.05)
if cagrilar != ["indir"]:
    sorun.append(f"indir çağrılmadı: {cagrilar}")
try:
    bul("Yeniden indir")
except StopIteration:
    sorun.append("indirince düğme 'Yeniden indir' olmadı")
if lp.YerelCeviri.paket_durumu() != (True, 3):
    sorun.append(f"paket durumu yanlış: {lp.YerelCeviri.paket_durumu()}")
import tkinter.filedialog as sahte_fd
sahte_fd.SECILECEK = "/tmp/en_tr.argosmodel"
bul("Dosyadan ekle").invoke()
time.sleep(0.3)
if ("dosya", "/tmp/en_tr.argosmodel") not in cagrilar:
    sorun.append(f"dosyadan ekleme çağrılmadı: {cagrilar}")
sahte_fd.SECILECEK = ""
n = len(cagrilar); bul("Dosyadan ekle").invoke(); time.sleep(0.1)
if len(cagrilar) != n:
    sorun.append("dosya seçilmeden ekleme yapıldı")
print("Dil paketi:", cagrilar)

# ---- Chrome çevirisi seçeneği: kodsuz başlar, yerel bağlantı açılır, seçim kaydedilir
lp.CHROME_PORT = 47391
lp._CHROME["nesne"] = None
lp.main()
bul("Chrome çevirisi").invoke()
if bul("Chrome çevirisi").cget("bg") != lp.RENK["turkuaz"]: sorun.append("Chrome çevirisi seçilemedi")
ana_d = bul("Başlat"); n0 = len(motorlar); ana_d.invoke()
if len(motorlar) != n0 + 1 or ana_d.cget("text") != "Durdur": sorun.append("Chrome seçiliyken başlamadı")
ana_d.invoke()
if json.load(open(lp.CONFIG_PATH)).get("ceviri_kaynagi") != "chrome": sorun.append("Chrome seçimi kaydedilmedi")
k = lp._CHROME["nesne"]
if not k or not k.sunucu: sorun.append("Chrome için yerel bağlantı açılmadı")
else: k.durdur()
lp.main()
if bul("Chrome çevirisi").cget("bg") != lp.RENK["turkuaz"]: sorun.append("Chrome seçimi açılışta hatırlanmadı")
bul("Bilgisayar").invoke()
if lp._CHROME["nesne"] and lp._CHROME["nesne"].sunucu: lp._CHROME["nesne"].durdur()
print("Chrome seçeneği tamam")

# ---- çalışma kaydı
lp.KAYIT.yaz("Telefona ulaşılamadı", hata=True)
lp.KAYIT.yaz("OKUNAN: Hold on")
bul("Kaydı kopyala").invoke()
pano = "".join(sahte_tk.PANO)
if "OKUNAN: Hold on" not in pano or "HATA: Telefona ulaşılamadı" not in pano:
    sorun.append(f"kayıt panoya kopyalanmadı: {pano[-120:]!r}")
pencere_sayisi = len(sahte_tk.PENCERELER)
bul("Kaydı göster").invoke()
if len(sahte_tk.PENCERELER) != pencere_sayisi + 1:
    sorun.append("kayıt penceresi açılmadı")
for metin in ("Yenile", "Kopyala", "Temizle"):
    try:
        bul(metin).invoke()
    except StopIteration:
        sorun.append(f"kayıt penceresinde '{metin}' yok")
if lp.KAYIT.metin() or lp.KAYIT.son_hata:
    sorun.append("Temizle kaydı silmedi")
if not os.path.exists(lp.KAYIT.dosya()):
    sorun.append("kayıt dosyası yazılmadı")

# ---- durum rengi
if lp.durum_rengi("Çeviri alınamadı: Telefona ulaşılamadı") != lp.RENK["kirmizi"]: sorun.append("hata rengi")
if lp.durum_rengi("OCR 140 ms · telefon 600 ms") != lp.RENK["turkuaz"]: sorun.append("çalışma rengi")
if lp.durum_rengi("Hazır.") != lp.RENK["gri"]: sorun.append("hazır rengi")

# ---- kutu: konuşan adı ayrı
k = lp.Kutu(sahte_tk.Tk())
k.yaz("JOEL: Sakin ol.")
if k.ad.cget("text").strip() != "JOEL:" or k.etiket.cget("text") != "Sakin ol.":
    sorun.append(f"konuşan adı ayrılmadı: {k.ad.cget('text')!r} {k.etiket.cget('text')!r}")
k.yaz("Sakin ol.")
if k.ad.cget("text") != "" or k.etiket.cget("text") != "Sakin ol.":
    sorun.append("adsız satır yanlış")

if sorun:
    print("BAŞARISIZ:", sorun); sys.exit(1)
print("Arayüz hatasız kuruldu, bütün düğmeler çalıştı, Gemini kalktı.")

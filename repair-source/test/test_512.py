"""PC 5.12 (6 Kasım kaydı): altyazı satırını seçme, argo sadeleştirme, uydurma sayı, gecikme."""
import importlib.util, os, sys, time, types
PC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs")); sys.path.insert(0, PC)
spec = importlib.util.spec_from_file_location("lingoplay", os.path.join(PC, "lingoplay_pc.py"))
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)
sorun = []

# --- 1) Bölge GameLingo penceresini ve YouTube yazılarını da içeriyordu (bölge %62–%94)
G = 1920
kutular = [
    (20, 40, 60, 330, "Tarama (ms) Yazi boyutu"),          # GameLingo penceresi (solda, üstte)
    (70, 90, 60, 400, "Bolgeyi ekranda goster"),
    (40, 60, 1500, 1900, "GTA6'DAN SPERMARK Bi LGi"),       # YouTube yan sütunu (sağda)
    (300, 340, 760, 1160, "Yo! Crackheads! Woo!"),          # altyazı (ortada, altta)
]
if lp.altyazi_satirlari(kutular, G) != "Yo! Crackheads! Woo!":
    sorun.append(f"altyazı seçilemedi: {lp.altyazi_satirlari(kutular, G)!r}")
# pencere ortada da olsa, altyazıdan uzakta (üstte) ise atılır
kutular2 = [(20, 40, 800, 1100, "Seriven Hul SHESUIHTOR"), (300, 340, 700, 1220, "I guess they know we're coming.")]
if lp.altyazi_satirlari(kutular2, G) != "I guess they know we're coming.":
    sorun.append(f"üstteki yazı atılmadı: {lp.altyazi_satirlari(kutular2, G)!r}")
# iki satırlı altyazı birlikte kalır, satır içi kutular soldan sağa birleşir
iki = [(300, 338, 720, 1200, "Boobie, huh? Yeah,"), (346, 384, 980, 1180, "though."), (346, 384, 740, 975, "he ain't here,")]
if lp.altyazi_satirlari(iki, G) != "Boobie, huh? Yeah, he ain't here, though.":
    sorun.append(f"iki satır birleşmedi: {lp.altyazi_satirlari(iki, G)!r}")
# kenarda tek yazı varsa (ortada hiç yok) yine okunur
if lp.altyazi_satirlari([(300, 340, 40, 300, "Hey there")], G) != "Hey there":
    sorun.append("yalnız kenar yazısı kayboldu")
print("1) satır seçimi tamam")

# --- 2) argo sadeleştirme (yalnızca bilgisayarın çevirisi için)
ARGO = {
    "Yo, what's up? Y'all tryna smoke?": "Hey, what's up? you guys trying to smoke?",
    "No. Y'all know Boobie?": "No. you guys know Boobie?",
    "Like, might have to fuck around": "Might have to mess around",
    "Like, I don't even wanna hear that shit.": "I don't even want to hear that crap.",
    "Hold up, bruh.": "Wait, brother.",
    "Be cool, don't... you know.": "Take it easy, don't... you know.",
    "Yo, what the fuck?": "Hey, what the hell?",
    "Alright, that's the block right here.": "All right, that's the block right here.",
    "Err, nothing.": "Nothing.",
    "at this point I really don't give a fuck.": "At this point I really don't care.",
    "I guess they know we're coming.": "I guess they know we're coming.",
    "Like a boss.": "Like a boss.",
}
for ham, beklenen in ARGO.items():
    if lp.argo_sadele(ham) != beklenen:
        sorun.append(f"argo: {ham!r} -> {lp.argo_sadele(ham)!r} (beklenen {beklenen!r})")
print("2) örnek:", lp.argo_sadele("Yo, what's up? Y'all tryna smoke?"))

# --- 3) uydurma sayı
if not lp.uydurma_var_mi("Yazi boyutu Yeni Bolgeyi", "Yazi +380638588488 Yeni"): sorun.append("uydurma numara yakalanmadı")
if lp.uydurma_var_mi("Call 911 now!", "Hemen 911'i ara!"): sorun.append("kaynaktaki sayı uydurma sanıldı")
if lp.uydurma_var_mi("Room 5.", "Oda 5."): sorun.append("kısa sayı uydurma sanıldı")

# --- 4) YerelCeviri: modele sadeleştirilmiş metin gider, uydurma çeviri gösterilmez, uzun satır çevrilmez
giden = []
class SP:
    def encode(self, m, out_type=str): giden.append(m); return m.split()
    def decode(self, p): return " ".join(p)
class Cev:
    def __init__(self, cikti): self.cikti = cikti
    def translate_batch(self, b, **k):
        giden.append(k.get("beam_size"))
        return [types.SimpleNamespace(hypotheses=[self.cikti.split()])]
y = lp.YerelCeviri({}); y.hazir = True; y.sp = SP(); y.cevirici = Cev("Sigara mı içmek istiyorsunuz?")
if y.cevir("Y'all tryna smoke?") != "Sigara mı içmek istiyorsunuz?": sorun.append("yerel çeviri döndürmedi")
if giden[0] != "You guys trying to smoke?" or giden[1] < 4: sorun.append(f"modele giden yanlış: {giden}")
y.cevirici = Cev("Yazi +380638588488 Yeni")
if y.cevir("Yazi boyutu Yeni") is not None or "anlamsız" not in (y.son_hata or ""): sorun.append("uydurma çeviri gösterildi")
if y.cevir("x " * 150) is not None: sorun.append("çok uzun satır çevrildi")

# --- 5) gecikme: okuma 450 ms sürerken döngü ayrıca uzun beklemiyor
import numpy as np
class YavasOCR:
    def __init__(self, **k): pass
    def __call__(self, img, **k):
        time.sleep(0.45)
        return [([(800, 10), (1100, 10), (1100, 40), (800, 40)], f"Line {time.time():.3f}", 0.9)], None
lp.RapidOCR = YavasOCR
n = {"i": 0}
class SCT:
    monitors = [None, {"left": 0, "top": 0, "width": 1920, "height": 1080}]
    def __enter__(self): return self
    def __exit__(self, *a): pass
    def grab(self, b):
        n["i"] += 1
        k = np.zeros((b["height"], b["width"], 4), dtype=np.uint8)
        k[5:30, 10:400] = 190 + (n["i"] % 2) * 60
        return k
lp.mss = types.SimpleNamespace(mss=lambda: SCT())
class Kutu:
    def yaz(self, *a, **k): pass
m = lp.Motor(dict(lp.VARSAYILAN, ceviri_kaynagi="telefon"), Kutu(), lambda s: None)
m.yeni_satir = lambda k: None
import threading
t = threading.Thread(target=m.run, daemon=True); t.start()
time.sleep(3.0); m.calisiyor = False; t.join(2)
dongu = 3.0 / max(1, m.tani.get("okuma", 0) + sum(1 for _ in []))
okuma = m.tani["okuma"] + 0
print(f"5) 3 sn'de {okuma} okuma (okuma 450 ms) → bir tur ≈ {3000 // max(okuma, 1)} ms")
if okuma < 4: sorun.append(f"döngü hâlâ uzun bekliyor: 3 sn'de {okuma} okuma")
if okuma > 7: sorun.append(f"döngü hiç dinlenmiyor: {okuma}")

if sorun:
    print("BAŞARISIZ:"); [print(" -", s) for s in sorun]; sys.exit(1)
print("5.12 testi geçti.")

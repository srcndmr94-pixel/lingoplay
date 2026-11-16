"""Uçtan uca: PC programının Chrome köprüsü (gerçek Python kodu) ↔ gerçek Chromium'daki GameLingo eklentisi 1.1.
Chrome'un çeviri modeli burada yok; eklentinin görünmez köprü sayfasına sahte çevirmen eklenir."""
import importlib.util, json, os, shutil, socket, sys, tempfile, threading, time
from playwright.sync_api import sync_playwright

KOK = os.path.dirname(os.path.abspath(__file__))
PC = os.path.join(KOK, "..", "pc")
CHROME = os.environ.get("GL_CHROME", "/opt/pw-browsers/chromium-1243/chrome-linux64/chrome")
sys.path.insert(0, os.path.join(KOK, "stubs")); sys.path.insert(0, PC)
spec = importlib.util.spec_from_file_location("lingoplay", os.path.join(PC, "lingoplay_pc.py"))
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)
sorun = []

with socket.socket() as s_:
    if s_.connect_ex(("127.0.0.1", lp.CHROME_PORT)) == 0:
        print("47330 meşgul, test atlandı"); sys.exit(0)

# Eklentinin test kopyası: köprü sayfasına sahte çevirmen
ext = tempfile.mkdtemp()
shutil.copytree(os.path.join(KOK, "..", "chrome"), ext, dirs_exist_ok=True)
shutil.copy(os.path.join(KOK, "chrome", "sahte_ceviri.js"), ext)
html = open(os.path.join(ext, "kopru.html"), encoding="utf-8").read()
open(os.path.join(ext, "kopru.html"), "w", encoding="utf-8").write(
    html.replace('<script src="kopru.js">', '<script src="sahte_ceviri.js"></script><script src="kopru.js">'))

def bekle(kosul, sure):
    son = time.time() + sure
    while time.time() < son:
        if kosul(): return True
        time.sleep(0.05)
    return kosul()

with sync_playwright() as p:
    # 1) Program önce açılır, Chrome sonra: eklenti birkaç saniyede bağlanır
    k = lp.ChromeKopru({})
    durumlar = []
    if not k.baslat(): sorun.append(f"yerel bağlantı açılmadı: {k.son_hata}")
    if k.hazir: sorun.append("Chrome açılmadan bağlı göründü")
    if k.cevir("Hello") is not None or "bağlı değil" not in (k.son_hata or ""):
        sorun.append(f"bağlı değilken mesaj yanlış: {k.son_hata}")
    ctx = p.chromium.launch_persistent_context(tempfile.mkdtemp(), executable_path=CHROME, headless=False,
        args=["--headless=new", f"--disable-extensions-except={ext}", f"--load-extension={ext}", "--no-sandbox"])
    bas = time.time()
    if not bekle(lambda: k.hazir, 10): sorun.append("eklenti PC programına bağlanmadı")
    print(f"1) eklenti {time.time() - bas:.1f} sn'de bağlandı")
    k.hazirla(durumlar.append)
    if not durumlar or "bağlı ✓" not in durumlar[-1]: sorun.append(f"hazırla durumu: {durumlar}")

    # 2) çeviri ve hız
    sureler = []
    for cumle in ["Where are you going?", "I guess they know we're coming.", "Alright, that's the block right here.",
                  "Be cool, don't... you know.", "Yo, what's up? Y'all tryna smoke?"]:
        t = k.cevir(cumle)
        sureler.append(k.son_sure)
        if t != "TR: " + cumle: sorun.append(f"çeviri yanlış: {cumle!r} -> {t!r} ({k.son_hata})")
    ort = int(sum(sureler) / len(sureler) * 1000)
    print(f"2) 5 satır, ortalama {ort} ms (en çok {int(max(sureler) * 1000)} ms)")
    if max(sureler) > 0.5: sorun.append(f"çeviri yavaş: {sureler}")

    # 3) Türkçe karakter ve tırnaklar bozulmadan gidip gelir
    t = k.cevir('He said "çok güzel" — it\'s 100% fine.')
    if t != 'TR: He said "çok güzel" — it\'s 100% fine.': sorun.append(f"karakterler bozuldu: {t!r}")

    # 4) önbellek: aynı satır Chrome'a yeniden gitmez
    t0 = time.time(); k.cevir("Where are you going?")
    if k.son_sure != 0.0 or time.time() - t0 > 0.05: sorun.append("önbellek çalışmadı")

    # 5) motor akışı: Chrome köprüsüyle satır kutuya yazılır, hafızaya yazılmaz, taslak denenmez
    class Kutu:
        def __init__(self): self.y = []
        def yaz(self, m, taslak=False): self.y.append((m, taslak))
    kutu = Kutu()
    lp.KAYIT.temizle()
    m = lp.Motor(dict(lp.VARSAYILAN, ceviri_kaynagi="chrome"), kutu, lambda s_: None)
    m.cevirmen = k
    m.son_okunan = "Joel: Run to the truck."
    m._isle("Joel: Run to the truck.")
    if kutu.y != [("Joel: TR: Run to the truck.", False)]: sorun.append(f"motor yazdı: {kutu.y}")
    if "YAZILAN · Chrome çevirisi" not in lp.KAYIT.metin(): sorun.append("kayıtta Chrome çevirisi yok")

    # 6) sıradan bir web sayfası köprüyü kullanamaz
    sayfa = ctx.new_page()
    sayfa.goto("data:text/html,<p>x</p>")
    sonuc = sayfa.evaluate("""async () => {
        const r = {};
        try { const y = await fetch('http://127.0.0.1:47330/is', {headers: {'X-GameLingo': '1'}}); r.baslikli = y.status; }
        catch (e) { r.baslikli = 'engellendi'; }
        try { const y = await fetch('http://127.0.0.1:47330/is', {mode: 'no-cors'}); r.basliksiz = y.type; }
        catch (e) { r.basliksiz = 'engellendi'; }
        return r; }""")
    if sonuc.get("baslikli") != "engellendi": sorun.append(f"web sayfası köprüye erişti: {sonuc}")
    time.sleep(0.2)
    t = k.cevir("Still working after the page tried.")
    if t != "TR: Still working after the page tried.": sorun.append(f"sayfa denemesinden sonra bozuldu: {t!r}")
    print(f"6) web sayfası: {sonuc}")

    # 7) açılır pencere bağlantıyı gösterir
    sw = ctx.service_workers[0] if ctx.service_workers else ctx.wait_for_event("serviceworker")
    ext_id = sw.url.split("/")[2]
    a = ctx.new_page(); a.goto(f"chrome-extension://{ext_id}/acilir.html")
    if not bekle(lambda: "bağlı ✓" in a.inner_text("#pc"), 4): sorun.append(f"açılır pencere: {a.inner_text('#pc')!r}")
    print(f"7) açılır pencere: {a.inner_text('#pc')!r}")

    # 8) PC programı kapanıp yeniden açılınca eklenti kendiliğinden yeniden bağlanır
    k.durdur()
    time.sleep(0.5)
    k2 = lp.ChromeKopru({})
    if not k2.baslat(): sorun.append(f"yeniden açılamadı: {k2.son_hata}")
    bas = time.time()
    if not bekle(lambda: k2.hazir, 8): sorun.append("program yeniden açılınca eklenti bağlanmadı")
    print(f"8) yeniden bağlanma {time.time() - bas:.1f} sn")
    if k2.cevir("Back again.") != "TR: Back again.": sorun.append("yeniden bağlandıktan sonra çeviri yok")

    # 9) iki program aynı anda: ikincisi açık bir hata verir
    k3 = lp.ChromeKopru({})
    if k3.baslat() or "iki kez" not in (k3.son_hata or ""): sorun.append(f"çift açılış mesajı: {k3.son_hata}")

    # 10) Chrome kapanınca: birkaç saniye içinde "bağlı değil", çeviri beklemeden None
    ctx.close()
    if not bekle(lambda: not k2.hazir, 25): sorun.append("Chrome kapandı ama bağlı görünüyor")
    t0 = time.time()
    if k2.cevir("Nobody home.") is not None or time.time() - t0 > 0.2: sorun.append("Chrome yokken çeviri bekledi")
    k2.durdur()

if sorun:
    print("BAŞARISIZ:"); [print(" -", x) for x in sorun]; sys.exit(1)
print("Chrome köprüsü uçtan uca testi geçti.")

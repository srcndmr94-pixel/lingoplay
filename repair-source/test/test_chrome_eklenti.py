"""GameLingo Tarayıcı (Chrome eklentisi): gerçek Chromium'da, YouTube/Netflix taklidi sayfalarla.
Chrome'un çeviri modeli burada yok; içerik betiğinin dünyasına sahte çevirmen eklenir."""
import http.server, json, os, shutil, sys, tempfile, threading, time
from playwright.sync_api import sync_playwright

KOK = os.path.dirname(os.path.abspath(__file__))
EKLENTI = os.path.join(KOK, "..", "chrome")
CHROME = os.environ.get("GL_CHROME", "/opt/pw-browsers/chromium-1243/chrome-linux64/chrome")
sorun = []

# Test kopyası: sahte çevirmen içerik betiğinden önce yüklenir
ext = tempfile.mkdtemp()
shutil.copytree(EKLENTI, ext, dirs_exist_ok=True)
shutil.copy(os.path.join(KOK, "chrome", "sahte_ceviri.js"), ext)
m = json.load(open(os.path.join(ext, "manifest.json"), encoding="utf-8"))
m["content_scripts"][0]["js"] = ["sahte_ceviri.js"] + m["content_scripts"][0]["js"]
json.dump(m, open(os.path.join(ext, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False)

class Sessiz(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k): super().__init__(*a, directory=os.path.join(KOK, "chrome"), **k)
    def log_message(self, *a): pass
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Sessiz)
threading.Thread(target=srv.serve_forever, daemon=True).start()
URL = f"http://127.0.0.1:{srv.server_address[1]}"

def bekle(sayfa, kosul, sure=2.0):
    son = time.time() + sure
    while time.time() < son:
        v = sayfa.evaluate(kosul)
        if v: return v
        time.sleep(0.03)
    return sayfa.evaluate(kosul)

KUTU = "(() => { const k = document.getElementById('gl-ceviri'); return k && k.classList.contains('gl-gorunur') ? k.textContent : null; })()"
YER = """(() => { const k = document.getElementById('gl-ceviri').getBoundingClientRect();
  const c = document.querySelector('.caption-window, .player-timedtext-text-container').getBoundingClientRect();
  return {kUst: k.top, kAlt: k.bottom, kOrta: (k.left + k.right) / 2, cUst: c.top, cAlt: c.bottom, cOrta: (c.left + c.right) / 2,
          yazi: parseFloat(getComputedStyle(document.getElementById('gl-ceviri')).fontSize),
          arka: getComputedStyle(document.getElementById('gl-ceviri')).backgroundColor}; })()"""
CAGRI = "Number(document.documentElement.getAttribute('data-gl-cagri') || 0)"

with sync_playwright() as p:
    ctx = p.chromium.launch_persistent_context(tempfile.mkdtemp(), executable_path=CHROME, headless=False,
        args=["--headless=new", f"--disable-extensions-except={ext}", f"--load-extension={ext}", "--no-sandbox"],
        viewport={"width": 1400, "height": 900})
    hatalar = []
    sw = ctx.service_workers[0] if ctx.service_workers else ctx.wait_for_event("serviceworker", timeout=10000)
    ext_id = sw.url.split("/")[2]

    # ---- 1) YouTube: çeviri İngilizcenin hemen altında, ortalı, siyah bant
    s = ctx.new_page(); s.on("pageerror", lambda e: hatalar.append(str(e)))
    s.on("console", lambda c: hatalar.append(c.text) if c.type == "error" else None)
    s.goto(URL + "/youtube.html?mod=hazir"); time.sleep(0.4)
    bas = time.time()
    s.evaluate("altyazi('Where are you going?')")
    metin = bekle(s, KUTU)
    gecikme = int((time.time() - bas) * 1000)
    if metin != "TR: Where are you going?": sorun.append(f"çeviri görünmedi: {metin!r}")
    y = s.evaluate(YER)
    if not (y["cAlt"] <= y["kUst"] <= y["cAlt"] + 10): sorun.append(f"kutu İngilizcenin altında değil: {y}")
    if abs(y["kOrta"] - y["cOrta"]) > 3: sorun.append(f"kutu ortalı değil: {y}")
    if y["arka"] not in ("rgb(0, 0, 0)", "rgba(0, 0, 0, 1)"): sorun.append(f"arka plan siyah değil: {y['arka']}")
    print(f"1) YouTube: {metin!r} · {gecikme} ms · kutu üstü {y['kUst']:.0f}, İngilizce altı {y['cAlt']:.0f} · yazı {y['yazi']:.0f}px")

    # ---- 2) satır değişince hızlı güncelleme
    bas = time.time()
    s.evaluate("altyazi('I guess they know we\\'re coming.')")
    metin = bekle(s, KUTU.replace("k.textContent : null", "(k.textContent.includes('coming') ? k.textContent : null) : null"))
    gecikme2 = int((time.time() - bas) * 1000)
    if not metin or "coming" not in metin: sorun.append(f"yeni satır gelmedi: {metin!r}")
    if gecikme2 > 500: sorun.append(f"güncelleme yavaş: {gecikme2} ms")
    print(f"2) yeni satır {gecikme2} ms: {metin!r}")

    # ---- 3) art arda satırlar: en sonuncusu kalır, araya girenler boşa çevrilmez
    once = s.evaluate(CAGRI)
    for i in range(6):
        s.evaluate(f"altyazi('Line number {i} here')"); time.sleep(0.02)
    time.sleep(0.8)
    son = s.evaluate(KUTU); cagri = s.evaluate(CAGRI) - once
    if son != "TR: Line number 5 here": sorun.append(f"son satır gösterilmedi: {son!r}")
    if cagri > 4: sorun.append(f"araya giren satırlar da çevrildi: {cagri} çağrı")
    print(f"3) 6 hızlı satır → {cagri} çeviri, ekranda: {son!r}")

    # ---- 4) önbellek: tekrar eden satır yeniden çevrilmez
    s.evaluate("altyazi('Where are you going?')"); time.sleep(0.4)
    once = s.evaluate(CAGRI)
    s.evaluate("altyazi('Hold on.')"); time.sleep(0.4)
    s.evaluate("altyazi('Where are you going?')"); time.sleep(0.4)
    if s.evaluate(CAGRI) - once != 1: sorun.append(f"önbellek çalışmıyor: {s.evaluate(CAGRI) - once}")
    if s.evaluate(KUTU) != "TR: Where are you going?": sorun.append("önbellekten gösterilmedi")

    # ---- 5) iki satırlı altyazı tek cümle olarak çevrilir
    s.evaluate("altyazi('Boobie, huh? Yeah,\\nhe ain\\'t here, though.')")
    metin = bekle(s, KUTU.replace("k.textContent : null", "(k.textContent.includes('though') ? k.textContent : null) : null"))
    if metin != "TR: Boobie, huh? Yeah, he ain't here, though.": sorun.append(f"iki satır birleşmedi: {metin!r}")

    # ---- 6) altyazı kalkınca çeviri de kalkar
    s.evaluate("altyazi('')")
    if bekle(s, KUTU + " === null", 1.0) is not True: sorun.append("altyazı gidince çeviri kalmadı mı? hâlâ görünüyor")

    # ---- 7) İngilizce en alttaysa çeviri üstüne geçer (dışarı taşmaz)
    s.evaluate("altyazi('At the very bottom.', 0)")
    bekle(s, KUTU)
    y = s.evaluate(YER)
    if y["kAlt"] > y["cUst"] + 1 or y["kAlt"] > 720: sorun.append(f"altta sığmayınca üste geçmedi: {y}")

    # ---- 8) oynatıcı küçülünce yazı küçülür
    s.evaluate("document.getElementById('movie_player').style.cssText='width:640px;height:360px'")
    s.evaluate("altyazi('Small player now.')"); bekle(s, KUTU.replace("k.textContent : null", "(k.textContent.includes('Small') ? k.textContent : null) : null"))
    kucuk = s.evaluate(YER)["yazi"]
    if not (kucuk < y["yazi"]): sorun.append(f"küçük oynatıcıda yazı küçülmedi: {kucuk} / {y['yazi']}")

    # ---- 9) açılır pencere: İngilizceyi gizle ve yazı boyu
    s.evaluate("document.getElementById('movie_player').style.cssText=''")
    s.evaluate("altyazi('Hide the English.')"); bekle(s, KUTU)
    a = ctx.new_page(); a.on("pageerror", lambda e: hatalar.append("açılır: " + str(e)))
    a.goto(f"chrome-extension://{ext_id}/acilir.html"); time.sleep(0.5)
    a.check("#ingilizceGizle"); time.sleep(0.5)
    durum_metni = a.inner_text("#durum")
    s.bring_to_front(); time.sleep(0.4)
    gizli = s.evaluate("document.querySelector('.caption-window').classList.contains('gl-ingilizce-gizli') && getComputedStyle(document.querySelector('.caption-window')).visibility")
    y = s.evaluate(YER)
    if gizli != "hidden": sorun.append(f"İngilizce gizlenmedi: {gizli}")
    if abs(y["kUst"] - y["cUst"]) > 3: sorun.append(f"gizliyken çeviri İngilizcenin yerine geçmedi: {y}")
    a.uncheck("#ingilizceGizle"); a.fill("#boyut", "150"); a.dispatch_event("#boyut", "input"); time.sleep(0.5)
    s.evaluate("altyazi('Bigger text please.')"); bekle(s, KUTU.replace("k.textContent : null", "(k.textContent.includes('Bigger') ? k.textContent : null) : null"))
    y2 = s.evaluate(YER)
    if s.evaluate("getComputedStyle(document.querySelector('.caption-window')).visibility") != "visible": sorun.append("İngilizce geri gelmedi")
    if not (y2["yazi"] > y["yazi"] * 1.3): sorun.append(f"yazı büyümedi: {y2['yazi']} / {y['yazi']}")
    a.uncheck("#acik"); time.sleep(0.5)
    if s.evaluate(KUTU) is not None: sorun.append("çeviri kapatılınca gizlenmedi")
    a.check("#acik"); a.fill("#boyut", "100"); a.dispatch_event("#boyut", "input"); time.sleep(0.3)
    print(f"9) açılır pencere tamam · durum: {durum_metni!r}")

    # ---- 10) Netflix
    n = ctx.new_page(); n.on("pageerror", lambda e: hatalar.append(str(e)))
    n.goto(URL + "/netflix.html?mod=hazir"); time.sleep(0.3)
    n.evaluate("altyazi('Say my name.')")
    metin = bekle(n, KUTU); y = n.evaluate(YER)
    if metin != "TR: Say my name." or not (y["cAlt"] <= y["kUst"] <= y["cAlt"] + 10): sorun.append(f"Netflix: {metin!r} {y}")
    print(f"10) Netflix: {metin!r}")

    # ---- 11) ilk sefer: model indirilmemiş → tıkla düğmesi → indirir, çevirir
    d = ctx.new_page(); d.on("pageerror", lambda e: hatalar.append(str(e)))
    d.goto(URL + "/youtube.html?mod=indir"); time.sleep(0.3)
    d.evaluate("altyazi('First time here.')")
    dugme = bekle(d, "(() => { const k = document.getElementById('gl-ceviri'); return k && k.classList.contains('gl-dugme') ? k.textContent : null; })()")
    if not dugme or "tıklayın" not in dugme: sorun.append(f"indirme düğmesi çıkmadı: {dugme!r}")
    else:
        d.click("#gl-ceviri"); time.sleep(0.2)
        d.evaluate("altyazi('After the download.')")
        metin = bekle(d, KUTU.replace("k.textContent : null", "(k.textContent.includes('After') ? k.textContent : null) : null"), 3)
        if metin != "TR: After the download.": sorun.append(f"tıkladıktan sonra çeviri gelmedi: {metin!r}")
    print(f"11) ilk sefer: düğme {dugme!r} → {d.evaluate(KUTU)!r}")

    # ---- 12) yerleşik çeviri olmayan tarayıcı: açık uyarı
    u = ctx.new_page(); u.on("pageerror", lambda e: hatalar.append(str(e)))
    u.goto(URL + "/youtube.html?mod=yok"); time.sleep(0.3)
    u.evaluate("altyazi('No translator here.')")
    uyari = bekle(u, KUTU)
    if not uyari or "Chrome 138" not in uyari: sorun.append(f"desteklenmiyor uyarısı yok: {uyari!r}")
    print(f"12) desteklenmiyor: {uyari!r}")

    # ---- 13) altyazısız sıradan sayfa: hiçbir şey eklenmez
    b = ctx.new_page(); b.goto(URL + "/netflix.html?mod=hazir"); time.sleep(0.6)
    if b.evaluate("!!document.getElementById('gl-ceviri')"): sorun.append("altyazısız sayfaya kutu eklendi")

    ilgili = [h for h in hatalar if "favicon" not in h and "Failed to load resource" not in h]
    if ilgili: sorun.append(f"sayfa hataları: {ilgili}")
    ctx.close()
srv.shutdown()

if sorun:
    print("BAŞARISIZ:"); [print(" -", x) for x in sorun]; sys.exit(1)
print("Chrome eklentisi testi geçti.")

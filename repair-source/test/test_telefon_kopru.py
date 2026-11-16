"""PC'nin telefon köprüsü istemcisi: telefondaki protokolle konuşuyor mu?
Telefon tarafı olarak GERÇEK Kotlin kodu (PhoneBridge.serveOne) çalıştırılır
(KOPRU_SUNUCU_CP ortam değişkeni verilmişse); yoksa aynı protokolü konuşan küçük bir sahte sunucu."""
import importlib.util, os, socket, subprocess, sys, threading, time
PC_YOLU = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))
spec = importlib.util.spec_from_file_location("lingoplay", PC_YOLU)
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)

surec = None
cp = os.environ.get("KOPRU_SUNUCU_CP")
if cp:
    surec = subprocess.Popen(["java", "-cp", cp, "SunucuKt"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    while "HAZIR" not in (surec.stdout.readline() or "HAZIR"):
        pass
    print("Telefon tarafı: gerçek Kotlin PhoneBridge")
else:
    def sahte():
        ss = socket.socket(); ss.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        ss.bind(("0.0.0.0", 47321)); ss.listen(5)
        u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); u.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        u.bind(("0.0.0.0", 47322))
        def udp():
            while True:
                d, a = u.recvfrom(128)
                if d.decode().strip() == "GLFIND 4321": u.sendto(b"GLHERE", a)
        threading.Thread(target=udp, daemon=True).start()
        while True:
            c, _ = ss.accept()
            f = c.makefile("r", encoding="utf-8")
            bas = f.readline().split()
            if bas[1] != "4321": c.sendall("ERR kod yanlış\n".encode()); c.close(); continue
            if bas[2] == "PING": c.sendall(b"PONG 1\n"); c.close(); continue
            n = int(bas[3]); tur = f.readline().strip(); ad = f.readline().strip(); satir = f.readline().strip()
            onceki = [f.readline() for _ in range(n)]
            if len(bas) > 4 and bas[4] == "Q": c.sendall(f"OK hızlı: {satir}\n".encode())
            elif "waiting" in satir: c.sendall(f"OK Dışarıda bekliyor. [tür={tur} ad={ad} önceki={n}]\n".encode())
            elif "skipme" in satir: c.sendall(b"SKIP\n")
            else: c.sendall("ERR model meşgul\n".encode())
            c.close()
    threading.Thread(target=sahte, daemon=True).start()
    time.sleep(0.3)
    print("Telefon tarafı: sahte sunucu")

sorun = []
try:
    ay = dict(lp.VARSAYILAN, ceviri_kaynagi="telefon", telefon_kod="4321", telefon_ip="", tur="Oyun", oyun="The Last of Us")
    k = lp.cevirmen_olustur(ay)
    if not isinstance(k, lp.TelefonKopru): sorun.append("kaynak Telefon iken köprü seçilmedi")
    metin, hazir = k.baglan()
    print("Bağlan:", metin)
    if not hazir: sorun.append(f"telefon bulunamadı: {metin}")
    if not ay.get("telefon_ip"): ay["telefon_ip"] = "127.0.0.1"
    c1 = k.cevir("He is waiting outside.")
    print("Çeviri 1:", c1)
    if not c1 or not c1.startswith("Dışarıda bekliyor.") or "ad=The Last of Us" not in c1 or "önceki=0" not in c1:
        sorun.append(f"çeviri/alanlar yanlış: {c1}")
    t0 = time.time(); tq = k.taslak("He is waiting outside.")
    print("Taslak:", tq, f"{int((time.time()-t0)*1000)} ms")
    if tq != "hızlı: He is waiting outside.": sorun.append(f"hızlı taslak gelmedi: {tq}")
    k.cevir("skipme"); 
    if not k.atlandi or k.son_hata: sorun.append("SKIP sessizce atlanmadı")
    c3 = k.cevir("He is waiting outside again.")
    if not c3 or "önceki=2" not in c3: sorun.append(f"önceki satırlar gitmedi: {c3}")
    if k.cevir("unknown") is not None or "meşgul" not in (k.son_hata or ""): sorun.append(f"hata iletilmedi: {k.son_hata}")
    yanlis = lp.TelefonKopru(dict(ay, telefon_kod="9999", telefon_ip="127.0.0.1"))
    if yanlis.cevir("He is waiting.") is not None or "yanlış" not in (yanlis.son_hata or ""): sorun.append(f"yanlış kod: {yanlis.son_hata}")
    kodsuz = lp.TelefonKopru(dict(ay, telefon_kod=""))
    if kodsuz.cevir("x") is not None or "kodu" not in (kodsuz.son_hata or ""): sorun.append("kodsuz durum")
    if not isinstance(lp.cevirmen_olustur(dict(lp.VARSAYILAN)), lp.YerelCeviri): sorun.append("varsayılan bilgisayar değil")
finally:
    if surec: surec.kill()

if sorun: print("BAŞARISIZ:", sorun); sys.exit(1)
print("Telefon köprüsü testi geçti.")

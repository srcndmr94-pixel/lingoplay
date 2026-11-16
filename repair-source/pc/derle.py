"""GameLingo PC'yi derler (GitHub Actions, Windows). Derleme adımları zip'in içinde durur:
PC tarafında yeni bir paket gerekirse yalnızca bu dosya ve gereksinimler.txt değişir,
GitHub'daki build.yml'ye dokunmak gerekmez.

  python kaynak/pc/derle.py        -> dist/LingoPlay.exe + dist/model-testi.txt
"""
import os
import subprocess
import sys

PC = os.path.dirname(os.path.abspath(__file__))
DIST = os.path.abspath("dist")


# GitHub'ın Windows ekranı cp1252: Türkçe harfler (İ, ı, ş, ğ) basılamayıp program çöküyordu.
for akis in (sys.stdout, sys.stderr):
    try:
        akis.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def calistir(komut, **k):
    print(">", " ".join(komut), flush=True)
    return subprocess.run(komut, **k).returncode


def main():
    # 1) Paketler
    if calistir([sys.executable, "-m", "pip", "install", "-r", os.path.join(PC, "gereksinimler.txt")]) != 0:
        sys.exit("Paketler kurulamadı")

    # 1b) Windows OCR (isteğe bağlı: kurulamazsa program RapidOCR ile çalışır)
    calistir([sys.executable, "-m", "pip", "install", "-r", os.path.join(PC, "gereksinimler-windows-ocr.txt")])
    winrt_var = calistir([sys.executable, "-c", "import winrt.windows.media.ocr, winrt.windows.graphics.imaging, "
                          "winrt.windows.storage.streams, winrt.windows.globalization; print('winrt tamam')"]) == 0

    # 2) Kurulan çeviri motoru gerçekten açılıyor mu (EXE'den önce, açık hata mesajı için)
    calistir([sys.executable, "-c", "import ctranslate2, sentencepiece; print('ctranslate2', ctranslate2.__version__)"])

    # 3) EXE
    komut = [sys.executable, "-m", "PyInstaller", "--noconfirm", "--onefile", "--noconsole", "--name", "LingoPlay",
             "--paths", PC]
    for paket in ("rapidocr_onnxruntime", "onnxruntime", "ctranslate2", "sentencepiece") + (("winrt",) if winrt_var else ()):
        komut += ["--collect-all", paket]
    if winrt_var:
        for m in ("winrt.windows.media.ocr", "winrt.windows.globalization", "winrt.windows.graphics.imaging",
                  "winrt.windows.storage.streams", "winrt.windows.foundation", "winrt.windows.foundation.collections"):
            komut += ["--hidden-import", m]
    komut += ["--hidden-import", "ctranslate2", "--hidden-import", "sentencepiece", "--hidden-import", "kelimeler",
              os.path.join(PC, "lingoplay_pc.py")]
    if calistir(komut) != 0:
        sys.exit("EXE derlenemedi")
    exe = os.path.join(DIST, "LingoPlay.exe")
    if not os.path.exists(exe):
        sys.exit("dist/LingoPlay.exe yok")

    # 4) EXE'nin içindeki çeviri motorunu dene: modeli indirip birkaç cümle çevirir.
    #    Sonuç model-testi.txt'ye yazılır; başarısız olsa da EXE yine yüklenir.
    #    Bu adım ne olursa olsun derlemeyi düşürmez.
    rapor = os.path.join(DIST, "model-testi.txt")
    try:
        try:
            kod = subprocess.run([exe, "--model-testi", rapor], timeout=600).returncode
        except subprocess.TimeoutExpired:
            kod = "zaman aşımı"
        if not os.path.exists(rapor):
            with open(rapor, "w", encoding="utf-8") as f:
                f.write(f"model-testi.txt yazılmadı (çıkış: {kod})\n")
        # Okuma testi: Windows OCR ve RapidOCR'ın bu makinedeki hızı (aynı dosyaya eklenir)
        try:
            subprocess.run([exe, "--ocr-testi", rapor], timeout=300)
        except subprocess.TimeoutExpired:
            with open(rapor, "a", encoding="utf-8") as f:
                f.write("okuma testi zaman aşımı\n")
        print(open(rapor, encoding="utf-8").read(), flush=True)
    except Exception as e:
        try:
            with open(rapor, "a", encoding="utf-8") as f:
                f.write(f"model testi çalıştırılamadı: {type(e).__name__}: {e}\n")
        except Exception:
            pass

    # 5) Model testinin indirdiği dil paketini ayrı dosya olarak koy (ayrı çıktı: "Dil-Paketi").
    #    Bilgisayarda: GameLingo → Bilgisayar (internetsiz) → Dosyadan ekle.
    try:
        paket_disa_aktar(os.path.join(os.path.expanduser("~"), ".lingoplay", "model_en_tr"),
                         os.path.join(DIST, "paket", "en_tr.argosmodel"))
    except Exception as e:
        print(f"Dil paketi dışa aktarılamadı: {type(e).__name__}: {e}", flush=True)


def paket_disa_aktar(kaynak, hedef):
    """Açılmış modeli tekrar .argosmodel (zip) dosyasına koyar."""
    import zipfile
    if not os.path.isdir(kaynak):
        print("Dil paketi yok (model testi indirmemiş).", flush=True)
        return False
    os.makedirs(os.path.dirname(hedef), exist_ok=True)
    with zipfile.ZipFile(hedef, "w", zipfile.ZIP_DEFLATED, compresslevel=1) as z:
        for yol, _, dosyalar in os.walk(kaynak):
            for ad in dosyalar:
                tam = os.path.join(yol, ad)
                z.write(tam, os.path.relpath(tam, kaynak))
    print(f"Dil paketi: {hedef} ({os.path.getsize(hedef) // (1 << 20)} MB)", flush=True)
    return True


if __name__ == "__main__":
    main()

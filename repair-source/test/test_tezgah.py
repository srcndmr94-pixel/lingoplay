"""
UÇTAN UCA TEZGÂH. Kullanıcının 26 dakikalık kaydındaki 78 repliği gerçek
taslak yolundan geçirip beklenen Türkçeyle karşılaştırır.

NEDEN JAVA: Kotlin derleyicisi burada yok, ama Android'in regex motoru
Java'nın motorudur. Kuralları Python'a çevirmek iki kez hata üretti:
  * Python değişken genişlikli lookbehind'ı desteklemiyor (SlangNormalizer
    kurallarının yarısı derlenmiyor).
  * Java'da "(?i)" \\p{Lu}'yu da duyarsız yapıyor; Python'da yapmıyor.
Bu yüzden Tezgah.java kuralları KOTLIN DOSYALARINDAN ÇALIŞMA ANINDA okuyor;
elle kopya kural tutulmuyor. Elle kopya tutan eski aynalar iki kez bayatladı
(y'all kuralı değişti, aynalar eski değeri doğru sanmaya devam etti).

İki şeyi birlikte sınıyor:
  1. --denetim     : AiGuard.hitapDuzelt + CaptureService kırıntı kuralı
  2. =beklenen.tsv : 78 repliğin tamamının çevirisi
  3. Ses.java      : ses etiketi katmanı (SoundTags) — kırpılan etiketler ve
                     OCR'ın bozduğu köşeli parantezler
  4. Kalkan.java   : özel ad kalkanı (NameShield) — adlar korunuyor mu,
                     gerçek İngilizce kelimeler kalkanlanmıyor mu
  5. Suslu.java    : süslü yazı tipi çöpü — kırpılıyor mu, gerçek replikler
                     yanlışlıkla çöp sayılıyor mu
  6. Onarim.java   : OCR onarımları + GERÇEK KAYIT KORPUSU üzerinde iki
                     özellik: onar(onar(x))==onar(x) ve temiz Türkçeye
                     dokunmama — nota işareti, kırık konuşmacı
                     parantezi, bölünmüş kelime, düşen kesme, çöp ön ek
  7. Defter.java   : ad defteri (AdDefteri) — adlar altyazı akışından
                     öğreniliyor mu, unvan/dil kelimeleri çevrilmeye devam
                     ediyor mu, OCR büyük harf gürültüsü ad sanılıyor mu
"""

import os
import shutil
import subprocess
import sys
import tempfile

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
TEZGAH = os.path.join(KOK, "Tezgah.java")
SES = os.path.join(KOK, "Ses.java")
KALKAN = os.path.join(KOK, "Kalkan.java")
SUSLU = os.path.join(KOK, "Suslu.java")
ONARIM = os.path.join(KOK, "Onarim.java")
DEFTER = os.path.join(KOK, "Defter.java")
GZ = os.path.join(KOK, "..", "app", "src", "main", "res", "raw", "kelimeler.gz")
BEKLENEN = os.path.join(KOK, "kayit_beklenen.tsv")

for yol in (TEZGAH, SES, KALKAN, SUSLU, ONARIM, DEFTER, BEKLENEN):
    if not os.path.exists(yol):
        print("BAŞARISIZ: eksik dosya " + yol)
        sys.exit(1)

if not shutil.which("javac") or not shutil.which("java"):
    # Android derlemesi JDK istiyor; JDK'sız ortamda bu test atlanıyor ama
    # sessizce geçmiyor, ekrana yazıyor.
    print("ATLANDI: JDK yok (javac/java bulunamadı). Tezgâh çalıştırılamadı.")
    sys.exit(0)

gecici = tempfile.mkdtemp(prefix="tezgah")
try:
    d = subprocess.run(["javac", "-encoding", "UTF-8", "-d", gecici, TEZGAH, SES, KALKAN, SUSLU, ONARIM, DEFTER],
                       capture_output=True, text=True)
    # Uyarılar (deprecation) sorun değil; yalnız çıkış kodu önemli.
    if d.returncode:
        print("BAŞARISIZ: Tezgah.java derlenmedi\n" + d.stderr)
        sys.exit(1)

    ortam = dict(os.environ, JAVA_TOOL_OPTIONS="")
    hata = 0
    for sinif, arg in (("Tezgah", "--denetim"), ("Tezgah", "=" + BEKLENEN),
                       ("Ses", ""), ("Kalkan", GZ), ("Suslu", GZ + "|" + KOK),
                       ("Onarim", GZ + "|" + KOK), ("Defter", "")):
        komut = ["java", "-cp", gecici, "-Dfile.encoding=UTF-8",
                 "-Dstdout.encoding=UTF-8", sinif, KAYNAK]
        if arg:
            komut.extend(arg.split("|"))
        s = subprocess.run(komut,
                           capture_output=True, text=True, env=ortam)
        cikti = (s.stdout + s.stderr).strip()
        print(cikti)
        if s.returncode:
            hata += 1
    if hata:
        print("BAŞARISIZ: tezgâh %d bölümde düştü" % hata)
        sys.exit(1)
finally:
    shutil.rmtree(gecici, ignore_errors=True)

print("Tezgâh testi geçti.")

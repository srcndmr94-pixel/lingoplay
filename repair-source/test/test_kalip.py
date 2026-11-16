#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BÜTÜN Regex LİTERALLERİ DERLENİYOR MU?

Bozuk bir regex, Kotlin'de derleme hatası DEĞİL: Regex("...") çalışma anında
kuruluyor. Bozuksa sınıf ilk kullanıldığında PatternSyntaxException atıyor —
yani uygulama altyazı çevirmeye başladığı anda ya da açılışta çöküyor.
Kotlin derleyicisi olmadan bu sınıf hatayı başka hiçbir testim yakalamıyor.

Bu test kaynaktaki her Regex("...") dizgisini çıkarıp java.util.regex ile
derliyor (Kotlin Regex zaten onun üstünde çalışıyor).

İçinde Kotlin şablonu (${...}) olan kalıplar ATLANIYOR: onların son hâli
ancak çalışma anında belli olur, statik derlenemez.
"""

import base64
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

KACIS = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "$": "$"}


def coz(ham):
    """Kotlin dizgi kaçışlarını gerçek karaktere çevirir."""
    return re.sub(r"\\(.)", lambda m: KACIS.get(m.group(1), "\\" + m.group(1)), ham)


kalipLar = []
sablonlu = []
atlanan = 0
for yol in sorted(glob.glob(os.path.join(KAYNAK, "*.kt"))):
    with open(yol, encoding="utf-8") as f:
        kaynak = f.read()
    # Yorum satırları atılıyor: içindeki örnek kalıplar gerçek kod değil.
    # Yorumlar atılıyor ama ORİJİNAL satır numarası korunuyor: yoksa
    # bildirilen numara kayıyor ve liste işe yaramıyor.
    satirlar = kaynak.splitlines()
    tutulan = [(i + 1, l) for i, l in enumerate(satirlar)
               if not l.strip().startswith("//")]
    temiz = "\n".join(l for _, l in tutulan)
    gercekSatir = [n for n, _ in tutulan]
    # Parantez DENGELI okunuyor. Eski hâl "Regex(\"(?i)\" + it.first)"
    # çağrısında yalnız "(?i)" kısmını alıyordu; bu geçerli bir regex
    # olduğu için test sessizce HİÇBİR ŞEY doğrulamıyordu.
    for m in re.finditer(r"Regex\(", temiz):
        j = m.end() - 1
        d, son, dizgide = 0, -1, False
        k = j
        while k < len(temiz):
            c = temiz[k]
            if dizgide:
                if c == "\\":
                    k += 2
                    continue
                if c == '"':
                    dizgide = False
            elif c == '"':
                dizgide = True
            elif c == "(":
                d += 1
            elif c == ")":
                d -= 1
                if d == 0:
                    son = k
                    break
            k += 1
        if son < 0:
            continue
        arg = temiz[j + 1:son]
        parcalar = re.findall(r'"((?:[^"\\]|\\.)*)"', arg)
        pat = coz("".join(parcalar))
        kalan = re.sub(r'"(?:[^"\\]|\\.)*"', "", arg)
        for bayrak in ("RegexOption.IGNORE_CASE", "RegexOption.DOT_MATCHES_ALL",
                       "RegexOption.MULTILINE", "setOf", ","):
            kalan = kalan.replace(bayrak, "")
        ix = temiz[:m.start()].count("\n")
        satir = gercekSatir[ix] if ix < len(gercekSatir) else ix + 1
        if re.search(r"[A-Za-z_$]", kalan):
            atlanan += 1
            sablonlu.append("%s:%d" % (os.path.basename(yol), satir))
            continue
        # Kotlin şablonu: "${...}" ve süssüz "$AD" biçimi de çalışma anında
        # çözülür, statik derlenemez. Süssüz biçim regex olarak DERLENİR ama
        # anlamsızdır ($ = satır sonu) — yani test sessizce hiçbir şey
        # doğrulamaz. İkisini de ayırıyoruz.
        if "${" in pat or re.search(r"\$[A-Za-z_]", pat):
            atlanan += 1
            sablonlu.append("%s:%d" % (os.path.basename(yol), satir))
            continue
        kalipLar.append((os.path.basename(yol), satir, pat))

if not kalipLar:
    print("BAŞARISIZ: hiç Regex literali bulunamadı — çıkarıcı bozulmuş olabilir.")
    sys.exit(1)

# --- ÖNCE GERÇEK MOTOR: ICU -------------------------------------------------
# Android'de java.util.regex ICU'dur. Masaüstü Java'da derlenen bir kalıp
# Android'de derlenmeyebilir: "(?iU)" Java'da geçerli, ICU'da
# U_REGEX_INVALID_FLAG. 9.78-9.82 bu yüzden açılışta çöktü ve bu test
# yalnız Java'ya baktığı için "geçti" dedi.
sys.path.insert(0, KOK)
try:
    import icu_regex
except Exception:
    icu_regex = None

if icu_regex is not None:
    icuBozuk = []
    for dosya, satir, pat in kalipLar:
        _, e = icu_regex.derle(pat)
        if e:
            icuBozuk.append("%s:%d  %s\n       %s" % (dosya, satir, e, pat[:100]))
    if icuBozuk:
        print("BAŞARISIZ: %d kalıp ANDROID motorunda (ICU) derlenmiyor — "
              "uygulama açılışta çöker:" % len(icuBozuk))
        for b in icuBozuk:
            print("  - " + b)
        sys.exit(1)
    icuNotu = "ICU (Android motoru) + "
else:
    icuNotu = "UYARI: ICU yok, yalnız "

if not shutil.which("javac") or not shutil.which("java"):
    print("%sJava yok. %d kalıp denetlendi." % (icuNotu, len(kalipLar)))
    sys.exit(0)

gecici = tempfile.mkdtemp(prefix="kalip")
try:
    liste = os.path.join(gecici, "liste.txt")
    with open(liste, "w", encoding="utf-8") as f:
        for dosya, satir, pat in kalipLar:
            b64 = base64.b64encode(pat.encode("utf-8")).decode()
            f.write("%s|%d|%s\n" % (dosya, satir, b64))

    kod = """
import java.nio.file.*; import java.util.*; import java.util.regex.*;
public class Kalip {
    public static void main(String[] a) throws Exception {
        List<String> satirlar = Files.readAllLines(Paths.get(a[0]));
        int n = 0, bozuk = 0;
        for (String l : satirlar) {
            if (l.trim().isEmpty()) continue;
            String[] p = l.split("\\\\|", 3);
            if (p.length < 3) continue;
            String pat = new String(Base64.getDecoder().decode(p[2]), "UTF-8");
            n++;
            try { Pattern.compile(pat); }
            catch (PatternSyntaxException e) {
                bozuk++;
                System.out.println("  BOZUK " + p[0] + ":" + p[1]
                    + "  -> " + e.getDescription() + " @" + e.getIndex());
                System.out.println("     " + (pat.length() > 110 ? pat.substring(0,110) : pat));
            }
        }
        System.out.println("SAYI " + n + " BOZUK " + bozuk);
        if (bozuk > 0) System.exit(1);
    }
}
"""
    kjava = os.path.join(gecici, "Kalip.java")
    with open(kjava, "w", encoding="utf-8") as f:
        f.write(kod)

    d = subprocess.run(["javac", "-encoding", "UTF-8", "-d", gecici, kjava],
                       capture_output=True, text=True)
    if d.returncode:
        print("BAŞARISIZ: denetleyici derlenmedi\n" + d.stderr)
        sys.exit(1)

    ortam = dict(os.environ, JAVA_TOOL_OPTIONS="")
    s = subprocess.run(["java", "-cp", gecici, "-Dfile.encoding=UTF-8",
                        "-Dstdout.encoding=UTF-8", "Kalip", liste],
                       capture_output=True, text=True, env=ortam)
    cikti = (s.stdout + s.stderr).strip()
    if s.returncode:
        print("BAŞARISIZ: bozuk regex var (uygulama çalışma anında çökerdi)")
        print(cikti)
        sys.exit(1)
finally:
    shutil.rmtree(gecici, ignore_errors=True)

print("Kalıp testi geçti [" + icuNotu + "masaüstü Java]: %d regex literali derlendi, %d şablonlu kalıp atlandı%s."
      % (len(kalipLar), atlanan, (" (" + ", ".join(sablonlu) + ")") if sablonlu else ""))

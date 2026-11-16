#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KALIPLARI JAVA'DA ÇALIŞTIRAN YARDIMCI.

Neden var: Kotlin Regex java.util.regex üstünde çalışır ve orada "\\b" ile
"\\w" VARSAYILAN olarak yalnız ASCII'dir — Türkçe harfler (ğ ı ö ş ü ç)
kelime sınırı sayılır. Python'da ise "\\b" Unicode farkındadır.

Bu fark testleri YALANCI yapıyordu. 11 Kasım'da üç ürün hatası Python
testlerinden geçip gitti:
    "\\bgöt\\b"              -> Java'da "götürdü" İÇİNDE eşleşiyor
    "^(lan|ulan|be|..)\\b"  -> Java'da "Beğendim" içinde eşleşiyor
    "\\w{2,}m[ae]z"         -> Java'da "ölmez", "görmedi" KAÇIYOR
Python üçünü de "temiz" gösteriyordu.

Bundan sonra Türkçe metne uygulanan her kalıp bu yardımcıyla sınanıyor.
"""

import base64
import json
import os
import shutil
import subprocess
import tempfile

KOK = os.path.dirname(os.path.abspath(__file__))

_JAVA = r"""
import java.nio.file.*; import java.util.*; import java.util.regex.*;
public class Esle {
    public static void main(String[] a) throws Exception {
        List<String> satirlar = Files.readAllLines(Paths.get(a[0]));
        StringBuilder out = new StringBuilder();
        for (String l : satirlar) {
            if (l.trim().isEmpty()) continue;
            String[] p = l.split("\\|", 2);
            String pat = new String(Base64.getDecoder().decode(p[0]), "UTF-8");
            String metin = new String(Base64.getDecoder().decode(p[1]), "UTF-8");
            String sonuc;
            try {
                Matcher m = Pattern.compile(pat).matcher(metin);
                if (m.find()) {
                    sonuc = "1|" + Base64.getEncoder().encodeToString(
                        m.group().getBytes("UTF-8"));
                } else {
                    sonuc = "0|";
                }
            } catch (PatternSyntaxException e) {
                sonuc = "E|" + Base64.getEncoder().encodeToString(
                    e.getDescription().getBytes("UTF-8"));
            }
            out.append(sonuc).append("\n");
        }
        Files.write(Paths.get(a[1]), out.toString().getBytes("UTF-8"));
    }
}
"""


class JavaYok(Exception):
    """JDK bulunamadı; çağıran test kendini atlayabilir."""


def _icu():
    """Android'in gerçek regex motoru (ICU). Yoksa None."""
    try:
        import icu_regex
        return icu_regex
    except Exception:
        return None


MOTOR = "ICU (Android'in motoru)" if _icu() else "masaüstü Java (yedek)"


def esle(ciftler):
    """
    ÖNCE ICU: Android'de java.util.regex OpenJDK değil ICU'dur. İkisi
    farklı davranır ve masaüstü Java'ya güvenmek uygulamayı çökertti:
    Java "(?U)" bayrağını kabul eder, ICU U_REGEX_INVALID_FLAG verir.
    ICU kütüphanesi yoksa masaüstü Java'ya düşülür (daha zayıf kanıt).
    """
    icu = _icu()
    if icu is not None:
        out = []
        for pat, metin in ciftler:
            ok, es, h = icu.esle(pat, metin)
            out.append((ok, es, h))
        return out
    return _esle_java(ciftler)


def _esle_java(ciftler):
    """
    ciftler: [(kalip, metin), ...]
    döner  : [(eslesti: bool, eslesen_metin: str|None, hata: str|None), ...]

    Kalıplar JAVA'da (java.util.regex) derlenip çalıştırılır — Kotlin'in
    gerçekte kullandığı motor.
    """
    if not ciftler:
        return []
    if not shutil.which("javac") or not shutil.which("java"):
        raise JavaYok("javac/java bulunamadı")

    gecici = tempfile.mkdtemp(prefix="jregex")
    try:
        kjava = os.path.join(gecici, "Esle.java")
        with open(kjava, "w", encoding="utf-8") as f:
            f.write(_JAVA)
        d = subprocess.run(["javac", "-encoding", "UTF-8", "-d", gecici, kjava],
                           capture_output=True, text=True)
        if d.returncode:
            raise RuntimeError("Esle.java derlenmedi:\n" + d.stderr)

        giris = os.path.join(gecici, "giris.txt")
        with open(giris, "w", encoding="utf-8") as f:
            for pat, metin in ciftler:
                f.write("%s|%s\n" % (
                    base64.b64encode(pat.encode("utf-8")).decode(),
                    base64.b64encode(metin.encode("utf-8")).decode()))

        cikis = os.path.join(gecici, "cikis.txt")
        ortam = dict(os.environ, JAVA_TOOL_OPTIONS="")
        s = subprocess.run(["java", "-cp", gecici, "-Dfile.encoding=UTF-8",
                            "Esle", giris, cikis],
                           capture_output=True, text=True, env=ortam)
        if s.returncode:
            raise RuntimeError("Esle çalışmadı:\n" + s.stdout + s.stderr)

        sonuclar = []
        with open(cikis, encoding="utf-8") as f:
            for l in f.read().splitlines():
                if not l:
                    continue
                bayrak, _, veri = l.partition("|")
                if bayrak == "E":
                    sonuclar.append((False, None,
                                     base64.b64decode(veri).decode("utf-8")))
                elif bayrak == "1":
                    sonuclar.append((True,
                                     base64.b64decode(veri).decode("utf-8"), None))
                else:
                    sonuclar.append((False, None, None))
        if len(sonuclar) != len(ciftler):
            raise RuntimeError("Java %d sonuç döndürdü, %d beklendi"
                               % (len(sonuclar), len(ciftler)))
        return sonuclar
    finally:
        shutil.rmtree(gecici, ignore_errors=True)


def kalip_cikar(kaynak, ad):
    """Kotlin kaynağından `private val <ad> = Regex(...)` kalıbını çıkarır."""
    import re
    im = "private val %s = Regex(" % ad
    i = kaynak.index(im)
    j = kaynak.index("(", i + len(im) - 1)
    d = 0
    son = -1
    for k in range(j, len(kaynak)):
        if kaynak[k] == "(":
            d += 1
        elif kaynak[k] == ")":
            d -= 1
            if d == 0:
                son = k
                break
    if son < 0:
        raise ValueError("%s kapanmıyor" % ad)
    govde = "\n".join(s for s in kaynak[j:son].splitlines()
                      if not s.strip().startswith("//"))
    # Şablon kullanılıyorsa statik sınama anlamsız olur.
    kalansiz = re.sub(r'"(?:[^"\\]|\\.)*"', "", govde)
    if "$" in kalansiz:
        raise ValueError("%s şablon kullanıyor, statik sınanamaz" % ad)
    parcalar = re.findall(r'"((?:[^"\\]|\\.)*)"', govde)
    return "".join(parcalar).replace("\\\\", "\\")

"""
Kotlin'de "taşıma sırasında unutulan parametre" hatasını derlemeden önce yakalar.

Geniş bir denetim yanlış alarm üretiyor, o yüzden tek bir hata türüne
odaklanıyoruz: bir fonksiyon, BAŞKA bir fonksiyonun parametresi olan ama
kendisinde tanımlı olmayan bir ismi kullanıyorsa uyarıyoruz. Kodu bölüp
taşırken yapılan hata tam olarak budur ve derlemeyi durdurur.
"""

import os
import re
import sys

KOK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "java")

FUN_BASI = re.compile(
    r"^[ \t]*(?:(?:private|public|internal|protected|override|suspend|inline|open)\s+)*"
    r"fun\s+(?:<[^>]+>\s*)?(\w+)\s*\(",
    re.M,
)


def interpolasyonu_sil(kod):
    """${...} içini siler. İçinde tırnak olabiliyor ve dizge süzgecini bozuyor:
       "mod:${if (x) "internet (API)" else "..."}"
    Bu yüzden "internet(" ve "else(" tanımsız fonksiyon sanılıyordu."""
    cikti = []
    i = 0
    while i < len(kod):
        if kod.startswith("${", i):
            derinlik = 0
            j = i + 1
            while j < len(kod):
                if kod[j] == "{":
                    derinlik += 1
                elif kod[j] == "}":
                    derinlik -= 1
                    if derinlik == 0:
                        break
                j += 1
            cikti.append(" ")
            i = j + 1
        else:
            cikti.append(kod[i])
            i += 1
    return "".join(cikti)


def temizle(kod):
    kod = re.sub(r"//[^\n]*", "", kod)
    kod = re.sub(r"/\*.*?\*/", "", kod, flags=re.S)
    kod = interpolasyonu_sil(kod)
    kod = re.sub(r'"""(?:.|\n)*?"""', '""', kod)
    kod = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', kod)
    # Karakter sabitleri: ".replace('â', 'a')" içindeki 'a' tanımsız isim
    # sanılıyordu. Dizgeler gibi bunlar da kod değil.
    kod = re.sub(r"'(?:\\.|[^'\\\n])'", "' '", kod)
    return kod


def eslesen_kapanis(metin, acilis, ac, kapa):
    derinlik = 0
    for j in range(acilis, len(metin)):
        if metin[j] == ac:
            derinlik += 1
        elif metin[j] == kapa:
            derinlik -= 1
            if derinlik == 0:
                return j
    return len(metin) - 1


def fonksiyonlari_cikar(kod):
    """[(ad, parametre kümesi, gövde)] döndürür."""
    sonuc = []
    for m in FUN_BASI.finditer(kod):
        ad = m.group(1)
        acilis = kod.index("(", m.start())
        kapanis = eslesen_kapanis(kod, acilis, "(", ")")
        par = set(re.findall(r"(\w+)\s*:", kod[acilis + 1:kapanis]))

        govde_basi = kod.find("{", kapanis)
        esittir = kod.find("=", kapanis)
        if govde_basi == -1 or (esittir != -1 and esittir < govde_basi):
            # TEK İFADELİ GÖVDE ("fun f(): Int = ..."). Eskiden "\n\n" aranıyordu
            # ve araya boş satır girmeyince gövde BİR SONRAKİ fonksiyonu da
            # yutuyordu; onun parametreleri bu fonksiyonda tanımsız sanılıyordu
            # ("fun ekranY(y: Int) = ..." içinde runOcr'ın filterEdges'i).
            # Yeni bildirim başlayan ilk satırda duruyoruz.
            yeni_bildirim = re.compile(
                r"^[ \t]*(?:@|\}|(?:private|public|internal|protected|override|"
                r"suspend|inline|open|companion|abstract)\s+)*"
                r"(?:fun|val|var|class|object|interface|init)\b")
            satirlar = kod[kapanis:].split("\n")
            toplanan = [satirlar[0]]
            for satir in satirlar[1:]:
                if yeni_bildirim.match(satir):
                    break
                toplanan.append(satir)
            govde = "\n".join(toplanan)
        else:
            son = eslesen_kapanis(kod, govde_basi, "{", "}")
            govde = kod[govde_basi + 1:son]
        sonuc.append((ad, par, govde))
    return sonuc


def yerel_isimler(govde):
    isimler = set()
    isimler |= set(re.findall(r"\b(?:val|var)\s+(\w+)", govde))
    isimler |= set(re.findall(r"\bcatch\s*\(\s*(\w+)\s*:", govde))
    isimler |= set(re.findall(r"\bfor\s*\(\s*(\w+)\s+in", govde))
    # for ((a, b) in ...) ve { a, b -> } gibi çoklu tanımlar
    for kalip in (r"\(\s*\((\w+)\s*,\s*(\w+)\)", r"\{\s*(\w+)\s*,\s*(\w+)\s*->"):
        for ikili in re.findall(kalip, govde):
            isimler |= set(ikili)
    # for ((text, x, y) in found) — üçlü ayrıştırma
    for uclu in re.findall(r"\(\s*\((\w+)\s*,\s*(\w+)\s*,\s*(\w+)\s*\)", govde):
        isimler |= set(uclu)
    isimler |= set(re.findall(r"\{\s*(\w+)\s*->", govde))
    # Parantezli ayrıştırma: list.map { (en, tr) -> ... }
    for ikili in re.findall(r"\{\s*\(\s*(\w+)\s*,\s*(\w+)\s*\)\s*->", govde):
        isimler |= set(ikili)
    for uclu in re.findall(r"\{\s*\(\s*(\w+)\s*,\s*(\w+)\s*,\s*(\w+)\s*\)\s*->", govde):
        isimler |= set(uclu)
    # gövde içindeki iç içe fonksiyonların parametreleri de burada tanımlıdır
    for ic in re.finditer(r"\bfun\s+\w+\s*\(([^)]*)\)", govde):
        isimler |= set(re.findall(r"(\w+)\s*:", ic.group(1)))
    isimler |= set(re.findall(r"\bval\s*\((\w+)\s*,\s*(\w+)\)", govde) and
                   [x for t in re.findall(r"\bval\s*\((\w+)\s*,\s*(\w+)\)", govde) for x in t])
    return isimler


def bloklari_cikar(kod):
    """Örtülü alıcılı blokların içini siler."""
    kalipler = [r"\.apply\s*\{", r"\.also\s*\{", r"\.run\s*\{",
                r"\.let\s*\{", r"\.use\s*\{", r"object\s*:[^{]*\{",
                r"\bwith\s*\([^)]*\)\s*\{"]
    degisti = True
    while degisti:
        degisti = False
        for kalip in kalipler:
            m = re.search(kalip, kod)
            if not m:
                continue
            acilis = kod.index("{", m.start())
            kapanis = eslesen_kapanis(kod, acilis, "{", "}")
            kod = kod[:m.start()] + " " + kod[kapanis + 1:]
            degisti = True
            break
    return kod


def denetle(yol):
    kod = temizle(open(yol, encoding="utf-8").read())
    sinif_uyeleri = set(re.findall(r"^\s*(?:private |)(?:@Volatile\s+)?(?:val|var|const val)\s+(\w+)",
                                   kod, re.M))
    fonksiyonlar = fonksiyonlari_cikar(kod)

    # Tüm parametre isimleri havuzu: bunlar "taşınabilir" isimlerdir.
    tum_parametreler = set()
    for _, par, _ in fonksiyonlar:
        tum_parametreler |= par

    # Local functions close over parameters of their enclosing function. Track
    # lexical scopes rather than reporting valid closures as undefined names.
    scopes = []
    for match in FUN_BASI.finditer(kod):
        opening = kod.index("(", match.start())
        closing = eslesen_kapanis(kod, opening, "(", ")")
        brace = kod.find("{", closing)
        equal = kod.find("=", closing)
        if brace >= 0 and (equal < 0 or brace < equal or
                re.search(r"\bwithContext\s*\(", kod[equal:brace])):
            end = eslesen_kapanis(kod, brace, "{", "}")
            pars = set(re.findall(r"(\w+)\s*:", kod[opening + 1:closing]))
            scopes.append((match.start(), brace, end, pars))

    known_members = set(re.findall(r"\bfun\s+(\w+)\s*\(", kod))
    sorunlar = []
    for idx, (ad, par, govde) in enumerate(fonksiyonlar):
        # apply/also/run/let/with blokları örtük alıcıyla çalışır: içlerindeki
        # "alpha = 0.6f" alıcının özelliğidir, tanımsız isim değil.
        govde = bloklari_cikar(govde)
        inherited = set()
        starts = list(FUN_BASI.finditer(kod))
        if idx < len(starts):
            start = starts[idx].start()
            for parent_start, parent_brace, parent_end, parent_params in scopes:
                if parent_brace < start < parent_end:
                    inherited |= parent_params
        tanimli = par | yerel_isimler(govde) | sinif_uyeleri | inherited
        # yalnızca başka fonksiyonun parametresi olan isimleri kontrol et
        supheli = tum_parametreler - tanimli
        for isim in sorted(supheli):
            # nokta olmadan, tek başına kullanılıyor mu
            # adlandırılmış argüman (fonk(x = 1)) kullanım sayılmaz
            # DİKKAT: "\s*(?!=...)" yazılırsa \s* geri izleyip sıfır boşlukla
            # eşleşiyor ve koruma deliniyordu; "runOcr(band, filterEdges = true)"
            # adlandırılmış argümanı bu yüzden kullanım sanılıyordu.
            kullanim = re.search(rf"(?<![\w.]){re.escape(isim)}(?![\w])(?!\s*=[^=])", govde)
            adlandirilmis = re.search(rf"[(,]\s*{re.escape(isim)}\s*=[^=]", govde)
            # A same-file method call such as bytes(...) is a valid member
            # invocation, not a reference to parse(bytes: ByteArray).
            member_call = isim in known_members and bool(re.search(
                rf"(?<![\w.]){re.escape(isim)}\s*\(", govde))
            if kullanim and not adlandirilmis and not member_call:
                sorunlar.append((ad, isim))
    return sorunlar


def ust_duzey_parametreler(metin):
    """Parametre listesini ÜST DÜZEY virgüllerden böler; "(Req) -> String"
    gibi iç içe parantezleri bozmadan (ad, tip) ikilileri döndürür."""
    parcalar = []
    derinlik = 0
    son = 0
    for i, c in enumerate(metin):
        # DİKKAT: "->" içindeki ">" kapanış sayılmamalı, yoksa derinlik eksiye
        # düşüyor ve "ready: () -> Boolean, onClient: ..." tek parça kalıyordu.
        if c == ">" and i > 0 and metin[i - 1] == "-":
            continue
        if c in "(<[":
            derinlik += 1
        elif c in ")>]":
            derinlik -= 1
        elif c == "," and derinlik == 0:
            parcalar.append(metin[son:i])
            son = i + 1
    parcalar.append(metin[son:])
    sonuc = []
    for p in parcalar:
        m = re.match(r"\s*(?:vararg\s+)?(\w+)\s*:\s*(.+)", p, re.S)
        if m:
            sonuc.append((m.group(1), m.group(2)))
    return sonuc


def cagri_denetle(yol):
    """Tanımlanmamış fonksiyonların çağrılmasını yakalar."""
    ham = open(yol, encoding="utf-8").read()
    tum_kod = temizle(ham)
    tum_fonksiyonlar = set(re.findall(r"\bfun\s+(\w+)", tum_kod))
    kod = tum_kod

    # apply/run/also/let/use blokları ve object ifadeleri içindeki çağrılarda
    # alıcı örtülüdür (textView.apply { setTextColor(...) }); bunları çıkarıyoruz
    # yoksa her Android çağrısı yanlışlıkla "tanımsız" görünür.
    kod = bloklari_cikar(kod)

    tanimli = tum_fonksiyonlar
    tanimli |= set(re.findall(r"\b(?:val|var)\s+(\w+)", tum_kod))
    # Fonksiyon TİPİNDE parametreler çağrılabilir: "stillWanted: () -> Boolean"
    # sonra "stillWanted()". İmza çok satırlı ve iç içe parantezli olabildiği
    # için ("ready: () -> Boolean, work: (Req) -> String") dengeli parantez
    # taraması gerekiyor; düz regex ilk ")" de duruyordu.
    for m in re.finditer(r"\bfun\s+(?:<[^>]+>\s*)?\w+\s*\(", tum_kod):
        acilis = tum_kod.index("(", m.start())
        kapanis = eslesen_kapanis(tum_kod, acilis, "(", ")")
        for isim, tip in ust_duzey_parametreler(tum_kod[acilis + 1:kapanis]):
            if "->" in tip:
                tanimli.add(isim)
    # Yerel lambda değişkenleri: "val isle: (Int) -> Unit = { ... }"
    for isim, tip in re.findall(r"\b(?:val|var)\s+(\w+)\s*:\s*([^=\n]+)=", tum_kod):
        if "->" in tip:
            tanimli.add(isim)
    tanimli |= set(re.findall(r"\b(?:class|object|enum class|interface)\s+(\w+)", tum_kod))

    # this.xxx(...) ya da sade xxx(...) biçiminde, nokta olmadan yapılan çağrılar
    cagrilar = set(re.findall(r"(?<![\w.])([a-z]\w*)\s*\(", kod))

    # Kotlin/Android'in hazır fonksiyonları: tanımlı saymamız gerekenler
    hazir = {
        "listOf", "setOf", "mapOf", "arrayOf", "mutableListOf", "mutableMapOf",
        "emptyList", "emptySet", "emptyMap", "buildMap", "intArrayOf",
        "minOf", "maxOf", "require", "check", "error", "println", "print",
        "launch", "delay", "withContext", "runBlocking", "async", "await",
        "suspendCancellableCoroutine", "measureTimeMillis", "lazy", "run",
        "let", "also", "apply", "takeIf", "takeUnless", "repeat", "with",
        "synchronized", "arrayOfNulls", "booleanArrayOf", "byteArrayOf",
        "if", "when", "while", "for", "catch", "return", "throw", "super",
        "this", "it", "intent", "resources", "startActivity", "startService",
        "stopSelf", "stopForeground", "startForeground", "getSystemService",
        "registerForActivityResult", "findViewById", "setContentView",
        "runOnUiThread", "requestPermissions", "moveTaskToBack", "post",
        "toString", "trim", "take", "format", "valueOf", "joinToString",
        "contains", "startsWith", "endsWith", "replace", "split", "indexOf",
        "substring", "lowercase", "uppercase", "firstOrNull", "ifBlank",
        "coerceIn", "coerceAtLeast", "coerceAtMost", "copyOf", "abs",
        "arraycopy", "currentTimeMillis", "sleep", "max", "min", "sqrt",
        # property accessor'ları ve örtülü alıcılı yaygın çağrılar
        "get", "set", "put", "append", "add", "remove", "clear", "close",
        "val", "var", "fun", "init",
        # Activity / View / Service'ten miras alinan hazir fonksiyonlar
        "finish", "invalidate", "postInvalidate", "onTouchEvent",
        "performClick", "requestLayout", "constructor", "setOnTouchListener",
        "setOnClickListener", "setContentView", "getContext", "getWidth",
        "getHeight", "drawRect", "drawCircle", "drawText", "drawRoundRect",
        "contains", "coerceIn", "coerceAtLeast", "coerceAtMost", "isEmpty",
        "isNotEmpty", "indices", "listOf", "first", "second",
        # Anahtar kelimeler: "else (x)" ve "ml: suspend (String) -> ..." gibi
        # yerlerde isimden sonra parantez geliyor, fonksiyon çağrısı değil.
        "else", "suspend", "in", "is", "as", "by", "do", "out", "vararg",
        # Eksik kalan stdlib
        "charArrayOf", "sequenceOf", "generateSequence", "doubleArrayOf",
        "longArrayOf", "floatArrayOf", "shortArrayOf", "emptyArray",
        "lazyOf", "TODO", "requireNotNull", "checkNotNull", "assert",
        "listOfNotNull", "toList", "appendLine", "shl",
    }

    sorunlar = []
    for ad in sorted(cagrilar - tanimli - hazir):
        if len(ad) <= 2:
            continue
        # tanımlıysa ve import edilmişse atla
        if re.search(rf"import .*\b{re.escape(ad)}\b", ham):
            continue
        # sınıf adı gibi büyük harfle başlıyorsa atla (yukarıda filtrelendi)
        sorunlar.append(ad)
    return sorunlar


def main():
    toplam = []
    for dizin, _, dosyalar in os.walk(KOK):
        for d in sorted(dosyalar):
            if d.endswith(".kt"):
                yol = os.path.join(dizin, d)
                for fonk, isim in denetle(yol):
                    toplam.append(f"{d} → fun {fonk}() içinde tanımsız olabilir: {isim}")
                for ad in cagri_denetle(yol):
                    toplam.append(f"{d} → tanımlı olmayan fonksiyon çağrısı: {ad}()")

    if toplam:
        print("Olası tanımsız parametreler:")
        for satir in sorted(set(toplam)):
            print("  ✗", satir)
        return 1
    print("Kotlin parametre denetimi temiz.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

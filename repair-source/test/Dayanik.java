import java.nio.file.*; import java.util.*; import java.util.concurrent.*; import java.util.regex.*;
/**
 * KALIP DAYANIKLILIGI.
 *
 * Iki calisma ani arizasini ariyor:
 *
 * 1) BOS ESLESME. SlangNormalizer.normalize / Slang.normalize / TurkishFix
 *    icinde su satir var:
 *        if (m.value.first().isUpperCase()) ...
 *    "m.value" bos ise first() NoSuchElementException atar ve ceviri
 *    ortasinda COKER. Bir kalip sifir uzunlukta eslesebiliyorsa bu olur.
 *
 * 2) FELAKET GERI IZLEME. Ic ice niceleyici tasiyan bir kalip, bozuk uzun
 *    OCR satirinda eksponansiyel zamana girebilir. Uygulama donar, altyazi
 *    ekranda kalir. Gecikme bu projenin asil derdi oldugu icin olculuyor.
 *
 * Kaliplar kaynaktan okunur; kalip eklenince test kendiliginden kapsar.
 */
public class Dayanik {
    static int hata = 0;
    static final long SINIR_MS = 400;

    /** Saldirgan girdiler: OCR'in uretebilecegi en kotu haller. */
    static final String[] GIRDILER = yapGirdiler();

    static String[] yapGirdiler() {
        List<String> g = new ArrayList<>(Arrays.asList(
            "", " ", "  ", ".", "...", "!!!", "'", "''", "’", "-", "--",
            "[", "]", "()", "[]", "(", ")", "[[[", "]]]", "I", "a", "A",
            "i", "ı", "İ", "ş", "ğ", "Ğ",
            "a b", "I I", "' '", "[ ]", "- -", "d'", "'d",
            "Lu? Lu? Siktir.", "were here", "Talk in'", "s Oon",
            "[whispering] It's perfect.", "Telia) Because we'd never take it.",
            "ch At be w As worthy o F DArrying che Drince",
            "göt götürdü götürmek beğendim", "ölmedi gülmedi görmedi üşümedi",
            "toksikoloji eksik klasik motosiklet sıkıntı"
        ));
        // uzun ve tekrarli: geri izleme tuzaklari
        StringBuilder a = new StringBuilder();
        for (int i = 0; i < 400; i++) a.append("a");
        g.add(a.toString());
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 300; i++) b.append("ab ");
        g.add(b.toString());
        StringBuilder c = new StringBuilder();
        for (int i = 0; i < 200; i++) c.append("the fuck ");
        g.add(c.toString());
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < 150; i++) d.append("I ");
        g.add(d.toString());
        StringBuilder e = new StringBuilder();
        for (int i = 0; i < 120; i++) e.append("[groans] ");
        g.add(e.toString());
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < 100; i++) f.append("were here theyre youre ");
        g.add(f.toString());
        StringBuilder h = new StringBuilder();
        for (int i = 0; i < 100; i++) h.append("ölmedi değil hiç yok ");
        g.add(h.toString());
        StringBuilder k = new StringBuilder();
        for (int i = 0; i < 90; i++) k.append("in' ");
        g.add(k.toString());
        return g.toArray(new String[0]);
    }

    static String oku(String y) throws Exception {
        return new String(Files.readAllBytes(Paths.get(y)), "UTF-8");
    }

    static String temizYorum(String s) {
        StringBuilder sb = new StringBuilder();
        for (String l : s.split("\n")) if (!l.trim().startsWith("//")) sb.append(l).append("\n");
        return sb.toString();
    }

    static String coz(String h) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c == '\\' && i + 1 < h.length()) {
                char n = h.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '$': sb.append('$'); break;
                    default: sb.append('\\').append(n);
                }
            } else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Kaynaktaki her Regex(...) cagrisi — YALNIZ tamami dizgi literali
     * olanlar.
     *
     * Ilk hali "Regex(\"(?i)\" + it.first)" gibi cagrilarda sadece
     * "(?i)" kismini aliyordu; bu gecerli bir regex oldugu icin test
     * sessizce HICBIR SEY dogrulamiyordu. Artik parantez dengeli okunuyor
     * ve dizgi literalleri cikarildiktan sonra geriye tanimlayici kaliyorsa
     * (degisken/sablon) kalip ATLANIYOR.
     */
    static List<String[]> literaller(String dizin, String[] dosyalar) throws Exception {
        List<String[]> out = new ArrayList<>();
        Pattern str = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
        for (String d : dosyalar) {
            String s = temizYorum(oku(dizin + "/" + d));
            int i = 0;
            while ((i = s.indexOf("Regex(", i)) >= 0) {
                int j = i + "Regex".length();          // "(" burada
                int derinlik = 0, son = -1;
                boolean dizgide = false;
                for (int k = j; k < s.length(); k++) {
                    char c = s.charAt(k);
                    if (dizgide) {
                        if (c == '\\') k++;
                        else if (c == '"') dizgide = false;
                        continue;
                    }
                    if (c == '"') { dizgide = true; continue; }
                    if (c == '(') derinlik++;
                    else if (c == ')') { derinlik--; if (derinlik == 0) { son = k; break; } }
                }
                if (son < 0) break;
                String arg = s.substring(j + 1, son);
                StringBuilder pat = new StringBuilder();
                Matcher sm = str.matcher(arg);
                while (sm.find()) pat.append(sm.group(1));
                // Dizgileri cikar; geriye tanimlayici kaliyorsa literal degil.
                String kalan = sm.reset().replaceAll("")
                    .replace("RegexOption.IGNORE_CASE", "")
                    .replace("RegexOption.DOT_MATCHES_ALL", "")
                    .replace("RegexOption.MULTILINE", "");
                boolean sablon = kalan.matches("(?s).*[A-Za-z_$].*");
                String pp = coz(pat.toString());
                if (!sablon && pp.length() > 0 && !pp.contains("${")
                    && !pp.matches("(?s).*\\$[A-Za-z_].*")) {
                    // val adi: "private val AD = Regex(" kalibindan geriye bak
                    String ad = "";
                    int vb = s.lastIndexOf("val ", i);
                    if (vb > 0 && i - vb < 60) {
                        String par = s.substring(vb + 4, i).trim();
                        int eq = par.indexOf('=');
                        if (eq > 0) ad = par.substring(0, eq).trim();
                    }
                    // Lambda ile mi kullaniliyor? ".replace(x) {" varsa m.value
                    // lambdaya giriyor ve bos eslesmede first() COKER.
                    boolean lambda = !ad.isEmpty()
                        && s.matches("(?s).*" + Pattern.quote(ad) + "\\.replace\\([^)]*\\)\\s*\\{.*");
                    out.add(new String[]{d + (ad.isEmpty() ? "" : ":" + ad),
                                         pp, lambda ? "L" : "-"});
                }
                i = son + 1;
            }
        }
        return out;
    }

    /** "a" to "b" ciftlerinden kurulan kalip listeleri. */
    static void ciftKaliplar(String govde, String onek, String sonek,
                             String etiket, List<String[]> hedef) {
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s+to\\s+\"((?:[^\"\\\\]|\\\\.)*)\"")
            .matcher(govde);
        while (m.find()) hedef.add(new String[]{etiket, onek + coz(m.group(1)) + sonek, "L"});
    }

    static String blok(String s, String isaret, String bitis) {
        int i = s.indexOf(isaret);
        if (i < 0) return "";
        int j = bitis == null ? s.indexOf("\n    )", i) : s.indexOf(bitis, i);
        if (j < 0) j = Math.min(s.length(), i + 20000);
        return temizYorum(s.substring(i, j));
    }

    /**
     * KALIBA OZEL saldirgan girdi.
     *
     * Genel girdi kumesi yetmiyor: Java, kalibin sonundaki zorunlu harfi
     * girdide bulamazsa hizli eliyor ve felaket geri izleme hic
     * tetiklenmiyor (negatif kontrolde "(a+)+b" boyle kacti). Bu yuzden
     * kalibin KENDI harflerinden uzun girdi uretiyoruz: zorunlu literal de
     * iceride oldugu icin motor gercekten aramak zorunda kaliyor.
     */
    static List<String> kalibaOzel(String pat) {
        // Kaliptaki harf/bosluk kaciklarini topla (metakarakterleri atla)
        StringBuilder harfler = new StringBuilder();
        for (int i = 0; i < pat.length(); i++) {
            char c = pat.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}'
                || c == '?' || c == '*' || c == '+' || c == '|' || c == '^'
                || c == '$' || c == '.' || c == '<' || c == '=' || c == '!'
                || c == ':' || c == '-' || c == ',') continue;
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '\'') harfler.append(c);
        }
        List<String> out = new ArrayList<>();
        String h = harfler.toString().trim();
        if (h.isEmpty()) return out;
        String cekirdek = h.length() > 24 ? h.substring(0, 24) : h;
        StringBuilder uzun = new StringBuilder();
        for (int i = 0; i < 60; i++) uzun.append(cekirdek).append(" ");
        out.add(uzun.toString());
        // tek harfin uzun tekrari + zorunlu literalin sonda bulunmasi
        char ilk = cekirdek.charAt(0);
        StringBuilder tek = new StringBuilder();
        for (int i = 0; i < 350; i++) tek.append(ilk);
        out.add(tek.toString());
        out.add(tek.toString() + cekirdek);
        out.add(cekirdek + tek.toString());
        return out;
    }

    public static void main(String[] a) throws Exception {
        String dizin = a[0];
        List<String[]> kaliplar = literaller(dizin,
            new String[]{"OfflineFix.kt", "AiGuard.kt", "OcrSozluk.kt",
                         "TurkishFix.kt", "TextFix.kt", "Slang.kt", "CaptureService.kt"});

        String off = temizYorum(oku(dizin + "/OfflineFix.kt"));
        String sl = temizYorum(oku(dizin + "/Slang.kt"));
        // SlangNormalizer ve Slang.rewrites: urun bunlari IGNORE_CASE ile derler
        ciftKaliplar(blok(off, "object SlangNormalizer {", "fun normalize(text: String)"),
            "(?i)", "", "SlangNormalizer", kaliplar);
        ciftKaliplar(blok(sl, "private val rewrites", "fun normalize(text: String)"),
            "(?i)", "", "Slang.rewrites", kaliplar);
        // ProfanityShield kelime haritasi
        int ix = off.indexOf(".map { Regex(\"(?i)\\\\b${it.first}\\\\b\") to it.second }");
        if (ix > 0) {
            int bas = off.lastIndexOf("= listOf(", ix);
            ciftKaliplar(off.substring(bas, ix), "(?i)\\b", "\\b", "kufur", kaliplar);
        }

        System.out.println("  denetlenen kalip: " + kaliplar.size()
            + ", genel girdi: " + GIRDILER.length + " + kaliba ozel 4");

        ExecutorService ex = Executors.newSingleThreadExecutor();
        int bos = 0, yavas = 0, patlak = 0, sifirGenislik = 0;
        for (String[] k : kaliplar) {
            Pattern P;
            try { P = Pattern.compile(k[1]); }
            catch (PatternSyntaxException e) { patlak++; hata++;
                System.out.println("  X DERLENMEDI " + k[0] + ": " + e.getDescription());
                continue; }
            List<String> girdiler = new ArrayList<>(Arrays.asList(GIRDILER));
            girdiler.addAll(kalibaOzel(k[1]));
            for (String g : girdiler) {
                Future<Object[]> f = ex.submit(() -> {
                    long t0 = System.nanoTime();
                    Matcher m = P.matcher(g);
                    boolean bosEsleme = false;
                    int n = 0;
                    while (m.find() && n++ < 5000) if (m.group().isEmpty()) { bosEsleme = true; break; }
                    return new Object[]{bosEsleme, (System.nanoTime() - t0) / 1_000_000L};
                });
                try {
                    Object[] r = f.get(SINIR_MS * 3, TimeUnit.MILLISECONDS);
                    if ((Boolean) r[0]) {
                        if ("L".equals(k[2])) {
                            bos++; hata++;
                            System.out.println("  X BOS ESLESME + LAMBDA " + k[0]
                                + "  -> m.value.first() NoSuchElementException");
                            System.out.println("      kalip: " + kisalt(k[1]));
                            System.out.println("      girdi: " + kisalt(g));
                        } else {
                            sifirGenislik++;
                        }
                        break;
                    }
                    long ms = (Long) r[1];
                    if (ms > SINIR_MS) {
                        yavas++; hata++;
                        System.out.println("  X YAVAS " + k[0] + "  " + ms + " ms");
                        System.out.println("      kalip: " + kisalt(k[1]));
                        System.out.println("      girdi uzunlugu: " + g.length());
                        break;
                    }
                } catch (TimeoutException te) {
                    f.cancel(true);
                    yavas++; hata++;
                    System.out.println("  X KILITLENDI " + k[0] + " (>" + SINIR_MS * 3 + " ms)");
                    System.out.println("      kalip: " + kisalt(k[1]));
                    System.out.println("      girdi uzunlugu: " + g.length());
                    break;
                }
            }
        }
        ex.shutdownNow();
        System.out.println("  COKME riski (bos eslesme + lambda): " + bos
            + " | yavas/kilitli: " + yavas + " | derlenmeyen: " + patlak
            + " | zararsiz sifir-genislik kalip: " + sifirGenislik);
        if (hata > 0) { System.out.println("DAYANIK DUSTU: " + hata + " bulgu."); System.exit(1); }
        System.out.println("Dayaniklilik testi gecti: lambda ile kullanilan hicbir "
            + "kalip bos eslesmiyor, saldirgan girdide kilitlenme yok.");
        System.out.println("  NOT: zamanlama olcumu, teorik felaket geri izlemenin "
            + "YOKLUGUNU kanitlamaz; gercek kaliplarin gercekci uzun girdide "
            + "yavaslamadigini gosterir.");
    }

    static String kisalt(String s) {
        String t = s.replace("\n", "\\n");
        return t.length() > 96 ? t.substring(0, 96) + "..." : t;
    }
}

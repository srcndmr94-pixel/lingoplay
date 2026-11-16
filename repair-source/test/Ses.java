import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * SES ETIKETI KATMANININ AYNASI (SoundTags).
 *
 * Sozlukler (words, skip, adjectives, phrases, devamAdi) Kotlin kaynagindan
 * CALISMA ANINDA okunuyor; elle kopya tutulmuyor.
 *
 * repair/translate/translatePart ise duz prosedurel Kotlin, kural listesi
 * degil; onlar elle mirror edildi. Bayatlama tehlikesine karsi bir ALARM var:
 * Kotlin'deki repair() govdesindeki Regex sayisi burada beklenenden farkliysa
 * test dusuyor ve aynayi guncellemeye zorluyor. Elle kopya tutan aynalar bu
 * projede uc kez sessizce bayatladi; bir daha sessiz olmasin.
 */
public class Ses {

    static final int BEKLENEN_REPAIR_REGEX = 10;

    static Map<String, String> words = new LinkedHashMap<>();
    static Map<String, String> phrases = new LinkedHashMap<>();
    static Map<String, String> devamAdi = new LinkedHashMap<>();
    static Map<String, String> roller = new LinkedHashMap<>();
    static Map<String, String> araclar = new LinkedHashMap<>();
    static Map<String, String> ozneliFiil = new LinkedHashMap<>();
    static final Pattern ROL_ARACLI = Pattern.compile("^(\\w+)\\s+(?:on|over)\\s+(?:the\\s+)?(\\w+)$");
    static Set<String> skip = new HashSet<>();
    static Set<String> adjectives = new HashSet<>();
    static Set<String> ZARF = new HashSet<>();
    static int hata = 0;

    static String oku(String p) throws IOException {
        return new String(Files.readAllBytes(Paths.get(p)), "UTF-8");
    }

    /** listOf( ... ) govdesini dengeli parantezle keser.
     *  Yorum ve dizge icindeki parantezi SAYMAZ: kaynakta bir yorumda tek
     *  basina '(' gecmesi blogu kendi sonundan tasirip sonraki tabloyu da
     *  icine aliyordu, o da sessizce yanlis deger veriyordu. */
    static String blok(String metin, String baslik, String acilis) {
        int i = metin.indexOf(baslik);
        if (i < 0) throw new RuntimeException("bulunamadi: " + baslik);
        i = metin.indexOf(acilis, i);
        int derin = 0, j = i + acilis.length() - 1;
        boolean dizge = false, yorum = false;
        while (true) {
            char c = metin.charAt(j);
            if (yorum) {
                if (c == '\n') yorum = false;
            } else if (dizge) {
                if (c == '\\') j++;
                else if (c == '"') dizge = false;
            } else if (c == '/' && j + 1 < metin.length() && metin.charAt(j + 1) == '/') {
                yorum = true;
            } else if (c == '"') {
                dizge = true;
            } else if (c == '(') derin++;
            else if (c == ')') { derin--; if (derin == 0) break; }
            j++;
        }
        return metin.substring(i, j);
    }

    static void ciftlere(String govde, Map<String, String> hedef) {
        Matcher m = Pattern.compile("\"([^\"]*)\"\\s*to\\s*\"([^\"]*)\"").matcher(govde);
        while (m.find()) hedef.put(m.group(1), m.group(2));
    }

    static void tekliye(String govde, Set<String> hedef) {
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(govde);
        while (m.find()) hedef.add(m.group(1));
    }

    static void yukle(String dizin) throws IOException {
        String s = oku(dizin + "/OfflineFix.kt");
        int st = s.indexOf("object SoundTags {");
        if (st < 0) throw new RuntimeException("object SoundTags yok");
        String ss = s.substring(st);
        ciftlere(blok(ss, "private val words: Map<String, String> = mapOf(", "mapOf("), words);
        ciftlere(blok(ss, "private val phrases = mapOf(", "mapOf("), phrases);
        ciftlere(blok(ss, "private val devamAdi: Map<String, String> = mapOf(", "mapOf("), devamAdi);
        ciftlere(blok(ss, "private val roller: Map<String, String> = mapOf(", "mapOf("), roller);
        ciftlere(blok(ss, "private val araclar: Map<String, String> = mapOf(", "mapOf("), araclar);
        ciftlere(blok(ss, "private val ozneliFiil: Map<String, String> = mapOf(", "mapOf("), ozneliFiil);
        if (ozneliFiil.size() < 6) throw new RuntimeException("BAYAT AYNA: ozneliFiil=" + ozneliFiil.size());
        if (roller.size() < 20) throw new RuntimeException("BAYAT AYNA: roller=" + roller.size());
        tekliye(blok(ss, "private val skip = setOf(", "setOf("), skip);
        tekliye(blok(ss, "private val adjectives = setOf(", "setOf("), adjectives);
        tekliye(blok(ss, "private val ZARF = setOf(", "setOf("), ZARF);

        // Bayatlama alarmi.
        int a = ss.indexOf("    fun repair(text: String): String {");
        int b = ss.indexOf("\n    /** Metin tamamen", a);
        int n = 0;
        Matcher m = Pattern.compile("Regex\\(").matcher(ss.substring(a, b));
        while (m.find()) n++;
        if (n != BEKLENEN_REPAIR_REGEX) {
            System.out.println("  X AYNA BAYATLADI: SoundTags.repair icinde " + n
                + " Regex var, bu ayna " + BEKLENEN_REPAIR_REGEX + " tanesini biliyor."
                + " Ses.java'yi guncelle ve BEKLENEN_REPAIR_REGEX'i duzelt.");
            hata++;
        }
    }

    // ------------------------------------------------------- isSoundWords
    static boolean isSoundWords(String s) {
        String[] toks = s.toLowerCase().split("[\\s,]+");
        boolean bos = true;
        for (String w : toks) {
            if (w.isEmpty()) continue;
            bos = false;
            String x = kirp(w, "'.-");
            boolean ok = words.containsKey(x) || skip.contains(x)
                || (x.endsWith("l") && words.containsKey(x.substring(0, x.length() - 1)))
                || x.equals("and");
            if (!ok) return false;
        }
        return !bos;
    }

    static String kirp(String s, String kar) {
        int a = 0, b = s.length();
        while (a < b && kar.indexOf(s.charAt(a)) >= 0) a++;
        while (b > a && kar.indexOf(s.charAt(b - 1)) >= 0) b--;
        return s.substring(a, b);
    }

    // ------------------------------------------------------------- repair
    static String repair(String text) {
        String t = text;
        // 1) Acilis I/l/L/| okunmus
        t = degistir(t, "(^|\\s|-)[IlL|]\\s?([a-z][a-z ,'’-]*[\\])])", m ->
            isSoundWords(m.group(2).substring(0, m.group(2).length() - 1))
                ? m.group(1) + "[" + m.group(2) : m.group(0));
        // 2) Acilis hic yok
        Matcher m2 = Pattern.compile("^([a-z][a-z ,'’-]*)[\\])]").matcher(t);
        if (m2.find() && isSoundWords(m2.group(1))) t = "[" + t;
        // 3) Kapanis "l" ya da hic yok
        Matcher m3 = Pattern.compile("\\[([a-z][a-z ,'’-]*)$").matcher(t);
        if (m3.find()) {
            String inner = m3.group(1).replaceAll("\\s+$", "");
            String lastTok = inner.contains(" ") ? inner.substring(inner.lastIndexOf(' ') + 1) : inner;
            String fixed = (lastTok.endsWith("l")
                && words.containsKey(lastTok.substring(0, lastTok.length() - 1).toLowerCase()))
                ? inner.substring(0, inner.length() - 1) : inner;
            if (isSoundWords(fixed)) t = t.substring(0, m3.start()) + "[" + fixed + "]";
        }
        // 4) Iki parantez de yok, acilis I/l/L
        Matcher m4 = Pattern.compile("^[IlL|]([a-z][a-z ,'’-]*)$").matcher(t.trim());
        if (m4.find() && isSoundWords(m4.group(1)))
            t = "[" + m4.group(1).replaceAll("\\s+$", "") + "]";
        // 5+6) Acilis "{" ya da "(" — kucuk harf, sonra buyuk harf (konusmaci adi)
        t = degistir(t, "([{(])([a-z][a-z ,'’-]*)(?=[\\])])", m ->
            isSoundWords(m.group(2)) ? "[" + m.group(2) : m.group(0));
        t = degistir(t, "(^|\\s|-)[{(](?=\\p{Lu}[\\p{L}'’.]*(?: \\p{Lu}[\\p{L}'’.]*)?])",
            m -> m.group(1) + "[");
        // 7) Bozuk acilistan SONRA BOSLUK
        Matcher m7 = Pattern.compile("^[IlL|]\\s+([a-z][a-z ,'’-]*?)[l]?$").matcher(t.trim());
        if (m7.find()) {
            String inner = m7.group(1).replaceAll("\\s+$", "");
            if (isSoundWords(inner)) t = "[" + inner + "]";
        }
        // 8) Konusmaci etiketinin kapanisi ")" okunmus
        t = t.replaceAll("^\\[(\\p{Lu}[\\p{L}'’.]*(?: \\p{Lu}[\\p{L}'’.]*)?)\\)", "[$1]");
        // 9) Satir sonundaki etiket: iki parantez de bozuk
        Matcher m9 = Pattern.compile("(\\s|-)[IlL|]([a-z][a-z ,'’-]*?)l$").matcher(t);
        if (m9.find()) {
            String inner = m9.group(2).replaceAll("\\s+$", "");
            if (isSoundWords(inner)) t = t.substring(0, m9.start()) + m9.group(1) + "[" + inner + "]";
        }
        // 10) Kapanis ")" okunmus
        t = degistir(t, "\\[([a-z][a-z ,'’-]*)\\)", m -> "[" + m.group(1) + "]");
        return t;
    }

    interface Donustur { String uygula(Matcher m); }

    static String degistir(String t, String desen, Donustur f) {
        Matcher m = Pattern.compile(desen).matcher(t);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(t, last, m.start()).append(f.uygula(m));
            last = m.end();
        }
        return sb.append(t.substring(last)).toString();
    }

    // ---------------------------------------------------------- translate
    static final Pattern TAG_RE = Pattern.compile("[\\[({]([^\\[\\](){}]*)[\\])}]?");

    static String translate(String text) {
        String t = text.trim();
        if (t.isEmpty()) return null;
        boolean realOpen = "[({".indexOf(t.charAt(0)) >= 0;
        boolean kosegeliVar = t.contains("[");
        // KOSULA BAGLI: ardindaki metin gercekten ses sozcugu olmali.
        // Onsuz "It's perfect." -> "[t's perfect." -> "[ses efekti]" oluyordu.
        final String kaynakT = t;
        t = degistir(t, "(^|\\s)[Il|](?=[a-z])", m -> {
            String kalan = kaynakT.substring(m.end());
            int kes = kalan.length();
            for (int i = 0; i < kalan.length(); i++)
                if ("])}".indexOf(kalan.charAt(i)) >= 0) { kes = i; break; }
            return isSoundWords(kalan.substring(0, kes).trim()) ? m.group(1) + "[" : m.group();
        });
        boolean hasClose = false;
        for (char c : t.toCharArray()) if ("])}".indexOf(c) >= 0) hasClose = true;
        if (!realOpen && !hasClose) {
            if (!t.startsWith("[")) return null;
            String inner = t.substring(1);
            if (inner.trim().isEmpty()) return null;
            for (char c : inner.toCharArray()) if (Character.isUpperCase(c)) return null;
            String[] toks = inner.toLowerCase().split("[\\s,]+");
            boolean bos = true;
            for (String w : toks) {
                if (w.isEmpty()) continue;
                bos = false;
                String x = kirp(w, "'.");
                if (!words.containsKey(x) && !skip.contains(x)) return null;
            }
            if (bos) return null;
            return "[" + translateContent(inner) + "]";
        }
        Matcher m = TAG_RE.matcher(t);
        List<String> icerikler = new ArrayList<>();
        while (m.find()) icerikler.add(m.group(1).trim());
        if (icerikler.isEmpty()) return null;
        if (!kirp(TAG_RE.matcher(t).replaceAll(""), "-– ").trim().isEmpty()) return null;
        List<String> out = new ArrayList<>();
        for (String content : icerikler) {
            if (content.isEmpty()) return null;
            Matcher nm = Pattern.compile("^((?:\\p{Lu}[\\p{L}'’]+\\s+(?:and\\s+)?){1,3})(\\p{Ll}.*)$").matcher(content);
            if (nm.find()) {
                boolean buyuk = false;
                for (char c : nm.group(2).toCharArray()) if (Character.isUpperCase(c)) buyuk = true;
                if (!buyuk) {
                    String kuyruk = nm.group(2).trim();
                    String kl = kuyruk.toLowerCase();
                    String adlar = nm.group(1).trim().replace(" and ", " ve ");
                    String rol = rolKuyrugu(kuyruk);
                    if (rol != null) { out.add("[" + adlar + rol + "]"); continue; }
                    String fiil = ozneliFiil.get(kl.replaceAll("l+$", ""));
                    if (fiil == null) fiil = ozneliFiil.get(kl);
                    out.add("[" + adlar + " " + (fiil != null ? fiil : translateContent(kuyruk)) + "]");
                    continue;
                }
            }
            for (char c : content.toCharArray()) if (Character.isUpperCase(c)) return null;
            String cevrilen = translateContent(content);
            if (cevrilen.equals("ses efekti") && !kosegeliVar) return null;
            out.add("[" + cevrilen + "]");
        }
        return String.join(" ", out);
    }

    static String rolCevir(String content) {
        String t = content.toLowerCase().trim();
        if (roller.containsKey(t)) return roller.get(t);
        if (t.endsWith("l") && roller.containsKey(t.substring(0, t.length()-1)))
            return roller.get(t.substring(0, t.length()-1));
        Matcher m = ROL_ARACLI.matcher(t);
        if (!m.find()) return null;
        String kisi = roller.get(m.group(1)), arac = araclar.get(m.group(2));
        if (kisi == null || arac == null) return null;
        return arac + bulunmaEki(arac) + "ki " + kisi;
    }

    static final Pattern KUYRUK_ARAC = Pattern.compile("^(?:(\\w+)\\s+)?(?:over|on|via|through)\\s+(?:the\\s+)?(\\w+)$");

    static String ayrilmaEki(String kelime) {
        String k = kelime.toLowerCase();
        char sonUnlu = 'a';
        for (char c : k.toCharArray()) if ("aeıioöuü".indexOf(c) >= 0) sonUnlu = c;
        boolean ince = "eiöü".indexOf(sonUnlu) >= 0;
        boolean sert = !k.isEmpty() && "pçtkfhsş".indexOf(k.charAt(k.length() - 1)) >= 0;
        return (sert ? "t" : "d") + (ince ? "en" : "an");
    }

    static String rolKuyrugu(String kuyruk) {
        String t = kuyruk.toLowerCase().trim().replaceAll("[)\\]]+$", "");
        if (roller.containsKey(t)) return " " + roller.get(t);
        Matcher m = KUYRUK_ARAC.matcher(t);
        if (!m.find()) return null;
        String arac = araclar.get(m.group(2));
        if (arac == null) return null;
        String rol = "";
        if (m.group(1) != null && !m.group(1).isEmpty()) {
            rol = roller.get(m.group(1));
            if (rol == null) return null;
        }
        return (rol.isEmpty() ? "" : " " + rol) + ", " + arac + ayrilmaEki(arac);
    }

    static String bulunmaEki(String kelime) {
        String k = kelime.toLowerCase();
        char sonUnlu = 'a';
        for (char c : k.toCharArray()) if ("aeıioöuü".indexOf(c) >= 0) sonUnlu = c;
        boolean ince = "eiöü".indexOf(sonUnlu) >= 0;
        char son = k.isEmpty() ? ' ' : k.charAt(k.length()-1);
        boolean sert = "pçtkfhsş".indexOf(son) >= 0;
        if (sert && ince) return "te";
        if (sert) return "ta";
        return ince ? "de" : "da";
    }

    static String translateContent(String content) {
        String rol = rolCevir(content);
        if (rol != null) return rol;
        String[] parts = content.split(",");
        List<String> dolu = new ArrayList<>();
        for (String p : parts) if (!p.trim().isEmpty()) dolu.add(p.trim());
        if (dolu.size() > 1) {
            List<String> tr = new ArrayList<>();
            for (String p : dolu) {
                String x = translatePart(p);
                if (!x.equals("ses efekti")) tr.add(x);
            }
            return tr.isEmpty() ? "ses efekti" : String.join(", ", tr);
        }
        return translatePart(content);
    }

    /** OfflineFix.cekimVaryanti aynasi (9.97). */
    static String cekimVaryanti(String w) {
        List<String> c = new ArrayList<>();
        if (w.length() > 5 && w.endsWith("ing")) {
            String k = w.substring(0, w.length() - 3);
            c.add(k + "s"); c.add(k + "es"); c.add(k + "e"); c.add(k + "les"); c.add(k);
            if (k.length() > 2 && k.charAt(k.length() - 1) == k.charAt(k.length() - 2)) c.add(k.substring(0, k.length() - 1) + "s");
        }
        if (w.length() > 4 && w.endsWith("es")) c.add(w.substring(0, w.length() - 2) + "ing");
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            String k = w.substring(0, w.length() - 1);
            c.add(k); c.add(k + "ing");
            if (k.endsWith("e")) c.add(k.substring(0, k.length() - 1) + "ing");
        }
        if (w.length() > 4 && w.endsWith("ed")) {
            String k = w.substring(0, w.length() - 2);
            c.add(k + "s"); c.add(k + "es"); c.add(k + "ing"); c.add(k.substring(0, k.length() - 1) + "ing");
        }
        for (String x : c) if (!x.equals(w) && words.containsKey(x)) return x;
        return null;
    }

    static String translatePart(String content) {
        List<String> tokens = new ArrayList<>();
        for (String w : content.toLowerCase().split("[\\s,]+")) if (!w.isEmpty()) tokens.add(w);
        StringBuilder kb = new StringBuilder();
        for (String t : tokens) { if (kb.length() > 0) kb.append(' '); kb.append(kirp(t, "'-.")); }
        String key = kb.toString();
        if (phrases.containsKey(key)) return phrases.get(key);
        if (key.endsWith("l") && phrases.containsKey(key.substring(0, key.length() - 1)))
            return phrases.get(key.substring(0, key.length() - 1));

        if (tokens.size() >= 2 && tokens.size() <= 4) {
            String son = kirp(tokens.get(tokens.size() - 1), "'-.");
            if (son.equals("continues") || son.equals("continue") || son.equals("continuing")) {
                List<String> onek = new ArrayList<>();
                for (int i = 0; i < tokens.size() - 1; i++) onek.add(kirp(tokens.get(i), "'-."));
                String ad = onek.isEmpty() ? null : devamAdi.get(onek.get(onek.size() - 1));
                if (ad != null) {
                    if (ad.equals("müzik") && onek.size() >= 2 && adjectives.contains(onek.get(onek.size() - 2)))
                        ad = "müziği";
                    List<String> sif = new ArrayList<>();
                    for (int i = 0; i < onek.size() - 1; i++)
                        if (words.containsKey(onek.get(i))) sif.add(words.get(onek.get(i)));
                    String s = String.join(" ", sif);
                    return (s.isEmpty() ? ad : s + " " + ad) + " devam ediyor";
                }
            }
        }

        List<String> res = new ArrayList<>();
        List<String> zarflar = new ArrayList<>();
        int known = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String w = kirp(tokens.get(i), "'-.");
            if (!words.containsKey(w) && w.endsWith("l")
                && words.containsKey(w.substring(0, w.length() - 1)))
                w = w.substring(0, w.length() - 1);
            if (!words.containsKey(w)) {
                List<String> adaylar = new ArrayList<>();
                adaylar.add(w.replace('n', 'm'));
                adaylar.add(w.replace("rn", ""));
                adaylar.add(w.replace("rn", "m"));
                adaylar.add(w.replace("ir", "i"));
                for (int k = 0; k < w.length(); k++)
                    if (w.charAt(k) == 'n') adaylar.add(w.substring(0, k) + "m" + w.substring(k + 1));
                for (String a : adaylar) if (words.containsKey(a)) { w = a; break; }
            }
            if (!words.containsKey(w)) { String v = cekimVaryanti(w); if (v != null) w = v; }
            if (skip.contains(w)) continue;
            String tr = words.get(w);
            if (tr == null) continue;
            known++;
            boolean sifatli = w.equals("music") && i > 0 && adjectives.contains(kirp(tokens.get(i - 1), "'"));
            String metin = sifatli ? "müziği" : tr;
            if (ZARF.contains(w)) zarflar.add(metin); else res.add(metin);
        }
        if (known == 0) return "ses efekti";
        for (String z : zarflar) res.add(Math.max(0, res.size() - 1), z);
        return String.join(" ", res);
    }

    /** Gercek akis: repair -> translate. */
    static String ceviri(String ham) {
        String r = repair(ham);
        String t = translate(r);
        return t == null ? null : t;
    }

    static void esit(String ham, String beklenen) {
        String olan = ceviri(ham);
        if (!Objects.equals(olan, beklenen)) {
            hata++;
            System.out.println("  X " + ham);
            System.out.println("      beklenen: " + beklenen);
            System.out.println("      cikan   : " + olan);
        }
    }

    public static void main(String[] a) throws Exception {
        yukle(a[0]);
        System.out.println("  " + words.size() + " ses sozcugu, " + phrases.size() + " kalip.");

        // --- 9.63 kaydinda KIRPILAN etiketler
        // 9.97 — 51 kaydin taramasi: eksik ses sozcukleri + genel cekim eslemesi
        esit("[heart beating rapidly]", "[kalp hızla atıyor]");
        esit("[people screaming]", "[insanlar çığlık atıyor]");
        esit("[cackling]", "[kahkaha atıyor]");
        esit("[sniffles]", "[burnunu çekiyor]");      // yalniz "sniffling" var
        esit("[jangled]", "[şıngırdıyor]");          // yalniz "jangles" var
        esit("[strains]", "[zorlanıyor]");
        // mantik korumasi: ayna elle kopyalandi, kaynakta ayni kurallar durmali
        {
            String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[0] + "/OfflineFix.kt")), "UTF-8");
            for (String parca : new String[]{
                "if (w !in words) cekimVaryanti(w)?.let { w = it }",
                "c += listOf(k + \"s\", k + \"es\", k + \"e\", k + \"les\", k)",
                "if (w.length > 4 && w.endsWith(\"es\")) c += w.dropLast(2) + \"ing\"",
                "c += listOf(k + \"s\", k + \"es\", k + \"ing\", k.dropLast(1) + \"ing\")",
                "return c.firstOrNull { it != w && it in words }"}) {
                if (!src.contains(parca)) { hata++; System.out.println("  X KAYNAKTA YOK (ayna bayat): " + parca); }
            }
        }
        esit("[grunts and pants]", "[homurtular ve nefes nefese]");
        esit("[pants and grunts]", "[nefes nefese ve homurtular]");
        esit("[tires screech]", "[lastikler gıcırdıyor]");
        esit("[gasps, pants]", "[nefesi kesiliyor, nefes nefese]");

        // --- 9.63 kaydinda OCR'in BOZDUGU parantezler
        esit("[high-pitched ringingl", "[tiz çalıyor]");
        esit("(snaps]", "[çıt sesi]");
        esit("I shrieks, whimpers", "[çığlıklar, sızlanma]");


        // --- 9.64 kaydi (Invincible): eksik sozcukler yuzunden ya kelime
        // dusuyordu ya parantez onarimi hic calismiyordu.
        esit("I energetic music playing]", "[hareketli müzik çalıyor]");
        esit("I grunting fiercely]", "[sertçe homurdanıyor]");
        esit("I gagging, whimperingl", "[öğürüyor, sızlanıyor]");
        esit("[militaristic music playingl", "[askeri müzik çalıyor]");
        esit("[short breaths]", "[kısa kısa nefesler]");
        esit("[muffled thumpingl", "[boğuk gümlüyor]");
        esit("[children in unison]", "[çocuklar hep bir ağızdan]");
        esit("[soldiers groaning, whimpering]", "[askerler inliyor, sızlanıyor]");
        esit("(grunting weaklyl", "[zayıfça homurdanıyor]");
        esit("[fierce gruntingl", "[sert homurdanıyor]");
        esit("[strained grunting]", "[zorlanarak homurdanıyor]");
        esit("[intense music playing]", "[yoğun müzik çalıyor]");
        esit("(whooshingl", "[uğultu]");

        // --- 9.65 kaydi (Invincible 2)
        esit("I atmospheric music playing]", "[atmosferik müzik çalıyor]");
        esit("[atmospheric music playing]", "[atmosferik müzik çalıyor]");
        esit("[haunting vocalizing over atmospheric music]",
             "[atmosferik müzik eşliğinde ürkütücü ses]");
        esit("[alert tone blaring]", "[uyarı sesi çalıyor]");
        esit("[sharp gasping breath]", "[keskin bir nefes]");
        esit("[soldiers coughing, groaning]", "[askerler öksürüyor, inliyor]");
        esit("[wheezing]", "[hırıldıyor]");

        // --- 9.66 kaydi (Invincible 2)
        esit("[loud thump]", "[yüksek bir gümleme]");
        esit("-[creature yowling]", "[yaratık uluyor]");
        esit("-[creature mewls]", "[yaratık mırlıyor]");
        esit("-[curtain opens", "[perde açılıyor]");
        esit("[intriguing music playingl", "[merak uyandıran müzik çalıyor]");
        esit("[gentle music playing]", "[yumuşak müzik çalıyor]");
        esit("[groans quietly]", "[hafifçe inliyor]");

        // --- Turkcede zarf fiilden ONCE gelir. Sozcukler eklenince bu sira
        // hatasi gorunur hale geldi: "homurdanıyor sertçe" gibi.
        esit("[breathing heavily]", "[derin derin nefes alıyor]");
        esit("(cadet gasps in pain) -[panting]", "[öğrenci acıyla nefesi kesiliyor] [soluyor]");
        esit("[body thuds]", "[ceset gümlüyor]");

        // --- Eskiden de dogru olanlar BOZULMAMALI
        esit("[dramatic music playing]", "[dramatik müzik çalıyor]");
        esit("[suspenseful music playing]", "[gerilim müziği çalıyor]");
        esit("[phone clatters]", "[telefon takırdıyor]");
        esit("[grunts, groans]", "[homurtular, inlemeler]");
        esit("[engine revs]", "[motor gürlüyor]");
        esit("[neck cracks]", "[çatırtı]");
        esit("[whimpers]", "[sızlanma]");
        esit("[screams]", "[çığlıklar]");
        esit("[panting]", "[soluyor]");
        esit("[groans]", "[inlemeler]");
        esit("[phone ringing]", "[telefon çalıyor]");
        esit("[music intensifies]", "[müzik şiddetleniyor]");
        esit("[suspenseful music continues]", "[gerilim müziği devam ediyor]");
        esit("[stabbing sound]", "[bıçaklama sesi]");

        // --- GERCEK REPLIK etiket sanilmamali
        esit("The music continues to play.", null);
        esit("I know what you did.", null);
        esit("Justice.", null);
        esit("He's alive?", null);
        esit("I need to know how they're connected.", null);
        // "(" artik ses sozcugu sartina bagli: siradan parantez etiket sanilmasin.
        esit("(see you later)", null);
        esit("(the usual stuff)", null);
        // Ama gercek ses etiketi parantezle gelirse onarilir.
        esit("(whispering)", "[fısıldıyor]");
        // Kosegeli parantezde eski davranis korunuyor: bilinmeyen ses etiketi
        // icin "[ses efekti]" yazmak, Ingilizce birakmaktan iyi.
        esit("[zzzt]", "[ses efekti]");
        esit("[clank]", "[ses efekti]");

        // --- 9.74: OCR'de "I"/"l" kirilmasi SADECE ses sozcugu geliyorsa
        // koseli parantez sayilir. Yoksa "It's" gibi gercek sozcukler bozuluyordu.
        esit("Igun cocks]", "[silah kuruluyor]");
        esit("lgunshot)", "[silah sesi]");
        esit("Igrunts]", "[homurtular]");
        esit("[whispering] It's perfect.", null);   // ONCE: "[fısıldıyor] [ses efekti]"
        esit("[groans] You were not kidding.", null);
        esit("I'm holding out for a hero", null);
        esit("It's true.", null);
        esit("I'll see you soon.", null);
        esit("Is that all you've got?", null);
        esit("lt's fine.", null);

        // --- 9.74: eksik muzik sifatlari
        esit("[uplifting music playing]", "[coşkulu müzik çalıyor]");
        esit("[uplifting music playingl", "[coşkulu müzik çalıyor]");
        esit("[hopeful music playing]", "[umutlu müzik çalıyor]");
        esit("[epic music playing]", "[destansı müzik çalıyor]");
        esit("[mysterious music playing]", "[gizemli müzik çalıyor]");
        esit("[alert tone stops]", "[uyarı sesi duruyor]");

        // --- 9.75: kayitta SESSIZCE dusen sozcukler artik ceviriliyor
        esit("[exciting music playing]", "[heyecanlı müzik çalıyor]");
        esit("[emotional music playing]", "[duygusal müzik çalıyor]");
        esit("[emotional music continues]", "[duygusal müzik devam ediyor]");
        esit("[metallic creaking]", "[metal gıcırtısı]");
        esit("[rumbling] -[metallic creaking]", "[gürlüyor] [metal gıcırtısı]");
        esit("[shivering]", "[titreyerek]");
        esit("[belabored breathing]", "[zorlu nefes alışı]");
        esit("[belabored breathingl", "[zorlu nefes alışı]");
        esit("[tracks squeaking]", "[raylar gıcırdıyor]");
        esit("[tracks squeakingl", "[raylar gıcırdıyor]");

        // --- 9.77: ROL etiketleri ses efekti SAYILMAMALI
        esit("[provost]", "[rektör]");
        esit("[provostl]", "[rektör]");
        esit("[reporter]", "[muhabir]");
        esit("[man on radio]", "[telsizdeki adam]");
        esit("[woman over the phone]", "[telefondaki kadın]");
        // "[radio]" TEK BASINA ses etiketi, rol degil
        esit("[radio]", "[telsiz]");
        esit("[radio static]", "[telsiz cızırtısı]");
        esit("[man on tv]", "[televizyondaki adam]");
        esit("[woman on intercom]", "[anonstaki kadın]");
        esit("[narrator]", "[anlatıcı]");
        // --- 9.77: kayitta "[ses efekti]" olan ses sozcukleri
        esit("[keys jangling]", "[anahtarlar şıngırdıyor]");
        esit("[keys janglingl", "[anahtarlar şıngırdıyor]");
        esit("[applause]", "[alkış]");
        esit("[yawning]", "[esniyor]");
        // Rol OLMAYAN kucuk harfli etiket hala ses etiketi
        esit("[chuckles]", "[kıkırdıyor]");
        esit("[door unlocks]", "[kapı açılıyor]");

        // --- 9.84: AD + FIIL etiketi (12 Kasim kaydi)
        esit("[Alien groans]", "[Alien inliyor]");          // ONCE: "[Alien inlemeler]"
        esit("[Nolan groans]", "[Nolan inliyor]");
        esit("[Tuva screams]", "[Tuva çığlık atıyor]");      // ONCE: "[Tuva çığlıklar]"
        esit("[Tuva yelps]", "[Tuva ciyaklıyor]");
        esit("[Nolan sighs]", "[Nolan iç çekiyor]");
        esit("[Space Racer grunts]", "[Space Racer homurdanıyor]");
        esit("[Infinity Ray powering up]", "[Infinity Ray güçleniyor]");
        esit("[Infinity Ray powering upl", "[Infinity Ray güçleniyor]");
        // Oznesiz hali DEGISMEMELI
        esit("[groans]", "[inlemeler]");
        esit("[thrilling music playing]", "[sürükleyici müzik çalıyor]");
        esit("(thrilling music playingl", "[sürükleyici müzik çalıyor]");

        // --- 9.85: 12 Kasim ikinci kayit
        esit("[Alien and Telia moaning loudly]", "[Alien ve Telia yüksek sesle inliyor]");   // ONCE: "inilti hidroliği"
        esit("[Alien over implant]", "[Alien, implanttan]");            // ONCE: "[Alien eşliğinde]"
        esit("[Geldarian warrior]", "[Geldarian savaşçı]");             // ONCE: "[Geldarian ses efekti]"
        esit("[Geldarian warrior over speaker]", "[Geldarian savaşçı, hoparlörden]");
        esit("[moaning continues]", "[inleme devam ediyor]");           // ONCE: "inliyor devam ediyor"
        esit("{rousing music playing] -[whooshing]", "[coşturucu müzik çalıyor] [uğultu]");
        esit("[mournful music playing]", "[yaslı müzik çalıyor]");
        esit("[creatures chitteringl", "[yaratıklar cırlıyor]");        // ONCE: "[yaratıklar]"
        esit("[bed banging rhythmically]", "[yatak ritmik şekilde çarpıyor]");
        // Kalip disinda da zarf fiilden ONCE gelmeli (ZARF kumesi)
        esit("[door banging rhythmically]", "[kapı ritmik şekilde çarpıyor]");
        esit("[Alien and Telia moaning loudlyl -[bed banging rhythmically]",
             "[Alien ve Telia yüksek sesle inliyor] [yatak ritmik şekilde çarpıyor]");
        // Muzikteki "over" anlami DEGISMEMELI
        esit("[music playing over speakers]", Ses.translate("[music playing over speakers]"));

        if (hata > 0) { System.out.println("SES DUSTU: " + hata + " hata."); System.exit(1); }
        System.out.println("Ses etiketi testi gecti: kirpilan etiketler ve bozuk parantezler onarildi.");
    }
}

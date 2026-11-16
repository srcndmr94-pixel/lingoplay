import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Taslak yolunun aynasi. Kotlin dosyalarini CALISMA ANINDA okur; kural
 * listelerini elle kopyalamaz. Java'nin regex motoru Android'in motorudur,
 * yani (?i) + \p{Lu} gibi tuzaklar burada da aynen gorunur.
 *
 * ML Kit yerine <ML:...> isareti doner: hangi parca elle yazilmis kaliptan,
 * hangisi ceviri motorundan geliyor ciplak gozle belli olur.
 */
public class Tezgah {

    static String off, slangSrc;
    static Map<String, String> PHRASES = new HashMap<>();
    static Set<String> NAMES = new HashSet<>();
    static Map<String, String> PH2 = new HashMap<>();
    static Map<String, String> HITAP = new LinkedHashMap<>();
    static List<String[]> SLANG = new ArrayList<>();
    static List<String[]> NORM = new ArrayList<>();
    static List<Object[]> SH_RULES = new ArrayList<>();
    static List<String[]> SH_WORDS = new ArrayList<>();

    static final String OBJ = "(you|him|her|them|me|us|'em|that guy|the guy|this guy)";
    static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?…])\\s+");
    static final Pattern TRAILING = Pattern.compile("[.!?…]+$");

    // --------------------------------------------------------------- yardim
    static String oku(String p) throws IOException {
        return new String(Files.readAllBytes(Paths.get(p)), "UTF-8");
    }

    static String keyOf(String t) {
        StringBuilder sb = new StringBuilder();
        for (char c : t.toLowerCase().toCharArray()) if (Character.isLetterOrDigit(c)) sb.append(c);
        return sb.toString();
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

    /** Kotlin dizge sabitinden gercek metin. */
    static String kstr(String s) {
        return s.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\$", "$");
    }

    static final Pattern CIFT = Pattern.compile(
        "\"((?:[^\"\\\\]|\\\\.)*)\"\\s*to\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    static List<String[]> ciftler(String govde) {
        List<String[]> out = new ArrayList<>();
        Matcher m = CIFT.matcher(govde);
        while (m.find()) out.add(new String[]{ kstr(m.group(1)).replace("$OBJ", OBJ), kstr(m.group(2)) });
        return out;
    }

    // --------------------------------------------------------------- yukleme
    static void yukle(String dizin) throws IOException {
        off = oku(dizin + "/OfflineFix.kt");
        slangSrc = oku(dizin + "/Slang.kt");

        // Sozluk IKI blokta: DEFAULT_ENTRIES + GAME_ENTRIES (default(GAME_ENTRIES)
        // ile birlestiriliyor). Yalniz birini okumak sessizce yarim sozlukle
        // test etmek demek.
        StringBuilder gl = new StringBuilder();
        for (String ad : new String[]{ "DEFAULT_ENTRIES", "GAME_ENTRIES" }) {
            int a = off.indexOf("const val " + ad + " = \"\"\"");
            if (a < 0) throw new RuntimeException("sozluk blogu yok: " + ad);
            a = off.indexOf("\"\"\"", a) + 3;
            gl.append(off, a, off.indexOf("\"\"\"", a)).append('\n');
        }
        for (String line : gl.toString().split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            if (l.startsWith("@")) { NAMES.add(l.substring(1).trim().toLowerCase()); continue; }
            int eq = l.indexOf('=');
            if (eq <= 0) continue;
            String k = keyOf(l.substring(0, eq));
            String v = l.substring(eq + 1).trim();
            while (!v.isEmpty() && ".!?… ".indexOf(v.charAt(v.length() - 1)) >= 0)
                v = v.substring(0, v.length() - 1);
            if (!k.isEmpty() && !v.isEmpty()) PHRASES.put(k, v);
        }

        for (String[] p : ciftler(blok(off, "private val phrases = mapOf(", "mapOf(")))
            PH2.put(keyOf(p[0]), p[1]);
        for (String[] p : ciftler(blok(off, "private val HITAP = mapOf(", "mapOf(")))
            HITAP.put(p[0], p[1]);
        SLANG = ciftler(blok(slangSrc, "private val rewrites", "listOf("));
        NORM = ciftler(blok(off, "object SlangNormalizer", "listOf("));

        String shBody = blok(off, "private val rules: List<Rule> = listOf(", "listOf(");
        Matcher m = Pattern.compile(
            "rule\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,\\s*(HARD|DAMN|MILD|null)\\)")
            .matcher(shBody);
        while (m.find()) {
            String tail = m.group(3).equals("HARD") ? "amına koyayım"
                : m.group(3).equals("DAMN") ? "lanet olsun"
                : m.group(3).equals("MILD") ? "be" : null;
            SH_RULES.add(new Object[]{ Pattern.compile("(?i)" + kstr(m.group(1))), kstr(m.group(2)), tail });
        }
        int w = off.indexOf("private val words: List<Pair<Regex, String>> = listOf(");
        String wBody = off.substring(w, off.indexOf("\n    ).map", w));
        for (String[] p : ciftler(wBody)) SH_WORDS.add(new String[]{ "(?i)\\b" + p[0] + "\\b", p[1] });
    }

    // --------------------------------------------------------------- motor
    static String look(String t) { return PHRASES.get(keyOf(t)); }
    static String phrasesLookup(String t) { return PH2.get(keyOf(t)); }
    static boolean isName(String t) { return NAMES.contains(t.trim().toLowerCase()); }

    static boolean startsWithName(String t) {
        String[] p = t.trim().split("[ ']");
        return p.length > 0 && NAMES.contains(p[0].toLowerCase());
    }

    /**
     * SlangNormalizer.normalize AYNASI.
     *
     * Urun IKISINI de yapiyor:
     *   Slang.rewrites          -> Regex("(?i)" + kalip)
     *   SlangNormalizer.rules   -> Regex(kalip, IGNORE_CASE)
     * ve iki normalize() de eslesme buyuk harfle basliyorsa karsiligin ilk
     * harfini buyutuyor.
     *
     * Ayna eskiden "(?i)" ekliyor ama harf geri koymayi ATLIYORDU; sonra
     * harf geri koymayi ekleyip "(?i)"yi KALDIRDIM ve ayna ters yone
     * sapti (kurallar artik buyuk harfli cumle basinda calismiyor sandim,
     * 78 kural bozuk cikti -- yanlis alarm). Dogrusu: IKISI de.
     */
    static String uygula(String t, List<String[]> kurallar) {
        for (String[] r : kurallar) {
            Matcher m = Pattern.compile("(?i)" + r[0]).matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String rep = r[1];
                for (int g = 1; g <= m.groupCount(); g++)
                    rep = rep.replace("$" + g, m.group(g) == null ? "" : m.group(g));
                String esl = m.group();
                if (!esl.isEmpty() && Character.isUpperCase(esl.charAt(0)) && !rep.isEmpty())
                    rep = Character.toUpperCase(rep.charAt(0)) + rep.substring(1);
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            t = sb.toString();
        }
        return t;
    }

    static String mlTranslate(String text) {
        String norm = uygula(uygula(text, SLANG), NORM);
        String t = norm;
        List<String> tails = new ArrayList<>();
        for (Object[] r : SH_RULES) {
            Pattern p = (Pattern) r[0];
            if (p.matcher(t).find()) {
                t = p.matcher(t).replaceAll((String) r[1]);
                if (r[2] != null && !tails.contains(r[2])) tails.add((String) r[2]);
            }
        }
        t = t.replaceAll("\\s{2,}", " ").trim();
        List<String> kufur = new ArrayList<>();
        for (String[] r : SH_WORDS) {
            Pattern p = Pattern.compile(r[0]);
            if (p.matcher(t).find()) { kufur.add(r[1]); t = p.matcher(t).replaceAll("KUFUR"); }
        }
        String s = "<ML:" + t + ">";
        if (!kufur.isEmpty()) s += kufur;
        if (!tails.isEmpty()) s += tails;
        return s;
    }

    static final java.util.Locale TR = new java.util.Locale("tr");

    static String lowerFirst(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(TR) + s.substring(1);
    }

    static String joinParts(List<String> parts, List<String> resolved, String punct) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < parts.size()) {
            String hitap = null;
            if (i > 0 && resolved.get(i) == null) {
                String k = parts.get(i).toLowerCase();
                while (!k.isEmpty() && "!?. ".indexOf(k.charAt(k.length() - 1)) >= 0) k = k.substring(0, k.length() - 1);
                while (!k.isEmpty() && "!?. ".indexOf(k.charAt(0)) >= 0) k = k.substring(1);
                hitap = HITAP.get(k);
            }
            if (hitap != null) { out.add(hitap + (i == parts.size() - 1 ? punct : "")); i++; continue; }
            String known = resolved.get(i) != null ? resolved.get(i)
                : (isName(parts.get(i)) ? parts.get(i) : null);
            if (known != null) {
                boolean isGloss = resolved.get(i) != null;
                String text = (i > 0 && isGloss && !startsWithName(known)) ? lowerFirst(known) : known;
                if (i == parts.size() - 1) text += punct;
                out.add(text);
                i++;
            } else {
                int j = i;
                while (j < parts.size() && resolved.get(j) == null && !isName(parts.get(j))) j++;
                String run = String.join(", ", parts.subList(i, j));
                boolean ends = j == parts.size();
                String tr = mlTranslate(run + (ends ? punct : ""));
                if (!ends) while (!tr.isEmpty() && ",.!?…; ".indexOf(tr.charAt(tr.length() - 1)) >= 0)
                    tr = tr.substring(0, tr.length() - 1);
                out.add(tr);
                i = j;
            }
        }
        return String.join(", ", out);
    }

    static String punctOf(String s) {
        Matcher m = TRAILING.matcher(s);
        return m.find() ? m.group() : "";
    }

    /** OfflineFix.harfiEslestir aynasi: karsilik kaynagin harfini izler. */
    static String harfiEslestir(String kaynak, String ceviri) {
        if (ceviri.isEmpty()) return ceviri;
        Character ilk = null;
        for (char c : kaynak.toCharArray()) if (Character.isLetter(c)) { ilk = c; break; }
        if (ilk == null) return ceviri;
        char c0 = ceviri.charAt(0);
        if (!Character.isLetter(c0)) return ceviri;
        if (Character.isUpperCase(ilk))
            return Character.isUpperCase(c0) ? ceviri
                : ceviri.substring(0, 1).toUpperCase(TR) + ceviri.substring(1);
        return Character.isLowerCase(c0) ? ceviri
            : ceviri.substring(0, 1).toLowerCase(TR) + ceviri.substring(1);
    }

    static String translateSentence(String sentence) {
        String punct = punctOf(sentence);
        String core = sentence.substring(0, sentence.length() - punct.length()).trim();
        if (core.isEmpty()) return sentence;
        String r = look(core);
        if (r != null) return harfiEslestir(core, r) + punct;
        r = phrasesLookup(core);
        if (r != null) return harfiEslestir(core, r) + punct;
        if (isName(core)) return core + punct;
        List<String> parts = new ArrayList<>();
        for (String p : core.split(",")) if (!p.trim().isEmpty()) parts.add(p.trim());
        if (parts.size() > 1) {
            List<String> resolved = new ArrayList<>();
            boolean any = false;
            for (String p : parts) {
                String v = look(p);
                if (v == null) v = phrasesLookup(p);
                resolved.add(v);
                if (v != null) any = true;
            }
            if (any || isName(parts.get(0))) return joinParts(parts, resolved, punct);
        }
        return mlTranslate(core + punct);
    }

    static String lineLookup(String t) {
        String punct = punctOf(t);
        String core = t.substring(0, t.length() - punct.length()).trim();
        if (core.isEmpty()) return null;
        String r = look(core);
        if (r == null) r = phrasesLookup(core);
        return r == null ? null : harfiEslestir(core, r) + punct;
    }

    // OfflineFix.dialogTurn: "Why didn't you? -I'm no killer." iki konusmaci.
    static final Pattern DIALOG = Pattern.compile(
        "(?<=[.!?…\\]])\\s*[-–]\\s*(?=[\\[\\p{Lu}'\"])");

    static String draft(String text) {
        String[] turns = DIALOG.split(text);
        if (turns.length > 1) {
            List<String> out = new ArrayList<>();
            for (String t : turns) {
                t = t.trim();
                if (t.isEmpty()) continue;
                String hit = lineLookup(t);
                out.add(hit != null ? hit : draft(t));
            }
            return String.join(" - ", out);
        }
        String[] sentences = SENTENCE_SPLIT.split(text);
        List<String> ok = new ArrayList<>();
        for (String s : sentences) if (!s.trim().isEmpty()) ok.add(s);
        if (ok.size() == 1) return translateSentence(ok.get(0));
        List<String> out = new ArrayList<>();
        for (String s : ok) {
            String hit = lineLookup(s);
            out.add(hit != null ? hit : translateSentence(s));
        }
        return String.join(" ", out);
    }

    static boolean tamamenKaliptanMi(String text) {
        boolean bakildi = false;
        for (String sn : SENTENCE_SPLIT.split(text)) {
            String s = sn.trim();
            if (s.isEmpty()) continue;
            String punct = punctOf(s);
            String core = s.substring(0, s.length() - punct.length()).trim();
            if (core.isEmpty()) continue;
            bakildi = true;
            if (look(core) != null || phrasesLookup(core) != null) continue;
            List<String> parts = new ArrayList<>();
            for (String p : core.split(",")) if (!p.trim().isEmpty()) parts.add(p.trim());
            boolean hepsi = parts.size() > 1;
            for (String p : parts)
                if (look(p) == null && phrasesLookup(p) == null && !isName(p)) { hepsi = false; break; }
            if (hepsi) continue;
            return false;
        }
        return bakildi;
    }

    // ===================================================== AiGuard.hitapDuzelt
    // Kurallar AiGuard.kt'den calisma aninda okunur; elle kopya tutulmaz.
    static List<String[]> HITAP_BAS = new ArrayList<>();
    static Pattern SERT_BAS;

    /** Slang.hints: yapay zekaya giden anlam ipuclari. */
    static List<Pattern> IPUCU_RE = new ArrayList<>();
    static List<String> IPUCU_TXT = new ArrayList<>();
    static int ipucuBozuk = 0;

    static void yukleIpucu() {
        for (String[] p : ciftler(blok(slangSrc, "private val hints", "listOf("))) {
            try {
                IPUCU_RE.add(Pattern.compile("(?i)" + p[0]));
                IPUCU_TXT.add(p[1]);
            } catch (Exception e) {
                System.out.println("  X derlenmeyen ipucu: " + p[0] + " -> " + e.getMessage());
                ipucuBozuk++;
            }
        }
    }

    /** Slang.hintsFor ile ayni: eslesen ipuclari, en fazla 6. */
    static List<String> ipuclari(String satir) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < IPUCU_RE.size(); i++)
            if (IPUCU_RE.get(i).matcher(satir).find() && !out.contains(IPUCU_TXT.get(i)))
                out.add(IPUCU_TXT.get(i));
        return out.size() > 6 ? out.subList(0, 6) : out;
    }

    static void yukleDenetim(String dizin) throws IOException {
        // Normalize Kotlin Unicode case option for this legacy Java source mirror.
        // Actual Kotlin uses RegexOption.IGNORE_CASE (Unicode-aware) rather than (?iu).
        String g = oku(dizin + "/AiGuard.kt")
            .replace(", RegexOption.IGNORE_CASE)", ")");
        for (String[] p : ciftler(blok(g, "private val HITAP_BAS", "listOf(")))
            HITAP_BAS.add(new String[]{ "(?i)^(?:" + p[0] + ")\\b", p[1] });
        Matcher m = Pattern.compile(
            "private val SERT_BAS = Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)").matcher(g);
        if (!m.find()) throw new RuntimeException("SERT_BAS bulunamadi");
        SERT_BAS = Pattern.compile(kstr(m.group(1)), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher m3 = Pattern.compile("private val LAN_ICINDE = Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)").matcher(g);
        if (!m3.find()) throw new RuntimeException("LAN_ICINDE bulunamadi");
        LAN_ICINDE = Pattern.compile(kstr(m3.group(1)), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        if (!g.contains("YoContext10427.correct(kaynak, ceviri)"))
            throw new RuntimeException("YoContext10427 production hook missing");
    }
    static Pattern LAN_ICINDE;

    // AiGuard.sikimDuzelt aynasi (9.98)
    static String sikimDuzelt(String c) {
        if (!c.toLowerCase().contains("sikim")) return c;
        Matcher a = Pattern.compile("(?i)\\bne sikimden\\b").matcher(c);
        StringBuffer sb = new StringBuffer();
        while (a.find()) a.appendReplacement(sb, Character.isUpperCase(a.group().charAt(0)) ? "Neden" : "neden");
        a.appendTail(sb);
        String t = sb.toString().replaceAll("(?i)\\bsikimde değil", "umurumda değil");
        Pattern sik = Pattern.compile("(?i)\\s*\\bsikim\\b");
        boolean lan = sik.matcher(t).find() || (!t.equals(c) && Pattern.compile("(?i)\\bne sikimden\\b").matcher(c).find());
        t = sik.matcher(t).replaceAll("");
        if (lan && !LAN_ICINDE.matcher(t).find()) {
            Matcher m = Pattern.compile("([?!.…]*)\\s*$").matcher(t);
            t = m.find() ? t.substring(0, m.start()).replaceAll("\\s+$", "") + " lan" + m.group(1) : t + " lan";
        }
        return t.trim();
    }

    static String hitapDuzelt(String kaynak, String cevir) {
        String ceviri = sikimDuzelt(cevir);
        String k = kaynak;
        while (!k.isEmpty() && "-– \"'[".indexOf(k.charAt(0)) >= 0) k = k.substring(1);
        // Legacy Java mirror: new policy is tested directly from production Kotlin too.
        if (Pattern.compile("(?i)^yo+\\b").matcher(k).find()) {
            boolean hard = Pattern.compile("(?i)\\b(fuck\\w*|shit\\w*|bitch\\w*|motherfuck\\w*|asshole\\w*|cunt\\w*|bastard\\w*|damn\\w*)\\b").matcher(k).find();
            if (hard) {
                Matcher g = Pattern.compile("(?i)^(?:hey|selam)\\b[\\s,!.…]*").matcher(ceviri);
                if (g.find() && LAN_ICINDE.matcher(ceviri.substring(g.end())).find()) {
                    String rest = ceviri.substring(g.end()).trim();
                    return Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
                }
                return ceviri;
            }
            Matcher first = Pattern.compile("(?i)^(?:lan|ulan)\\b[\\s,!.…]*").matcher(ceviri);
            if (first.find()) {
                String rest=ceviri.substring(first.end()).trim();
                if (rest.isEmpty()) return "Hey!";
                if (Pattern.compile("(?i)^yo+\\s*!").matcher(k).find())
                    return "Hey, " + Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
                return Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
            }
            return ceviri.replaceAll("(?i)\\s+(?:lan|ulan)([?.!]*)$", "$1");
        }
        String karsilik = null;
        for (String[] h : HITAP_BAS)
            if (Pattern.compile(h[0]).matcher(k).find()) { karsilik = h[1]; break; }
        if (karsilik == null) return ceviri;
        Matcher m = SERT_BAS.matcher(ceviri);
        if (!m.find() || m.start() != 0) return ceviri;
        String kalan = ceviri.substring(m.group().length());
        if (kalan.trim().isEmpty()) return ceviri;
        String ayrac = m.group(2);
        if (ayrac.trim().isEmpty() && ayrac.isEmpty()) ayrac = " ";
        return karsilik + (ayrac.isEmpty() ? " " : ayrac) + kalan;
    }

    // ============================================ CaptureService kirinti kurali
    static Set<String> KISA = new HashSet<>();

    static void yukleKisa(String dizin) throws IOException {
        String c = oku(dizin + "/CaptureService.kt");
        String b = blok(c, "private val KISA_KELIMELER = setOf(", "setOf(");
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(b);
        while (m.find()) KISA.add(m.group(1));
    }

    /** altyaziDegil icindeki parcalanmis-okuma kurali. */
    static boolean kirintiSatir(String text) {
        String[] kelimeler = text.trim().split("\\s+");
        if (kelimeler.length < 3) return false;
        int kirinti = 0;
        for (String k : kelimeler) {
            String sade = k.toLowerCase();
            while (!sade.isEmpty() && ".,!?:;\"'’-…".indexOf(sade.charAt(sade.length() - 1)) >= 0)
                sade = sade.substring(0, sade.length() - 1);
            while (!sade.isEmpty() && ".,!?:;\"'’-…".indexOf(sade.charAt(0)) >= 0)
                sade = sade.substring(1);
            if (sade.length() <= 2 && !KISA.contains(sade)) kirinti++;
        }
        return kirinti >= 3 && kirinti * 2 >= kelimeler.length;
    }

    /** Satirlari yazdirir (gelistirme) ya da beklenenle karsilastirir (test). */
    static String DIZIN = ".";
    public static void main(String[] args) throws Exception {
        DIZIN = args[0];
        yukle(args[0]);
        yukleDenetim(args[0]);
        yukleKisa(args[0]);
        yukleIpucu();

        if (args.length > 1 && args[1].equals("--denetim")) { denetim(); return; }
        System.out.println("sozluk: " + PHRASES.size() + " kalip, " + NAMES.size() + " ad, "
            + SLANG.size() + " argo, " + NORM.size() + " dilbilgisi, " + SH_RULES.size() + " kufur kurali");

        if (args.length > 1 && args[1].startsWith("=")) {
            // DOGRULAMA: her satir "ingilizce<TAB>beklenen turkce".
            int hata = 0, kalip = 0, toplam = 0;
            BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(args[1].substring(1)), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) {
                if (l.trim().isEmpty() || l.startsWith("#")) continue;
                String[] p = l.split("\t", 2);
                if (p.length < 2) continue;
                toplam++;
                String oldu = draft(p[0]);
                if (tamamenKaliptanMi(p[0])) kalip++;
                if (!oldu.equals(p[1])) {
                    hata++;
                    System.out.println("  X " + p[0]);
                    System.out.println("      beklenen: " + p[1]);
                    System.out.println("      cikan   : " + oldu);
                }
                if (oldu.contains("<ML:")) {
                    System.out.println("  ! ceviri motoruna dustu: " + p[0] + "  ->  " + oldu);
                }
            }
            System.out.println(toplam + " replik, " + kalip + " tanesi tamamen sozlukten.");
            if (hata > 0) { System.out.println("TEZGAH DUSTU: " + hata + " replik beklenenden farkli."); System.exit(1); }
            System.out.println("Tezgah temiz: 26 dakikalik kayittaki her replik beklendigi gibi cevrildi.");
            return;
        }

        List<String> satirlar = new ArrayList<>();
        if (args.length > 1 && args[1].startsWith("@")) {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(args[1].substring(1)), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) if (!l.trim().isEmpty()) satirlar.add(l.trim());
        } else for (int i = 1; i < args.length; i++) satirlar.add(args[i]);
        for (String s : satirlar)
            System.out.printf("%s\t%s%s%n", s, draft(s), tamamenKaliptanMi(s) ? "" : "");
    }

    static final java.util.Locale TRL = new java.util.Locale("tr");

    /** TurkishFix.buyukHarfiGeriKoy aynasi (elle; duz kod, kural listesi degil). */
    static String adBuyut(String kaynak, String ceviri) {
        String t = ceviri;
        Matcher m = Pattern.compile("[\\p{L}][\\p{L}'’]*").matcher(kaynak);
        List<int[]> yer = new ArrayList<>(); List<String> kel = new ArrayList<>();
        while (m.find()) { yer.add(new int[]{m.start(), m.end()}); kel.add(m.group()); }
        for (int i = 0; i < kel.size(); i++) {
            String kelime = kel.get(i);
            if (kelime.length() < 5) continue;
            if (!Character.isUpperCase(kelime.charAt(0))) continue;
            String onceki = kaynak.substring(0, yer.get(i)[0]).replaceAll("\\s+$", "");
            if (i == 0 || onceki.isEmpty() || ".!?…".indexOf(onceki.charAt(onceki.length() - 1)) >= 0) continue;
            String kok = kelime.toLowerCase();
            while (kok.endsWith("s")) kok = kok.substring(0, kok.length() - 1);
            while (kok.endsWith("e")) kok = kok.substring(0, kok.length() - 1);
            if (kok.length() < 4) continue;
            final String K = kok;
            Matcher mm = Pattern.compile("(?<![\\p{L}])" + K + "([\\p{L}'’]*)").matcher(t);
            StringBuilder sb = new StringBuilder(); int last = 0;
            while (mm.find()) {
                sb.append(t, last, mm.start())
                  .append(K.substring(0,1).toUpperCase(TRL)).append(K.substring(1)).append(mm.group(1));
                last = mm.end();
            }
            t = sb.append(t.substring(last)).toString();
        }
        return t;
    }

    static int hata = 0;

    static void bekle(boolean kosul, String mesaj) {
        if (!kosul) { System.out.println("  X " + mesaj); hata++; }
    }

    static void esit(String olan, String beklenen, String ne) {
        bekle(olan.equals(beklenen), ne + "\n      beklenen: " + beklenen + "\n      cikan   : " + olan);
    }

    static void denetim() throws IOException {
        // --- 1) "Yo": dogal hitap, sabit "Lan" degil. Gercek Kotlin de ayrica test edilir.
        esit(hitapDuzelt("Yo, we're here to pick up. For Boobie.", "Hey, malı almaya geldik."),
             "Hey, malı almaya geldik.", "dogru hitap bozuldu");
        esit(hitapDuzelt("Yo, we're here to pick up. For Boobie.", "Lan, malı almaya geldik."),
             "Malı almaya geldik.", "gereksiz lan kaldi");
        esit(hitapDuzelt("Yo, we're here to pick up.", "Malı almaya geldik."),
             "Malı almaya geldik.", "kaynakta olmayan lan eklendi");
        esit(hitapDuzelt("Yo, Raymond around?", "Raymond burada mı?"),
             "Raymond burada mı?", "isim bozuldu");
        esit(hitapDuzelt("Yo, what the fuck?", "Hey, bu ne lan?"),
             "Bu ne lan?", "sert replikte cift hitap");
        esit(hitapDuzelt("Yo!", "Hey!"), "Hey!", "Yo! tek basina Hey olmalidir");
        // 9.98: "sikim" yok (kullanicinin istegi)
        esit(hitapDuzelt("What the fuck is going on?", "Ne sikim oluyor?"), "Ne oluyor lan?", "sikim kaldi");
        esit(hitapDuzelt("What the fuck is going on here?!", "Burada ne sikim oluyor?!"), "Burada ne oluyor lan?!", "sikim kaldi");
        esit(hitapDuzelt("What the fuck you talking about?", "Ne sikimden bahsediyorsun sen?"), "Neden bahsediyorsun sen lan?", "sikimden kaldi");
        esit(hitapDuzelt("I don't give a fuck.", "Gerçekten sikimde değil."), "Gerçekten umurumda değil.", "sikimde kaldi");
        esit(hitapDuzelt("What the fuck?", "Ne sikim?"), "Ne lan?", "sikim kaldi");
        esit(hitapDuzelt("He sat down.", "Oturdu."), "Oturdu.", "temiz satir bozuldu");
        {
            String g2 = oku(DIZIN + "/AiGuard.kt");
            for (String parca : new String[]{"val ceviri = sikimDuzelt(cevir)",
                "t = SIKIM.replace(t, \"\")", "if (lanEkle && !LAN_ICINDE.containsMatchIn(t)) {"})
                bekle(g2.contains(parca), "sikimDuzelt degismis, ayna bayat: " + parca);
        }
        esit(hitapDuzelt("Hey, listen.", "Lan, dinle."), "Hey, dinle.", "Hey -> Hey olmadi");
        esit(hitapDuzelt("Like, might have to fuck around.", "Lan, ortalığı karıştırmak gerek."),
             "Yani, ortalığı karıştırmak gerek.", "Like -> Yani olmadi");
        esit(hitapDuzelt("Man.. I don't even wanna hear that shit.", "Lan... o boktan şeyi duymak istemiyorum."),
             "Ya... o boktan şeyi duymak istemiyorum.", "Man -> Ya olmadi");
        esit(hitapDuzelt("Look, we could go back empty handed.", "Ulan, elimiz boş dönebiliriz."),
             "Bak, elimiz boş dönebiliriz.", "Look -> Bak olmadi");

        // Cumle ICINDEKI "lan" dokunulmaz: kufur gercekten kaynakta.
        esit(hitapDuzelt("Where the fuck you at?", "Neredesin lan?"),
             "Neredesin lan?", "cumle icindeki lan degisti");
        esit(hitapDuzelt("Police! Don't fucking move!", "Polis! Kıpırdama lan!"),
             "Polis! Kıpırdama lan!", "cumle sonundaki lan degisti");
        // Kaynakta seslenme yoksa karisma.
        esit(hitapDuzelt("Get out of here!", "Lan, çık buradan!"),
             "Lan, çık buradan!", "seslenme yokken degisti");
        // Yalniz "Lan" varsa, kalan bos: dokunma (cumleyi yok etmeyelim).
        esit(hitapDuzelt("Yo!", "Lan!"), "Hey!", "tek sozcukte gereksiz lan kaldi");
        // Dogru seslenme zaten yerindeyse ayni kalir.
        esit(hitapDuzelt("Yo, phone.", "Lan, telefon."), "Telefon.", "notsiz telefona lan eklendi");

        // --- 2) Kirinti kurali: gercek kisa kelimeler cope sayilmasin.
        bekle(!kirintiSatir("Put it on speakerphone."), "gercek replik dustu: Put it on speakerphone.");
        bekle(!kirintiSatir("It is what it is, my friend."), "gercek replik dustu: It is what it is, my friend.");
        bekle(!kirintiSatir("I got it."), "gercek replik dustu: I got it.");
        bekle(!kirintiSatir("He is on to us."), "gercek replik dustu: He is on to us.");
        bekle(!kirintiSatir("Yo, is he up?"), "gercek replik dustu: Yo, is he up?");
        bekle(!kirintiSatir("Go on, do it."), "gercek replik dustu: Go on, do it.");
        // OCR copu hala yakalaniyor.
        bekle(kirintiSatir("f h b C V EN• TR"), "OCR copu kacti: f h b C V EN TR");
        bekle(kirintiSatir("q w z x k"), "OCR copu kacti: q w z x k");
        bekle(kirintiSatir("l1 g7 vv rn bq"), "OCR copu kacti: l1 g7 vv rn bq");

        // --- 3) Ipuclari: HIC GORULMEMIS satirlarda da yetisiyor mu?
        //
        // Ipucu, sabit kalip yazmanin genelleseblir karsiligi: ceviriyi
        // zorlamiyor, yapay zekaya kelimenin anlamini soyluyor. Bu yuzden
        // asagidaki satirlarin hicbiri sozlukte YOK ve olmasi da gerekmiyor.
        bekle(ipucuBozuk == 0, ipucuBozuk + " ipucu derlenmiyor (yukarida)");
        System.out.println("  " + IPUCU_RE.size() + " ipucu derlendi.");
        String[][] yeni = {
            { "Yo, my plug got the work, we re-up tonight.", "plug" },
            { "The opps slid through the block, bring the burner.", "opps" },
            { "He hit a lick for twenty racks, no cap.", "lick" },
            { "Pull up in the whip, we gotta move the pack.", "whip" },
            { "The fiends been out here all day, the heat is on.", "fiend" },
            { "Lay low til the smoke die down, deadass.", "lay low" },
            { "Go inside and ask for Marcus.", "ask for" },
            { "We just here to collect, the workers know.", "collect" },
            { "That nigga tooled up, he strapped.", "tooled up" },
            { "They in the traphouse movin weight.", "trap" },
        };
        for (String[] y : yeni) {
            List<String> b = ipuclari(y[0]);
            boolean var = false;
            for (String t : b) if (t.toLowerCase().contains(y[1].toLowerCase())) var = true;
            bekle(!b.isEmpty(), "hic ipucu yetismedi: " + y[0]);
            bekle(var, "'" + y[1] + "' ipucu 6 sinirina giremedi: " + y[0] + "\n      gelen: " + b);
        }

        // --- 4) Ozel adin buyuk harfi geri konur (ceviri motoru kucultuyor).
        esit(adBuyut("but only because all remaining Viltrumites have been infected.",
             "ama kalan tüm viltrumitler enfekte oldu."),
             "ama kalan tüm Viltrumitler enfekte oldu.", "Viltrumitler buyutulmedi");
        esit(adBuyut("It is the end of our race, Regent.", "Irkımızın sonu bu, regent."),
             "Irkımızın sonu bu, Regent.", "Regent buyutulmedi");
        // Siradan kelime buyutulmemeli.
        esit(adBuyut("The number is quite high.", "sayı oldukça yüksek."),
             "sayı oldukça yüksek.", "siradan kelime buyutuldu");
        esit(adBuyut("Our remaining population are the few who have recovered.",
             "kalan nüfusumuz iyileşmiş az kişidir."),
             "kalan nüfusumuz iyileşmiş az kişidir.", "siradan kelime buyutuldu");
        // Cumle basindaki buyuk harf ad gostergesi degil.
        esit(adBuyut("Billions upon billions.", "milyarlarca milyarlarca."),
             "milyarlarca milyarlarca.", "cumle basi ad sanildi");

        if (hata > 0) { System.out.println("DENETIM DUSTU: " + hata + " hata."); System.exit(1); }
        System.out.println("Denetim temiz: seslenme onarimi, kirinti kurali ve ipuclari beklendigi gibi.");
    }
}

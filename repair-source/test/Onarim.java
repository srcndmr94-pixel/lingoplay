import java.io.*; import java.nio.file.*; import java.util.*; import java.util.regex.*; import java.util.zip.*;
/**
 * 9.74 OCR onarimlarinin aynasi: notaTemizle, hitapParantezi,
 * bolunmusuBirlestir, kesmeyiGeriKoy, copOnEki.
 *
 * Sozluk gercek kelimeler.gz'den, VERI KUMELERI (NOTA_HARF, KISA_GERCEK,
 * kesme ardillari) OcrSozluk.kt'den CALISMA ANINDA okunuyor; boylece Kotlin
 * tarafina kelime eklenince ayna kendiliginden ogreniyor. Kural sayisi
 * degisirse bayatlama alarmi atiyor.
 */
public class Onarim {
    static Set<String> SOZ = new HashSet<>();
    static Set<String> NOTA_HARF = new HashSet<>();
    static Set<String> KISA_GERCEK = new HashSet<>();
    static List<String> KESME_ARD = new ArrayList<>();
    static Map<String, String> KESME_KESIN = new LinkedHashMap<>();
    static int hata = 0;

    // Kotlin'deki ayni kaliplar
    static final Pattern NOTA_BAS = Pattern.compile("^[\\s>♪♫¶|]*([JjDd>♪♫¶|lr<†])[\\s]+");
    static final Pattern NOTA_SON = Pattern.compile("[\\s]+([JjDd>♪♫¶|<†])[\\s>♪♫¶|.]*$");
    static final Pattern KIRIK_HITAP = Pattern.compile("^([\\s\\-–]*)(\\p{Lu}[\\p{L}'’]{1,19})[)\\]](\\s+)");
    static final Pattern UNSUZ_YIGINI = Pattern.compile("[bcdfghjklmnpqrstvwxz]{4}");
    static final Pattern UNLU = Pattern.compile("[aeiouy]");
    static final Pattern OZEL_AD = Pattern.compile("^\\p{Lu}\\p{Ll}+$");
    static final String KIRP = ".,!?:;\"'’-…[]";
    static final String NOTA_UC = "♪♫¶>< ";

    // Kaynaktaki beklenen kural sayilari (bayatlama alarmi)
    static final int BEKLENEN_NOTA = 11, BEKLENEN_KESIN = 2, BEKLENEN_ARD = 19;
    // Kotlin onar() zincirindeki "t = ..." adim sayisi. Zincire yeni bir
    // onarim eklenirse bu ayna onu BILMEDIGI icin test sessizce eksik
    // olcer; alarm o yuzden var. (ingAyrildi eklenince tam bu oldu.)
    static final int BEKLENEN_ZINCIR = 10;

    static String kirp(String s) {
        int a = 0, b = s.length();
        while (a < b && KIRP.indexOf(s.charAt(a)) >= 0) a++;
        while (b > a && KIRP.indexOf(s.charAt(b - 1)) >= 0) b--;
        return s.substring(a, b);
    }

    static Set<String> kume(String s, String isaret) {
        int i = s.indexOf(isaret);
        if (i < 0) throw new RuntimeException("kaynakta yok: " + isaret);
        int son = s.indexOf(")", i);
        // cok satirli setOf( ... ) : kapanis parantezini dogru bul
        int derinlik = 0; int j = s.indexOf("(", i);
        for (int k = j; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '(') derinlik++;
            else if (c == ')') { derinlik--; if (derinlik == 0) { son = k; break; } }
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(s.substring(j, son));
        while (m.find()) if (!m.group(1).isEmpty()) out.add(m.group(1));
        return out;
    }

    static void yukle(String kaynak, String gz) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(gz)), "UTF-8"))) {
            String l; while ((l = r.readLine()) != null) if (!l.isEmpty()) SOZ.add(l.trim().toLowerCase());
        }
        String s = new String(Files.readAllBytes(Paths.get(kaynak + "/OcrSozluk.kt")), "UTF-8");
        NOTA_HARF.addAll(kume(s, "private val NOTA_HARF = setOf("));
        KISA_GERCEK.addAll(kume(s, "private val KISA_GERCEK = setOf("));

        KESME_ARD.addAll(kume(s, "private val KESME_ARDIL = listOf("));
        int i = s.indexOf("private val KESME_KESIN");
        int bitis = s.indexOf("fun kesmeyiGeriKoy", i);
        if (i < 0 || bitis < 0) throw new RuntimeException("KESME_KESIN bulunamadi");
        Matcher mk = Pattern.compile("\"([a-z']+)\" to \"([a-z']+)\"").matcher(s.substring(i, bitis));
        while (mk.find()) KESME_KESIN.put(mk.group(1), mk.group(2));

        // MANTIK KORUMALARI.
        //
        // Bu ayna VERIYI kaynaktan okur ama MANTIGI elle tasir; Kotlin'de
        // bir koruma silinirse ayna bunu goremez. O yuzden kritik
        // korumalarin kaynakta DURDUGUNU dogruluyoruz. (Kotlin derleyicisi
        // olmadigi icin elimizdeki en iyi yol bu.)
        String[][] korumalar = {
            {"bolunmusuBirlestir", "!aGovde.last().isLetter()",
             "ilk parca noktalamayla bitiyorsa birlestirme YAPILMAMALI; "
             + "yoksa \"Lu? Lu? Siktir.\" -> \"LLulu? Siktir.\" olur"},
            {"bolunmusuBirlestir", "aOn + aSade + bOn.lowercase()",
             "on ek a.substring(0, a.length - aSade.length) ile hesaplanirsa "
             + "sondaki noktalama da sayilir ve ilk harf cogalir"},
            {"notaTemizle", "size >= 2",
             "kirpma sonrasi en az iki kelime kalmali, yoksa tek kelimelik "
             + "gercek replik silinir"},
            {"copOnEki", "on.size < 2",
             "tek bilinmeyen ad cop on ek sayilmamali"},
            {"copOnEki", "on.any { KISALTMA_EKI.containsMatchIn(it) }",
             "on ekte n't/'ll/'d varsa yapisik okunmus GERCEK cumledir, kirpilmamali"},
            {"bozukI", "!Sozluk.icinde(tam) && Sozluk.icinde(kok.lowercase())",
             "yapisik L yalniz sozluk dogrularsa ayrilmali; yoksa \"tell would\" bozulur"},
            {"kesmeyiGeriKoy", "KESME_BELIRSIZ",
             "\"were\" belirsizdir, yalniz clause basinda duzeltilmeli"},
        };
        for (String[] k : korumalar) {
            int a1 = s.indexOf("fun " + k[0]);
            if (a1 < 0) throw new RuntimeException("kaynakta yok: fun " + k[0]);
            int a2 = s.indexOf("\n    fun ", a1 + 10);
            if (a2 < 0) a2 = Math.min(s.length(), a1 + 3000);
            if (!s.substring(a1, a2).contains(k[1]))
                throw new RuntimeException("KORUMA SILINMIS: " + k[0] + " artik \""
                    + k[1] + "\" icermiyor. " + k[2]);
        }

        int iz = s.indexOf("fun onar(ham: String): String {");
        if (iz < 0) throw new RuntimeException("onar() bulunamadi");
        int jz = s.indexOf("\n    }", iz);
        int adim = 0;
        Matcher mz = Pattern.compile("(?m)^\\s+t = ").matcher(s.substring(iz, jz));
        while (mz.find()) adim++;
        if (adim != BEKLENEN_ZINCIR)
            throw new RuntimeException("BAYAT AYNA: onar() zincirinde " + adim
                + " adim var, bu ayna " + BEKLENEN_ZINCIR + " tanesini biliyor."
                + " Onarim.java'yi guncelle ve BEKLENEN_ZINCIR'i duzelt.");

        if (NOTA_HARF.size() != BEKLENEN_NOTA || KESME_KESIN.size() != BEKLENEN_KESIN
            || KESME_ARD.size() != BEKLENEN_ARD)
            throw new RuntimeException("BAYAT AYNA: NOTA_HARF=" + NOTA_HARF.size() + "/" + BEKLENEN_NOTA
                + ", KESME_KESIN=" + KESME_KESIN.size() + "/" + BEKLENEN_KESIN
                + ", KESME_ARD=" + KESME_ARD.size() + "/" + BEKLENEN_ARD
                + ". Onarim.java'yi guncelle.");
        System.out.println("  sozluk " + SOZ.size() + ", nota " + NOTA_HARF.size()
            + ", kisa-gercek " + KISA_GERCEK.size() + ", kesme " + KESME_KESIN.size()
            + "+1x" + KESME_ARD.size());
    }

    static int kelimeSayisi(String s) { return s.trim().isEmpty() ? 0 : s.trim().split("\\s+").length; }

    static String notaTemizle(String metin) {
        String t = metin.trim();
        for (int tur = 0; tur < 2; tur++) {
            Matcher b = NOTA_BAS.matcher(t);
            if (b.find() && NOTA_HARF.contains(b.group(1).toLowerCase())) {
                String kalan = t.substring(b.group().length()).trim();
                if (kelimeSayisi(kalan) >= 2) t = kalan; else break;
            } else break;
        }
        Matcher sn = NOTA_SON.matcher(t);
        if (sn.find() && NOTA_HARF.contains(sn.group(1).toLowerCase())) {
            String kalan = t.substring(0, sn.start()).trim();
            if (kelimeSayisi(kalan) >= 2) t = kalan;
        }
        int a = 0, b2 = t.length();
        while (a < b2 && NOTA_UC.indexOf(t.charAt(a)) >= 0) a++;
        while (b2 > a && NOTA_UC.indexOf(t.charAt(b2 - 1)) >= 0) b2--;
        return t.substring(a, b2).trim();
    }

    static String hitapParantezi(String metin) {
        Matcher m = KIRIK_HITAP.matcher(metin);
        if (!m.find()) return metin;
        return m.group(1) + "[" + m.group(2) + "]" + m.group(3) + metin.substring(m.group().length());
    }

    static String bolunmusuBirlestir(String metin) {
        List<String> p = new ArrayList<>(Arrays.asList(metin.split(" ")));
        if (p.size() < 2) return metin;
        int i = 0;
        while (i < p.size() - 1) {
            String a = p.get(i), b = p.get(i + 1);
            int bas = 0; while (bas < a.length() && !Character.isLetter(a.charAt(bas))) bas++;
            String aOn = a.substring(0, bas), aGovde = a.substring(bas);
            if (aGovde.isEmpty() || !Character.isLetter(aGovde.charAt(aGovde.length()-1))) { i++; continue; }
            String aSade = aGovde;
            String bOn = ""; for (char c : b.toCharArray()) { if (!Character.isLetter(c)) break; bOn += c; }
            String bKuyruk = b.substring(bOn.length());
            boolean harfDisi = false; for (char c : aSade.toCharArray()) if (!Character.isLetter(c)) harfDisi = true;
            if (aSade.length() < 1 || aSade.length() > 2 || bOn.length() < 2 || harfDisi
                || KISA_GERCEK.contains(aSade.toLowerCase())
                || (aSade.length() == 2 && SOZ.contains(aSade.toLowerCase()))) { i++; continue; }
            if (!SOZ.contains((aSade + bOn).toLowerCase())) { i++; continue; }
            p.set(i, aOn + aSade + bOn.toLowerCase() + bKuyruk);
            p.remove(i + 1);
        }
        return String.join(" ", p);
    }

    static String kirpOn(String s) {
        String K = ".,!?:;\"'’…[(";
        int a = 0, b = s.length();
        while (a < b && K.indexOf(s.charAt(a)) >= 0) a++;
        while (b > a && K.indexOf(s.charAt(b - 1)) >= 0) b--;
        return s.substring(a, b);
    }

    static String uygula(String t, Pattern r, String dogru, int kokGrup) {
        Matcher m = r.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String ilk = m.group(kokGrup);
            String d = Character.isUpperCase(ilk.charAt(0))
                ? Character.toUpperCase(dogru.charAt(0)) + dogru.substring(1) : dogru;
            String on = kokGrup == 2 ? m.group(1) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(on + d + m.group(kokGrup + 1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String kesmeyiGeriKoy(String metin) {
        String t = metin;
        for (String ard : KESME_ARD) {
            t = uygula(t, Pattern.compile("(^|[.!?\u2026\"'\\[\\]()\u2013\u2014-]\\s*)(were)(\\s+"
                + ard + "\\b)", Pattern.CASE_INSENSITIVE), "we're", 2);
        }
        for (Map.Entry<String, String> e : KESME_KESIN.entrySet()) {
            for (String ard : KESME_ARD) {
                t = uygula(t, Pattern.compile("(?<![\\p{L}'\u2019])(" + e.getKey() + ")(\\s+"
                    + ard + "\\b)", Pattern.CASE_INSENSITIVE), e.getValue(), 1);
            }
        }
        return t;
    }

    static boolean bilinir(String w) {
        String k = kirp(w);
        int ap = k.indexOf('\''); if (ap >= 0) k = k.substring(0, ap);
        if (k.length() < 2) return k.equalsIgnoreCase("i") || k.equalsIgnoreCase("a");
        return SOZ.contains(k.toLowerCase()) || OZEL_AD.matcher(k).matches();
    }

    static final Pattern KISALTMA_EKI = Pattern.compile(
        "\\p{L}(?:n['\u2019]t|['\u2019](?:ll|re|ve|d|s|m))", Pattern.CASE_INSENSITIVE);

    static String copOnEki(String metin) {
        String[] p = metin.trim().split("\\s+");
        if (p.length < 3) return null;
        int bas = p.length;
        for (int i = p.length - 1; i >= 0; i--) { if (!bilinir(p[i])) break; bas = i; }
        if (bas == 0 || bas == p.length) return null;
        List<String> on = Arrays.asList(Arrays.copyOfRange(p, 0, bas));
        if (on.size() < 2) return null;
        for (String w : on) if (kirp(w).length() < 3) return null;
        for (String w : on) if (bilinir(w)) return null;
        for (String w : on) if (KISALTMA_EKI.matcher(w).find()) return null;
        boolean sacma = false;
        for (String w : on) {
            String k = kirp(w).toLowerCase();
            if (UNSUZ_YIGINI.matcher(k).find() || !UNLU.matcher(k).find()) sacma = true;
        }
        if (!sacma) return null;
        return String.join(" ", Arrays.copyOfRange(p, bas, p.length));
    }

    /** "Talk in'" -> "Talkin'": kok + "ing" sozlukteyse birlestir. */
    static final Pattern ING_AYRI = Pattern.compile("\\b(\\p{L}{3,})\\s+(in['\u2019])(?=\\s|$|[.,!?])");

    static String ingAyrildi(String metin) {
        Matcher m = ING_AYRI.matcher(metin);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String kok = m.group(1);
            String rep = SOZ.contains((kok + "ing").toLowerCase())
                ? kok + m.group(2) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static final Pattern EGIK_I = Pattern.compile("(^|[\\s\\]\\)\\-\u2013])/(?=\\s+\\p{Ll})");
    static final Pattern LM = Pattern.compile("(?<![\\p{L}'\u2019])l['\u2019]?m(?![\\p{L}'\u2019])");
    static final Pattern YAPISIK_L = Pattern.compile("(?<![\\p{L}'\u2019])(\\p{L}{3,})l(\\s+)(would|will|could|can|should|have|had|was|am|"
        + "did|do|don't|didn't|can't|won't|wouldn't|couldn't|think|know|need|want)(?![\\p{L}])");

    static String bozukI(String metin) {
        Matcher m = EGIK_I.matcher(metin);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "I"));
        m.appendTail(sb);
        String t = LM.matcher(sb.toString()).replaceAll("I'm");
        m = YAPISIK_L.matcher(t);
        sb = new StringBuffer();
        while (m.find()) {
            String kok = m.group(1);
            String rep = (!SOZ.contains((kok + "l").toLowerCase()) && SOZ.contains(kok.toLowerCase()))
                ? kok + " I" + m.group(2) + m.group(3) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** onar() zincirinin 9.74+ kismi */
    static String onar(String ham) {
        String t = ham.trim().replaceAll("\\s+", " ");
        t = notaTemizle(t);
        t = hitapParantezi(t);
        t = bolunmusuBirlestir(t);
        t = ingAyrildi(t);
        t = bozukI(t);
        t = kesmeyiGeriKoy(t);
        return t.replaceAll("\\s+", " ").trim();
    }

    static void esit(String giris, String beklenen) {
        String c = onar(giris);
        if (!c.equals(beklenen)) {
            hata++;
            System.out.println("  X " + giris);
            System.out.println("      beklenen: " + beklenen);
            System.out.println("      cikan   : " + c);
        }
    }

    static void copEsit(String giris, String beklenen) {
        String c = copOnEki(giris);
        if (!Objects.equals(c, beklenen)) {
            hata++;
            System.out.println("  X copOnEki: " + giris);
            System.out.println("      beklenen: " + beklenen);
            System.out.println("      cikan   : " + c);
        }
    }

    /**
     * ESASLI OZELLIK TESTLERI — gercek kayit korpusu uzerinde.
     *
     * 1) DEGISMEZLIK: onar(onar(x)) == onar(x). Zincir bir satira iki kez
     *    uygulanirsa (ileride baska bir yere baglanirsa) sonuc degismemeli;
     *    degisiyorsa onarimlardan biri kendi cikisini bozuyor demektir.
     * 2) TURKCEYE DOKUNMAMA: onar(turkce) == turkce. Uygulama bazen KENDI
     *    yazdigi altyaziyi geri okuyor ("sadece kendi yazimiz" tanisi);
     *    zincir Turkce metni bozarsa ekrandaki ceviri cope doner.
     */
    /** Icinde OCR artigi olmayan Turkce satir mi? */
    static boolean temizTurkce(String l) {
        String t = l.trim();
        if (t.isEmpty()) return false;
        // kenarda nota isareti
        String[] p = t.split("\\s+");
        if (p.length > 0 && NOTA_HARF.contains(kirp(p[0]).toLowerCase())) return false;
        if (p.length > 0 && NOTA_HARF.contains(kirp(p[p.length-1]).toLowerCase())) return false;
        // kirik konusmaci parantezi
        if (KIRIK_HITAP.matcher(t).find()) return false;
        // ayri yazilmis 1-2 harfli LATIN parca (bolunmus kelime imzasi)
        for (int i = 0; i < p.length - 1; i++) {
            String k = kirp(p[i]);
            if (k.length() >= 1 && k.length() <= 2 && k.matches("[A-Za-z]+")) return false;
        }
        // "in'" ayrilmasi
        if (ING_AYRI.matcher(t).find()) return false;
        return true;
    }

    static void ozellikler(String dizin) throws Exception {
        List<String> ing = Files.readAllLines(Paths.get(dizin + "/onarim_ingilizce.txt"));
        List<String> tr = Files.readAllLines(Paths.get(dizin + "/onarim_turkce.txt"));
        int d = 0, t2 = 0;
        for (String l : ing) {
            if (l.trim().isEmpty()) continue;
            String bir = onar(l), iki = onar(bir);
            if (!bir.equals(iki)) {
                d++;
                if (d <= 5) {
                    System.out.println("  X DEGISMEZLIK BOZUK: " + l);
                    System.out.println("      1. gecis: " + bir);
                    System.out.println("      2. gecis: " + iki);
                }
            }
        }
        // "TEMIZ TURKCE" suzgeci: icinde OCR artigi OLMAYAN satirlar.
        // Artikli satirlarda zincirin degistirmesi DOGRUDUR (nota isaretini
        // atmak, "Telia)" -> "[Telia]"), o yuzden onlar olcunun disinda.
        int temizSayi = 0;
        for (String l : tr) {
            if (l.trim().isEmpty()) continue;
            if (!temizTurkce(l)) continue;
            temizSayi++;
            String bir = onar(l);
            if (!bir.equals(l.trim().replaceAll("\\s+", " "))) {
                t2++;
                if (t2 <= 6) {
                    System.out.println("  X TEMIZ TURKCE BOZULDU: " + l);
                    System.out.println("      cikan               : " + bir);
                }
            }
        }
        // Turkce satirlar icin degismezlik de sart
        for (String l : tr) {
            if (l.trim().isEmpty()) continue;
            String bir = onar(l), iki = onar(bir);
            if (!bir.equals(iki)) { t2++; if (t2 <= 8) System.out.println("  X TR DEGISMEZLIK: " + l); }
        }
        hata += d + t2;
        System.out.println("  ozellik: " + ing.size() + " ingilizce satir degismezlik="
            + (d == 0 ? "TAMAM" : d + " HATA") + ", " + temizSayi + "/" + tr.size()
            + " temiz turkce satir dokunulmadi=" + (t2 == 0 ? "TAMAM" : t2 + " HATA"));
        if (temizSayi < tr.size() / 2)
            throw new RuntimeException("Temiz turkce suzgeci cok dar: " + temizSayi
                + "/" + tr.size() + " — olcum anlamsiz.");
    }

    public static void main(String[] a) throws Exception {
        yukle(a[0], a[1]);
        if (a.length > 2) ozellikler(a[2]);

        // --- NOTA ISARETI (sarki altyazisi) ------------------------------
        esit("J And he's gotta be larger than life", "And he's gotta be larger than life");
        esit("D And he's gotta be larger than life", "And he's gotta be larger than life");
        esit("> I'm holding out for a hero 'til the morning light",
             "I'm holding out for a hero 'til the morning light");
        esit("I'm holding for a hero 'til the end of the night >",
             "I'm holding for a hero 'til the end of the night");
        esit("J Larger than life", "Larger than life");
        esit("Larger than life >", "Larger than life");
        esit("♪ All he needed were some friends", "All he needed were some friends");
        // "I" ve "A" nota sanilmamali
        esit("I know what you did.", "I know what you did.");
        esit("A man walks in.", "A man walks in.");
        // Kirpma sonrasi tek kelime kaliyorsa dokunma
        esit("D Surprise.", "D Surprise.");

        // --- KIRILMIS KONUSMACI PARANTEZI --------------------------------
        esit("Telia) Because we'd never take it.", "[Telia] Because we'd never take it.");
        esit("SHREK] Great job, guys.", "[SHREK] Great job, guys.");
        // 9.85: bastaki tire yuzunden kaciyordu ("Nolan) Earth?" cevrilmeden kaldi)
        esit("-Nolan) Earth? -[Alien That's what he said.", "-[Nolan] Earth? -[Alien That's what he said.");
        // Gercek parantez bozulmamali
        esit("[Telia] Because we'd never take it.", "[Telia] Because we'd never take it.");
        esit("(whispering) It's perfect.", "(whispering) It's perfect.");
        // Cumle ortasindaki parantez dokunulmaz
        esit("He said (quietly) that he knew.", "He said (quietly) that he knew.");

        // --- BOLUNMUS KELIME --------------------------------------------
        esit("And he's gotta be sure, and it's gotta be s Oon",
             "And he's gotta be sure, and it's gotta be soon");
        // Sozlukte karsiligi olmayan ayrik kelimeler BIRLESMEMELI
        esit("Not all I manes an near in-game", "Not all I manes an near in-game");
        esit("Far Far Away", "Far Far Away");
        esit("I am here.", "I am here.");
        esit("Go in to the room.", "Go in to the room.");
        esit("It is a bout time.", "It is a bout time.");
        // --- 9.84: BOZUK "I" (12 Kasim kaydi)
        esit("[Alien] You said he was dead! -[Nolan] / thought he was.",
             "[Alien] You said he was dead! -[Nolan] I thought he was.");
        esit("[Alien] / think we got off on the wrong foot.",
             "[Alien] I think we got off on the wrong foot.");
        esit("/ know.", "I know.");
        esit("I mean, if lm being honest, he's still a little mushy",
             "I mean, if I'm being honest, he's still a little mushy");
        esit("Yes, but lm afraid the details of my work", "Yes, but I'm afraid the details of my work");
        esit("You thinkl would ever help one of his kind?", "You think I would ever help one of his kind?");
        // Gercek kelimeler ve kesirler BOZULMAMALI
        esit("You tell would-be heroes.", "You tell would-be heroes.");
        esit("They all will come.", "They all will come.");
        esit("He got 3 / 4 of it.", "He got 3 / 4 of it.");
        esit("and/or something", "and/or something");
        esit("The film was great.", "The film was great.");
        esit("A calm day.", "A calm day.");
        // 9.80: ilk parca NOKTALAMAYLA bitiyorsa kelime orada bitmistir.
        // Eski hal "Lu? Lu? Siktir." -> "LLulu? Siktir." yapiyordu.
        esit("Lu? Lu? Siktir.", "Lu? Lu? Siktir.");
        esit("No. No. Stop.", "No. No. Stop.");
        esit("Go! Go! Now!", "Go! Go! Now!");
        esit("Hey, so on it goes.", "Hey, so on it goes.");
        // Ama gercek bolunme hala birlesiyor
        esit("it's gotta be s Oon", "it's gotta be soon");

        // --- KESME ISARETI ----------------------------------------------
        esit("Were here to show you what's next.", "We're here to show you what's next.");
        esit("were going to lose.", "we're going to lose.");
        esit("Youre ready for this.", "You're ready for this.");
        esit("Theyre about to leave.", "They're about to leave.");
        // Gercek gecmis zaman BOZULMAMALI
        esit("Were you there?", "Were you there?");
        esit("Were all the doors locked?", "Were all the doors locked?");
        esit("Where were they?", "Where were they?");
        esit("These were done by hand.", "These were done by hand.");

        // --- COP ON EK ---------------------------------------------------
        copEsit("hegd ahtdd Surprise.", "Surprise.");
        // 9.87 — 12 Kasim 9.86 kaydi: yapisik okunmus gercek cumle kirpilmamali
        copEsit("wouldn'tibe funifl phck you extra eye, out for my shack, and allow you to keep the", null);
        copEsit("Eootaea ranturarl on Pr orme came angles", null);   // "on" kisa -> dokunma
        // Gercek replikler dokunulmamali
        copEsit("Oyeme, mi hermano, we got a situation happening.", null);
        copEsit("Thaedus arrived.", null);
        copEsit("Viltrumite Thaedus arrived here.", null);
        copEsit("I know what you did.", null);
        copEsit("Nolan, Debbie, Mark.", null);
        copEsit("Welcome aboard the Venture.", null);
        copEsit("Captured on PS5", null);

        if (hata > 0) { System.out.println("ONARIM DUSTU: " + hata + " hata."); System.exit(1); }
        System.out.println("Onarim testi gecti: nota, hitap parantezi, bolunmus kelime, kesme, cop on ek.");
    }
}

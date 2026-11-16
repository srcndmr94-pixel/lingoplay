import java.io.*; import java.nio.file.*; import java.util.*; import java.util.regex.*;
/**
 * AdDefteri aynasi. ASLA_AD ve ETIKET_DOLGU kumeleri ile esikler
 * OfflineFix.kt'den CALISMA ANINDA okunuyor; Kotlin tarafina kelime
 * eklenince ayna kendiliginden ogreniyor.
 *
 * Testin asil isi: kullanicinin 11 Kasim 9.74 kaydindaki satirlari SIRAYLA
 * besleyip, adlarin hangi satirdan sonra korunmaya basladigini olcmek.
 */
public class Defter {
    static Set<String> ASLA_AD = new LinkedHashSet<>();
    static Set<String> ETIKET_DOLGU = new LinkedHashSet<>();
    static int ORTA_ESIK, UST_SINIR;
    static final Pattern ETIKET = Pattern.compile("\\[([^\\[\\]]{1,40})]?");
    static final Pattern KELIME = Pattern.compile("\\b\\p{Lu}[\\p{L}]{2,}\\b");
    static final Pattern CUMLE_AYIR = Pattern.compile("(?<=[.!?…])\\s+");
    static final String KIRP = "\"'’()[],.!?:;…-";

    static Set<String> kesinEtiket = new HashSet<>();
    static Set<String> kesinMetin = new HashSet<>();
    static Map<String, Integer> sayac = new HashMap<>();
    static Set<String> kucukGorulen = new HashSet<>();
    static int hata = 0;

    static Set<String> kume(String s, String isaret) {
        int i = s.indexOf(isaret);
        if (i < 0) throw new RuntimeException("kaynakta yok: " + isaret);
        int j = s.indexOf("(", i), derinlik = 0, son = -1;
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

    static void yukle(String kaynak) throws Exception {
        String s = new String(Files.readAllBytes(Paths.get(kaynak + "/OfflineFix.kt")), "UTF-8");
        ASLA_AD.addAll(kume(s, "private val ASLA_AD = setOf("));
        ETIKET_DOLGU.addAll(kume(s, "private val ETIKET_DOLGU = setOf("));
        Matcher e = Pattern.compile("ORTA_ESIK = (\\d+)").matcher(s);
        if (!e.find()) throw new RuntimeException("ORTA_ESIK yok");
        ORTA_ESIK = Integer.parseInt(e.group(1));
        Matcher u = Pattern.compile("UST_SINIR = (\\d+)").matcher(s);
        if (!u.find()) throw new RuntimeException("UST_SINIR yok");
        UST_SINIR = Integer.parseInt(u.group(1));
        if (ASLA_AD.size() < 60 || ETIKET_DOLGU.size() < 10)
            throw new RuntimeException("BAYAT AYNA: ASLA_AD=" + ASLA_AD.size()
                + ", ETIKET_DOLGU=" + ETIKET_DOLGU.size());
        // ENTEGRASYON: NameShield defteri gercekten soruyor mu? Defter
        // dogru calissa bile bu baglanti kopuksa hicbir sey degismez.
        int iS = s.indexOf("fun shield(text: String): Kalkanli");
        if (iS < 0) throw new RuntimeException("NameShield.shield bulunamadi");
        String govde = s.substring(iS, Math.min(s.length(), iS + 1500));
        if (!govde.contains("AdDefteri.adMi"))
            throw new RuntimeException("KOPUK BAGLANTI: NameShield.shield AdDefteri'ye "
                + "sormuyor — defter ogrense bile adlar korunmaz.");
        String cs = new String(Files.readAllBytes(
            Paths.get(kaynak + "/CaptureService.kt")), "UTF-8");
        if (!cs.contains("AdDefteri.temizle()"))
            throw new RuntimeException("AdDefteri.temizle() hic cagrilmiyor — defter "
                + "yapimlar arasinda sifirlanmiyor, bir filmin adlari sonrakine tasiyor.");
        if (!cs.contains("AdDefteri.ogren("))
            throw new RuntimeException("KOPUK BAGLANTI: CaptureService AdDefteri.ogren "
                + "cagirmiyor — defter hic dolmaz.");
        // Ogrenme CEVIRIDEN ONCE olmali
        int iO = cs.indexOf("AdDefteri.ogren("), iR = cs.indexOf("runTranslation(source, runId)");
        if (iO < 0 || iR < 0 || iO > iR)
            throw new RuntimeException("SIRA BOZUK: AdDefteri.ogren, ceviriden SONRA "
                + "cagriliyor — satirin kendi adlari o satirda korunmaz.");
        System.out.println("  asla-ad " + ASLA_AD.size() + ", dolgu " + ETIKET_DOLGU.size()
            + ", orta-esik " + ORTA_ESIK + ", ust-sinir " + UST_SINIR);
    }

    static String kirp(String s) {
        int a = 0, b = s.length();
        while (a < b && KIRP.indexOf(s.charAt(a)) >= 0) a++;
        while (b > a && KIRP.indexOf(s.charAt(b - 1)) >= 0) b--;
        String r = s.substring(a, b);
        int ap = r.indexOf('\''); if (ap >= 0) r = r.substring(0, ap);
        ap = r.indexOf('’'); if (ap >= 0) r = r.substring(0, ap);
        return r;
    }

    static boolean uygun(String k) {
        String kucuk = k.toLowerCase();
        return k.length() >= 3 && !ASLA_AD.contains(kucuk) && !ETIKET_DOLGU.contains(kucuk);
    }

    static void etiketEkle(String k) {
        if (!uygun(k)) return;
        if (kesinEtiket.size() < UST_SINIR) kesinEtiket.add(k.toLowerCase());
    }

    static void metinEkle(String k) {
        if (!uygun(k)) return;
        String kucuk = k.toLowerCase();
        if (kesinEtiket.contains(kucuk)) return;
        if (kesinMetin.size() < UST_SINIR) kesinMetin.add(kucuk);
    }

    static void sayacEkle(String k) {
        if (!uygun(k)) return;
        String kucuk = k.toLowerCase();
        if (kesinEtiket.contains(kucuk) || kesinMetin.contains(kucuk)) return;
        if (sayac.size() >= UST_SINIR && !sayac.containsKey(kucuk)) return;
        sayac.merge(kucuk, 1, Integer::sum);
    }

    static void kucukEkle(String k) {
        if (k.length() < 3) return;
        if (kucukGorulen.size() < UST_SINIR * 4) kucukGorulen.add(k.toLowerCase());
    }

    static boolean adMi(String kucuk) {
        if (kesinEtiket.contains(kucuk)) return true;
        if (kucukGorulen.contains(kucuk)) return false;
        return kesinMetin.contains(kucuk) || sayac.getOrDefault(kucuk, 0) >= ORTA_ESIK;
    }

    static void taze() { kesinEtiket.clear(); kesinMetin.clear(); sayac.clear(); kucukGorulen.clear(); }

    static void ogren(String satir) {
        if (satir == null || satir.trim().isEmpty()) return;
        Matcher m = ETIKET.matcher(satir);
        while (m.find()) {
            String ic = m.group(1).trim();
            if (ic.isEmpty()) continue;
            String[] sz = ic.split("[\\s,]+");
            List<String> sozcukler = new ArrayList<>();
            for (String x : sz) if (!x.trim().isEmpty()) sozcukler.add(x);
            if (sozcukler.isEmpty()) continue;
            String ilkSade = kirp(sozcukler.get(0));
            int basla = ETIKET_DOLGU.contains(ilkSade.toLowerCase()) ? 1 : 0;
            if (basla >= sozcukler.size()) continue;
            String bas = kirp(sozcukler.get(basla));
            if (bas.isEmpty() || !Character.isUpperCase(bas.charAt(0))) continue;
            for (int i = basla; i < sozcukler.size(); i++) {
                String k = kirp(sozcukler.get(i));
                if (!k.isEmpty() && Character.isUpperCase(k.charAt(0)) && KELIME.matcher(k).matches())
                    etiketEkle(k);
            }
        }
        String etiketsiz = ETIKET.matcher(satir).replaceAll(" ").trim();
        for (String cumle : CUMLE_AYIR.split(etiketsiz)) {
            String c = cumle.trim();
            if (c.isEmpty()) continue;
            String[] sz = c.split("\\s+");
            if (sz.length > 0) {
                String ilk = kirp(sz[0]);
                if (!ilk.isEmpty() && Character.isLowerCase(ilk.charAt(0))) kucukEkle(ilk);
            }
            for (int i = 1; i < sz.length; i++) {
                String k = kirp(sz[i]);
                if (k.length() < 3) continue;
                if (Character.isLowerCase(k.charAt(0))) { kucukEkle(k); continue; }
                if (!Character.isUpperCase(k.charAt(0))) continue;
                if (!KELIME.matcher(k).matches()) continue;
                String onceki = sz[i - 1].toLowerCase().replaceAll("[,.\"']", "");
                String sonraki = i + 1 < sz.length ? sz[i + 1].toLowerCase() : "";
                if (onceki.equals("the") && !sonraki.equals("of")) metinEkle(k);
                else if (!onceki.equals("the")) sayacEkle(k);
            }
        }
    }

    static final Pattern YAPISIK_ETIKET = Pattern.compile("\\[([Il|])(\\p{Lu}[\\p{L}'’]+)");

    static String acikEtiketKapat(String metin) {
        Matcher m = YAPISIK_ETIKET.matcher(metin);
        StringBuffer sb = new StringBuffer();
        while (m.find())
            m.appendReplacement(sb, Matcher.quoteReplacement(
                kesinEtiket.contains(m.group(2).toLowerCase()) ? "[" + m.group(2) : m.group()));
        m.appendTail(sb);
        String onarilan = sb.toString();
        String[] parcalar = onarilan.split("(?<=\\s)-(?=\\s*[\\[(])", -1);
        if (parcalar.length == 1) return tekEtiketKapat(onarilan);
        List<String> out = new ArrayList<>();
        for (String p : parcalar) {
            String bas = p.replaceAll("(?s)^( *).*$", "$1"), son = p.replaceAll("(?s)^.*?( *)$", "$1");
            out.add(bas + tekEtiketKapat(p.trim()) + son);
        }
        return String.join("-", out);
    }

    static String tekEtiketKapat(String metin) {
        String t = metin.replaceAll("^\\s+", "");
        if (t.length() < 4 || (t.charAt(0) != '[' && t.charAt(0) != '(')) return metin;
        String bas = t.substring(0, Math.min(42, t.length()));
        if (bas.indexOf(']') >= 0 || bas.indexOf(')') >= 0) return metin;
        List<String> sz = new ArrayList<>();
        for (String x : t.substring(1).split(" ")) if (!x.isEmpty()) sz.add(x);
        int n = 0;
        while (n < sz.size() && n < 3 && Character.isUpperCase(sz.get(n).charAt(0))
            && kesinEtiket.contains(sz.get(n).replaceAll("^['\u2019.,]+|['\u2019.,]+$", "").toLowerCase())) n++;
        if (n == 0 || n >= sz.size()) return metin;
        return "[" + String.join(" ", sz.subList(0, n)) + "] " + String.join(" ", sz.subList(n, sz.size()));
    }

    static void kapat(String giris, String beklenen) {
        String c = acikEtiketKapat(giris);
        if (!c.equals(beklenen)) {
            hata++;
            System.out.println("  X acikEtiketKapat: " + giris + "\n      beklenen: " + beklenen + "\n      cikan   : " + c);
        }
    }

    static void korunur(String ad) {
        if (!adMi(ad.toLowerCase())) {
            hata++;
            System.out.println("  X KORUNMUYOR: " + ad + " (defterde yok)");
        }
    }

    static void korunmaz(String ad) {
        if (adMi(ad.toLowerCase())) {
            hata++;
            System.out.println("  X YANLIS KORUNDU: " + ad + " — bu kelime cevrilmeliydi");
        }
    }

    public static void main(String[] a) throws Exception {
        yukle(a[0]);

        // --- Kullanicinin 11 Kasim 9.74 kaydi, SIRAYLA
        String[] kayit = {
            "First, we get this.",
            "[rumbling] -[metallic creaking]",
            "(whooshingl",
            "[the Captain It will take some time to reach our destination,",
            "and I'm sure you all wish to rest.",
            "Luckily for you, here on the Venture,",
            "Our guest accommodations are second to none.",
            "It take the couch.",
            "[Alien] Yeah, probably for the best. -[Telia] Are you sure?",
            "We really appreciate it.",
            "[Nolan] It'll be fine.",
            "(Alien and Telia moaning loudlyl",
            "[Alien] Oh, yeah. Oh. Oh, yeah.",
            "[atmospheric music playing]",
            "Captain, what's happening?",
            "but there's a few friendly space rocks in the neighborhood.",
            "[Alien] Why did you not stop before the giant asteroid field?",
            "Don't worry, friends, the Venture is a tough old lady.",
            "[electrical crackling, whooshingl [Tuva screams]",
            "[Nolan groans]",
            "Why don't you pull back to a safe distance?",
            "Alien and I will go in alone.",
            "[Nolan over implant I hid the coordinates of the Infinity Ray in my novel,",
        };
        for (String l : kayit) ogren(l);

        System.out.println("  defter: " + kesinEtiket.size() + " etiket, " + kesinMetin.size()
            + " metin, " + sayac.entrySet().stream().filter(x -> x.getValue() >= ORTA_ESIK).count()
            + " tekrarli, " + kucukGorulen.size() + " kucuk-gorulen");

        // --- KORUNMASI GEREKENLER (hepsi kayitta bozulmustu) ---------------
        korunur("Venture");    // "burada girisimde" -> "the Venture" kesin kanit
        korunur("Alien");      // "Yabanci ve ben"   -> [Alien] etiketi
        korunur("Telia");
        korunur("Nolan");
        korunur("Tuva");
        korunur("Infinity");

        // --- CEVRILMESI GEREKENLER ----------------------------------------
        korunmaz("Captain");   // "[the Captain]" -> "Kaptan" DOGRU cevirisi
        korunmaz("First");     // cumle basi, kanit degil
        korunmaz("Our");
        korunmaz("Luckily");
        korunmaz("Why");
        korunmaz("Dont");
        korunmaz("English");   // ASLA_AD
        korunmaz("Monday");
        korunmaz("Police");
        korunmaz("Earth");
        korunmaz("Mom");
        korunmaz("Doctor");
        // ses etiketi sozcukleri ad sanilmamali
        korunmaz("rumbling");
        korunmaz("crackling");
        korunmaz("groans");
        korunmaz("screams");
        korunmaz("metallic");

        // --- Cumle basi TEK BASINA kanit olmamali: taze defter -------------
        taze();
        ogren("Forgiveness.");
        ogren("Barbara.");
        ogren("Corrigan.");
        korunmaz("Forgiveness");
        korunmaz("Barbara");    // tek basina cumle: kanit yok (YZ zaten koruyor)

        // --- "the" + buyuk harf TEK gecisle kesin --------------------------
        taze();
        ogren("He boarded the Daystrom yesterday.");
        korunur("Daystrom");

        // --- Tekrarli orta-cumle: ESIK kadar gecis gerekli -----------------
        taze();
        ogren("I saw Grayson there.");
        korunmaz("Grayson");            // 1 gecis
        ogren("Then Grayson left.");
        korunur("Grayson");             // 2 gecis -> esik

        // --- OCR BUYUK HARF GURULTUSU ad sanilmamali -----------------------
        // Kayitta gercekten gecti: "'cause you were Worried some human book nerd"
        taze();
        ogren("[Alien over implant What, 'cause you were Worried some human book nerd");
        ogren("I was worried about you.");
        korunmaz("Worried");
        // Ayni kelime iki kez buyuk okunsa bile, kucuk gorulduyse ad degil
        taze();
        ogren("He was Worried then.");
        ogren("She was Worried too.");
        korunur("Worried");              // henuz kucuk gorulmedi
        ogren("Nobody was worried.");
        korunmaz("Worried");             // kucuk gorulunce GERI ALINIYOR
        // Etiketten gelen ad geri ALINMAZ
        taze();
        ogren("[Salt] Get down!");
        ogren("Pass the salt please.");
        korunur("Salt");                 // etiket kanidi kesindir

        // --- 9.84: KURUM OBEGI ad degil (12 Kasim kaydi) -------------------
        taze();
        ogren("The Coalition of Planets is fighting back against the Viltrum Empire,");
        korunmaz("Coalition");          // "Gezegenlerin Coalition" olmasin
        korunmaz("Empire");
        korunur("Viltrum");
        taze();
        ogren("Welcome aboard the Venture.");
        korunur("Venture");             // ciplak "the X" hala ad

        // --- 9.84: ACIK KALMIS konusmaci etiketi ---------------------------
        taze();
        ogren("[Space Racer] You thought wrong, Viltrumite.");
        ogren("[Alien] You said he was dead! -[Nolan] I thought he was.");
        kapat("(Space Racer To me!", "[Space Racer] To me!");
        kapat("[Alien We came here because we are gathering weapons.",
              "[Alien] We came here because we are gathering weapons.");
        // Kapanmis ya da ad icermeyen etiketlere DOKUNULMAZ
        kapat("[Alien] Ow! Stop!", "[Alien] Ow! Stop!");
        kapat("(whooshingl", "(whooshingl");
        kapat("[the Captain It will take some time", "[the Captain It will take some time");
        kapat("(Unknown Person says hi", "(Unknown Person says hi");
        kapat("[Alien", "[Alien");
        // 9.85: satir ORTASINDAKI acik etiket + ada yapismis "I"
        kapat("[Nolan] Earth? -[Alien That's what he said.", "[Nolan] Earth? -[Alien] That's what he said.");
        kapat("[INolan] And for that, I am more sorry.", "[Nolan] And for that, I am more sorry.");
        kapat("[Iris] Hello.", "[Iris] Hello.");      // "ris" bilinen ad degil: dokunma
        kapat("He said - (maybe) no.", "He said - (maybe) no.");
        String src = new String(Files.readAllBytes(Paths.get(a[0] + "/CaptureService.kt")), "UTF-8");
        if (!src.contains("AdDefteri.acikEtiketKapat("))
            throw new RuntimeException("KOPUK BAGLANTI: acikEtiketKapat CaptureService'e bagli degil.");

        if (hata > 0) { System.out.println("DEFTER DUSTU: " + hata + " hata."); System.exit(1); }
        System.out.println("Ad defteri testi gecti: adlar akistan ogreniliyor, "
            + "unvan/dil/takvim kelimeleri cevrilmeye devam ediyor.");
    }
}

import java.io.*; import java.nio.file.*; import java.util.*; import java.util.regex.*; import java.util.zip.*;
/**
 * OcrOnar.susluCop + temizKuyruk aynasi. Sozluk gercek .gz dosyasindan okunur.
 * Kurallar OcrSozluk.kt'den calisma aninda okunmuyor (duz kod); bayatlama
 * alarmi icin esik degeri kaynaktan dogrulaniyor.
 */
public class Suslu {
    static Set<String> SOZ = new HashSet<>();
    static Set<String> KISALTMA = new HashSet<>();
    static int ESIK;
    static final Pattern IKI_BUYUK = Pattern.compile("^\\p{Lu}{2}\\p{Ll}");
    static final Pattern OZEL_AD = Pattern.compile("^\\p{Lu}\\p{Ll}+$");
    static int hata = 0, KARISAN = 0;
    static final String KIRP = ".,!?:;\"'’-…[]";

    static String kirp(String s) {
        int a = 0, b = s.length();
        while (a < b && KIRP.indexOf(s.charAt(a)) >= 0) a++;
        while (b > a && KIRP.indexOf(s.charAt(b - 1)) >= 0) b--;
        return s.substring(a, b);
    }

    static void yukle(String kaynak, String gz) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(gz)), "UTF-8"))) {
            String l; while ((l = r.readLine()) != null) if (!l.isEmpty()) SOZ.add(l.trim().toLowerCase());
        }
        String s = new String(Files.readAllBytes(Paths.get(kaynak + "/OcrSozluk.kt")), "UTF-8");
        int i = s.indexOf("private val KISALTMA = setOf(");
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(s.substring(i, s.indexOf(")", i)));
        while (m.find()) KISALTMA.add(m.group(1));
        Matcher e = Pattern.compile("return puan >= (\\d+)").matcher(s);
        if (!e.find()) throw new RuntimeException("esik bulunamadi");
        ESIK = Integer.parseInt(e.group(1));
        // 9.87 esikleri kaynaktan: degisirse ayna da degisir, bayatlamaz.
        Matcher o = Pattern.compile("return toplam >= (\\d+) && bilinen \\* 10 >= toplam \\* (\\d+)").matcher(s);
        if (!o.find()) throw new RuntimeException("onEkGercek esigi bulunamadi");
        ON_EN_AZ = Integer.parseInt(o.group(1)); ON_ORAN = Integer.parseInt(o.group(2));
        Matcher g1 = Pattern.compile("if \\(kisa\\.size < (\\d+)\\) return false").matcher(s);
        Matcher g2 = Pattern.compile("return eslesen \\* 10 >= kisa\\.size \\* (\\d+)").matcher(s);
        if (!g1.find() || !g2.find()) throw new RuntimeException("ayniGurultulu esigi bulunamadi");
        G_EN_AZ = Integer.parseInt(g1.group(1)); G_ORAN = Integer.parseInt(g2.group(1));
        // mantik korumalari: ayna mantigi elle kopyalandigi icin kaynakta durduklarini dogrula
        String cap = new String(Files.readAllBytes(Paths.get(kaynak + "/CaptureService.kt")), "UTF-8");
        String[][] koruma = {
            {s, "if (kisa.size * 2 < uzun.size) return false", "boy orani korumasi"},
            {s, "if (x.length < 4 || y.length < 4) return false", "kisa kelimede tek harf farki esit sayilmamali"},
            {s, "!IKI_BUYUK.containsMatchIn(k)", "DArrying gibi suslu kelime gercek sayilmamali"},
            {cap, "OcrOnar.susluCop(collapsed2) && !OcrOnar.onEkGercek(collapsed2, susluKuyruk)", "kirpma karari onEkGercek'e bagli degil"},
            {cap, "similar(text, translatingFor) || OcrOnar.ayniGurultulu(text, translatingFor)", "cevrilmekte olan satir bozuk okumayla yeniden cevriliyor"},
            {cap, "similar(text, shownSource) || OcrOnar.ayniGurultulu(text, shownSource)", "ekrandaki satir bozuk okumayla yeniden cevriliyor"},
            {cap, "OcrOnar.ayniGurultulu(source, translatingFor)", "ayni satirin cevabi gec sayiliyor"},
        };
        for (String[] k : koruma) if (!k[0].contains(k[1])) {
            hata++; System.out.println("  X KAYNAKTA YOK: " + k[1] + "  (" + k[2] + ")");
        }
        System.out.println("  sozluk " + SOZ.size() + ", kisaltma " + KISALTMA.size() + ", esik " + ESIK);
    }

    static boolean susluCop(String metin) {
        String[] kel = metin.trim().split("\\s+");
        if (kel.length < 4) return false;
        int puan = 0;
        for (String ham : kel) {
            String k = kirp(ham);
            if (k.isEmpty()) continue;
            String kucuk = k.toLowerCase();
            boolean rakam = false, harf = false;
            for (char c : k.toCharArray()) { if (Character.isDigit(c)) rakam = true; if (Character.isLetter(c)) harf = true; }
            if (rakam && harf && k.length() >= 4) { puan += 2; continue; }
            if (IKI_BUYUK.matcher(k).find()) { puan += 2; continue; }
            if (k.length() == 1) { if (!kucuk.equals("i") && !kucuk.equals("a")) puan += 2; continue; }
            if (k.length() <= 3 && k.equals(k.toUpperCase()) && !KISALTMA.contains(kucuk)) { puan++; continue; }
            if (k.length() == 2 && !SOZ.contains(kucuk)) puan++;
        }
        return puan >= ESIK;
    }

    static final Set<String> KUCUK_OK = new HashSet<>(Arrays.asList("i","a","to","of","he","be","is","it"));

    static String temizKuyruk(String metin) {
        String[] p = metin.trim().split("\\s+");
        if (p.length < 5) return null;
        int bas = p.length, sayac = 0;
        for (int i = p.length - 1; i >= 0; i--) {
            String k = kirp(p[i]);
            int ap = k.indexOf('\''); if (ap >= 0) k = k.substring(0, ap);
            boolean tanidik = k.length() >= 2 && (SOZ.contains(k.toLowerCase())
                || OZEL_AD.matcher(k).matches() || KUCUK_OK.contains(k.toLowerCase()));
            if (!tanidik) break;
            bas = i; sayac++;
        }
        if (sayac < 4 || bas == 0) return null;
        return String.join(" ", Arrays.copyOfRange(p, bas, p.length));
    }

    /** OcrOnar.onEkGercek aynasi (9.87). */
    static boolean onEkGercek(String metin, String kuyruk) {
        String[] p = metin.trim().split("\\s+");
        int kuyrukBoyu = kuyruk == null ? 0 : kuyruk.trim().split("\\s+").length;
        int onBoyu = Math.max(0, Math.min(p.length, p.length - kuyrukBoyu));
        int toplam = 0, bilinen = 0;
        for (int i = 0; i < onBoyu; i++) {
            String k = kirp(p[i]);
            int ap = k.indexOf('\''); if (ap >= 0) k = k.substring(0, ap);
            ap = k.indexOf('\u2019'); if (ap >= 0) k = k.substring(0, ap);
            if (k.length() < 3) continue;
            toplam++;
            boolean harf = true;
            for (char c : k.toCharArray()) if (!Character.isLetter(c)) harf = false;
            if (harf && !IKI_BUYUK.matcher(k).find() && SOZ.contains(k.toLowerCase())) bilinen++;
        }
        return toplam >= ON_EN_AZ && bilinen * 10 >= toplam * ON_ORAN;
    }
    static int ON_EN_AZ, ON_ORAN, G_EN_AZ, G_ORAN;

    /** CaptureService'teki karar: suslu VE on ek gercek degilse kirp/at. */
    static boolean kirpilir(String s) {
        if (!susluCop(s)) return false;
        return !onEkGercek(s, temizKuyruk(s));
    }

    // ---- OcrOnar.ayniGurultulu aynasi (9.87) ----
    static List<String> sozcukler(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{Mn}+", "").toLowerCase();
        List<String> o = new ArrayList<>();
        for (String w : n.split("[^\\p{L}\\p{Nd}'\u2019]+")) if (!w.isEmpty()) o.add(w);
        return o;
    }
    static boolean yakin(String x, String y) {
        if (x.equals(y)) return true;
        if (x.length() < 4 || y.length() < 4) return false;
        int fark = x.length() - y.length();
        if (fark > 1 || fark < -1) return false;
        int i = 0;
        while (i < x.length() && i < y.length() && x.charAt(i) == y.charAt(i)) i++;
        int j = 0;
        while (j < x.length() - i && j < y.length() - i
            && x.charAt(x.length() - 1 - j) == y.charAt(y.length() - 1 - j)) j++;
        return x.length() - i - j <= 1 && y.length() - i - j <= 1;
    }
    static boolean ayniGurultulu(String a, String b) {
        List<String> sa = sozcukler(a), sb = sozcukler(b);
        List<String> kisa = sa.size() <= sb.size() ? sa : sb;
        List<String> uzun = sa.size() <= sb.size() ? sb : sa;
        if (kisa.size() < G_EN_AZ) return false;
        if (kisa.size() * 2 < uzun.size()) return false;
        List<String> kalan = new ArrayList<>(uzun);
        int es = 0;
        for (String w : kisa) {
            int bul = -1;
            for (int i = 0; i < kalan.size(); i++) if (yakin(w, kalan.get(i))) { bul = i; break; }
            if (bul >= 0) { es++; kalan.remove(bul); }
        }
        return es * 10 >= kisa.size() * G_ORAN;
    }
    static void ayni(String a, String b) {
        if (!ayniGurultulu(a, b) || !ayniGurultulu(b, a)) {
            hata++; System.out.println("  X AYNI SATIR TANINMADI:\n      " + a + "\n      " + b);
        }
    }
    static void ayri(String a, String b) {
        if (ayniGurultulu(a, b) || ayniGurultulu(b, a)) {
            hata++; System.out.println("  X AYRI SATIRLAR KARISTI:\n      " + a + "\n      " + b);
        }
    }

    static void cop(String s, String beklenenKuyruk) {
        if (!kirpilir(s)) { hata++; System.out.println("  X ARTIK KIRPILMIYOR (on ek gercek sanildi): " + s.substring(0, Math.min(60, s.length()))); return; }
        boolean c = susluCop(s);
        String k = c ? temizKuyruk(s) : null;
        if (!c) { hata++; System.out.println("  X COP SANILMADI: " + s.substring(0, Math.min(60, s.length()))); return; }
        if (!Objects.equals(k, beklenenKuyruk)) {
            hata++;
            System.out.println("  X " + s.substring(0, Math.min(56, s.length())));
            System.out.println("      beklenen kuyruk: " + beklenenKuyruk);
            System.out.println("      cikan           : " + k);
        }
    }

    static void temiz(String s) {
        if (kirpilir(s)) { hata++; System.out.println("  X YANLIS ALARM: " + s); }
    }

    public static void main(String[] a) throws Exception {
        yukle(a[0], a[1]);
        // --- Shrek fragmani: basi suslu yazi copu, sonu gercek altyazi
        // "Drince" (OCR'in bozdugu "Prince") ozel ad bicimine uyuyor ve
        // kuyrukta kaliyor. KABUL EDILEBILIR kalinti: gercek cumle korunuyor.
        // Ozel adlari kuyruktan atmak "Nolan went home" gibi satirlari
        // bozardi (kuyruk 2 kelimeye duser, satirin tamami atilir).
        cop("ch At be w As worthy o F DArrying che Drince who had to prove to the King of Far Far Away",
            "Drince who had to prove to the King of Far Far Away");
        cop("ch At be w As Worthy o F MArrying he prncao that he was worthy of marrying the princess.",
            "that he was worthy of marrying the princess.");
        cop("chat be w As WOrthy ha nn that he was worthy of marrying the princess.",
            "that he was worthy of marrying the princess.");
        cop("ch At be w AS WOrchy o F MATTying that he was worthy of marrying the princess.",
            "that he was worthy of marrying the princess.");
        // --- GERCEK replikler: hicbiri cop sayilmamali
        for (String s : new String[]{
            "Put it on speakerphone.", "It is what it is, my friend.",
            "I'm holding out for a hero 'til the morning light",
            "With The Witcher 4, we're are stepping things up.",
            "Thank you, Wyatt, and hello everyone.", "[SHREK] Great job, guys.",
            "First things first, I'm here with Sebastian and Keitech from CD Projekt Red.",
            "Captured on PS5", "Trailer I PS5 Games", "And he's gotta be larger than life",
            "This is Worg, Download and Councilor Tuva.",
            "We're the finest ship in the Coalition's navy",
            "Yo! Crackheads! Woo!", "I guess they know we're coming.", "No, General.",
            "[Grand Regent] How many are dead?", "Eh, fo sho. Yeah, yeah. Mmm hmm.",
            "Alright, that's the block right here.",
            "Let me tell y'all something, at this point I really don't give a fuck.",
            "Hey, yo, Ray! It's twelve! They tooled up and everything!",
            "Our friends on the task force are on their way.",
            "I know a ship we can take that won't raise any suspicions.",
            "announced a collaboration to make open-world development in Unreal even better.",
        }) temiz(s);
        // --- 9.87: 12 Kasim 9.86 kaydi (Black Myth: Wukong, ince yazi).
        // Bunlar bozuk okunmus GERCEK satirlar: kirpilmamali, atilmamali.
        for (String g : new String[]{
            "Because d hate to let you miss how IH slaughter each mongrel of the Court.",
            "III Slaughter each Mongrel of the Court those below those above and that Because ('d hate to let you miss how flt slaughter each mongrel of the Court.",
            "II Slaughter each Mongrel of the Court those below those above and that Because d hate to let you miss how I slaughter each mongrel of the Gourt.",
        }) temiz(g);

        // --- ayni satirin bozuk okumalari AYNI sayilmali (kayittan birebir)
        String[] because = {
            "Beoause1aate to let you miss row 'Slaughter each mongrel of the Court",
            "Because 'ahạte to let you miss how Tll slaughter each mongrel of the Court.",
            "Because I'dhate to let you niss how tit slaughter each mongrel of the Court.",
            "v Becąuse Ta hate to let you miss how.ll slaughter each mongrel of the Court.",
            "Because'dhate to let you miss trow HSlaughter each mongrel of the Court",
            "Because d hate to let you miss how IH slaughter each mongrel of the Court.",
        };
        for (int i = 0; i < because.length; i++)
            for (int j = i + 1; j < because.length; j++) ayni(because[i], because[j]);
        String[] pluck = {
            "would it be fun ifi pluekour extra eye gut for. my shack, and allow you to keep the",
            "Wouldn ibeun ilpludk your extra eye out för my snack, añd allow you to keep the",
            "woulan't it be fun if I pluds your extra eye out for my snack, and allow you to keep the",
            "t be fun iflpluck your extra eye but fo my snack, and allow you to keep the Wouldh",
            "Wouldh 1tbe fun iflpluckyour, extra eye out fot my shack, and allow you to keep the",
            "1t be iun it pluck your, extra eye out for my snack, and allow you to keep the Would",
        };
        for (int i = 0; i < pluck.length; i++)
            for (int j = i + 1; j < pluck.length; j++) ayni(pluck[i], pluck[j]);
        // iki AYRI satir birbirine karismamali
        for (String x : because) for (String y : pluck) ayri(x, y);
        ayri(because[1], "Those below, those above, and that black mutt of yours.");
        ayri("What do you want from me?", "What do you want me to do?");
        ayri("I don't know what you want me to say to him.", "I don't know what he wants me to do about it.");
        ayri("We just escaped and got back here now.", "You were just supposed to go to Earth and back.");
        // gercek kayit korpuslari: ARDISIK iki farkli replik ayni sayilirsa
        // ikincisi hic cevrilmez. Hic eslesme olmamali.
        int cift = 0, karisan = 0;
        for (String dosya : new String[]{"kayit_satirlari.txt", "batman_satirlari.txt"}) {
            List<String> sat = new ArrayList<>();
            for (String l : Files.readAllLines(Paths.get(a[2] + "/" + dosya), java.nio.charset.StandardCharsets.UTF_8))
                if (!l.trim().isEmpty()) sat.add(l.trim());
            for (int i = 0; i < sat.size(); i++)
                for (int j = i + 1; j < Math.min(sat.size(), i + 4); j++) {
                    String x = sat.get(i), y = sat.get(j);
                    if (x.equalsIgnoreCase(y)) continue;
                    cift++;
                    if (ayniGurultulu(x, y)) {
                        karisan++;
                        if (karisan <= 30) System.out.println("  ? " + dosya + ":\n      " + x + "\n      " + y);
                    }
                }
        }
        System.out.println("  korpus: " + cift + " komsu cift, ayni sayilan " + karisan);
        KARISAN = karisan;
        if (hata > 0) { System.out.println("SUSLU DUSTU: " + hata); System.exit(1); }
        System.out.println("Suslu yazi testi gecti: cop kirpiliyor, gercek replikler dokunulmuyor.");
    }
}

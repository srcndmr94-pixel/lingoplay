import java.io.*; import java.nio.file.*; import java.util.*; import java.util.regex.*; import java.util.zip.*;
public class Kalkan {
    static Set<String> SOZ = new HashSet<>();
    static Pattern ADAY;
    static Set<String> HER_ZAMAN_AD = new HashSet<>();
    static List<String> jetonlar = new ArrayList<>();
    static int hata = 0;

    static void yukle(String kaynak, String gz) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(gz)), "UTF-8"))) {
            String l; while ((l = r.readLine()) != null) if (!l.isEmpty()) SOZ.add(l.trim().toLowerCase());
        }
        String s = new String(Files.readAllBytes(Paths.get(kaynak + "/OfflineFix.kt")), "UTF-8");
        int i = s.indexOf("object NameShield {");
        if (i < 0) throw new RuntimeException("NameShield yok");
        String ss = s.substring(i);
        Matcher m = Pattern.compile("private val ADAY = Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)").matcher(ss);
        if (!m.find()) throw new RuntimeException("ADAY bulunamadi");
        ADAY = Pattern.compile(m.group(1).replace("\\\\", "\\"));
        Matcher m2 = Pattern.compile("private val HER_ZAMAN_AD = setOf\\(([^)]*)\\)").matcher(ss);
        if (m2.find()) { Matcher w = Pattern.compile("\"([^\"]+)\"").matcher(m2.group(1));
            while (w.find()) HER_ZAMAN_AD.add(w.group(1)); }
        for (int k = 1; k <= 12; k++) jetonlar.add("Qx" + k);
        System.out.println("  sozluk " + SOZ.size() + " kelime, ADAY=" + ADAY.pattern()
            + ", her-zaman-ad " + HER_ZAMAN_AD.size());
    }

    static Set<String> ORTADA_AD = new HashSet<>();
    /** NameShield.cumleOrtasi aynasi (9.92). */
    static boolean cumleOrtasi(String text, int bas) {
        int i = bas - 1;
        while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '"' || text.charAt(i) == '\'' || text.charAt(i) == '-')) i--;
        if (i < 0) return false;
        char c = text.charAt(i);
        return c != '.' && c != '!' && c != '?' && c != '\u2026' && c != ']' && c != ')' && c != ':';
    }

    static String[] shield(String text) {
        List<String[]> slots = new ArrayList<>();
        Matcher m = ADAY.matcher(text);
        StringBuilder sb = new StringBuilder(); int last = 0;
        while (m.find()) {
            String k = m.group(), kucuk = k.toLowerCase();
            boolean adMi = HER_ZAMAN_AD.contains(kucuk)
                || (ORTADA_AD.contains(kucuk) && cumleOrtasi(text, m.start()))
                || !SOZ.contains(kucuk);
            String yeni = k;
            if (adMi && slots.size() < jetonlar.size()) {
                String varOlan = null;
                for (String[] sl : slots) if (sl[1].equals(k)) varOlan = sl[0];
                if (varOlan != null) yeni = varOlan;
                else { yeni = jetonlar.get(slots.size()); slots.add(new String[]{yeni, k}); }
            }
            sb.append(text, last, m.start()).append(yeni); last = m.end();
        }
        sb.append(text.substring(last));
        StringBuilder ad = new StringBuilder();
        for (String[] sl : slots) { if (ad.length() > 0) ad.append(","); ad.append(sl[1]); }
        return new String[]{ sb.toString(), ad.toString() };
    }

    static void bekle(String ham, String beklenenMetin, String beklenenAdlar) {
        String[] r = shield(ham);
        if (!r[0].equals(beklenenMetin) || !r[1].equals(beklenenAdlar)) {
            hata++;
            System.out.println("  X " + ham);
            System.out.println("      beklenen: " + beklenenMetin + "   [ad: " + beklenenAdlar + "]");
            System.out.println("      cikan   : " + r[0] + "   [ad: " + r[1] + "]");
        }
    }

    /** NameShield.restore aynasi: jeton geri konma. */
    static String restore(String ceviri, List<String[]> slots) {
        String t = ceviri;
        for (String[] s : slots)
            t = Pattern.compile("(?i)" + Pattern.quote(s[0]) + "(?![0-9])")
                .matcher(t).replaceAll(Matcher.quoteReplacement(s[1]));
        return t;
    }

    public static void main(String[] a) throws Exception {
        yukle(a[0], a[1]);

        // --- JETON CAKISMASI. Qx1 geri konurken Qx12'nin icine girmemeli.
        // Onsuz "Marshall, DeLeon, Ernesto" yerine "Thaedus0, Thaedus1,
        // Thaedus2" cikiyordu: satirda 10+ ad varsa sessiz bozulma.
        String[] adlar = {"Thaedus","Talescria","Viltrumite","Nolan","Telia","Kregg",
                          "Argall","Boobie","Corrigan","Marshall","DeLeon","Ernesto"};
        List<String[]> slots = new ArrayList<>();
        StringBuilder kalkanli = new StringBuilder();
        for (int i = 0; i < adlar.length; i++) {
            slots.add(new String[]{"Qx" + (i + 1), adlar[i]});
            kalkanli.append("Qx").append(i + 1).append(i < adlar.length - 1 ? ", " : ".");
        }
        String beklenen = String.join(", ", adlar) + ".";
        String olan = restore(kalkanli.toString(), slots);
        if (!olan.equals(beklenen)) {
            hata++;
            System.out.println("  X jeton cakismasi");
            System.out.println("      beklenen: " + beklenen);
            System.out.println("      cikan   : " + olan);
        }
        // Kaynakta "(?![0-9])" gercekten var mi?
        String src = new String(Files.readAllBytes(Paths.get(a[0] + "/OfflineFix.kt")), "UTF-8");
        if (!src.contains("Regex.escape(s.token) + \"(?![0-9])\"")) {
            hata++;
            System.out.println("  X NameShield.restore jeton sinirini kullanmiyor: "
                + "Qx1 geri konurken Qx12'yi bozar");
        }
        // --- 9.92: sozlukte OLAN yaygin adlar, cumle ORTASINDA buyuk harfle
        Matcher mo = Pattern.compile("private val ORTADA_AD = setOf\\(([^)]*)\\)").matcher(src);
        if (mo.find()) { Matcher w = Pattern.compile("\"([^\"]+)\"").matcher(mo.group(1));
            while (w.find()) ORTADA_AD.add(w.group(1)); }
        if (ORTADA_AD.size() < 20 || !ORTADA_AD.contains("mark")) {
            hata++; System.out.println("  X ORTADA_AD okunamadi ya da 'mark' yok");
        }
        for (String ad : ORTADA_AD) if (!SOZ.contains(ad) || ad.length() < 4) {
            hata++; System.out.println("  X ORTADA_AD gereksiz/etkisiz girdi (sozlukte yok ya da 4 harften kisa): " + ad);
        }
        for (String parca : new String[]{
            "(kucuk in ORTADA_AD && cumleOrtasi(text, m.range.first))",
            "if (i < 0) return false",
            "text[i] != '.' && text[i] != '!' && text[i] != '?'"}) {
            if (!src.contains(parca)) { hata++; System.out.println("  X KAYNAKTA YOK (ayna bayat): " + parca); }
        }
        // 12 Kasim 9.91 kayitlari: "isaret haklıydı", "isaret kadar uzun"
        bekle("Maybe Mark was right.", "Maybe Qx1 was right.", "Mark");
        bekle("You'll be as tall as Mark soon.", "You'll be as tall as Qx1 soon.", "Mark");
        bekle("I raised Mark and Oliver in our house.", "I raised Qx1 and Qx2 in our house.", "Mark,Oliver");
        bekle("Have you seen Mark's phone?", "Have you seen Qx1's phone?", "Mark");
        // cumle BASINDA karar verilmez: bunlar fiil/ad olarak cevrilmeli
        bekle("Mark my words.", "Mark my words.", "");
        bekle("Stop. Chase him down.", "Stop. Chase him down.", "");
        bekle("[sighs] Hope you like it.", "[sighs] Hope you like it.", "");
        // kucuk harfliyse zaten ad degil
        bekle("They left a mark on the wall.", "They left a mark on the wall.", "");

        // --- KALKANLANMALI: sozlukte olmayan ozel adlar
        bekle("Before you ask me to perform the Toolock pul,",
              "Before you ask me to perform the Qx1 pul,", "Toolock");
        bekle("thrown into a Viltrumite prison.", "thrown into a Qx1 prison.", "Viltrumite");
        bekle("We need to see Thaedus immediately.", "We need to see Qx1 immediately.", "Thaedus");
        bekle("Well, welcome to Talescria.", "Well, welcome to Qx1.", "Talescria");
        bekle("You wonder if Allen is correct.", "You wonder if Qx1 is correct.", "Allen");
        // Ayni ad iki kez -> ayni jeton
        bekle("Thaedus knows. Thaedus always knows.",
              "Qx1 knows. Qx1 always knows.", "Thaedus");
        // --- KALKANLANMAMALI: gercek Ingilizce kelimeler
        bekle("Because we're both not dead.", "Because we're both not dead.", "");
        bekle("Alien. You're alive.", "Alien. You're alive.", "");
        bekle("Wait, what? Stop, stop!", "Wait, what? Stop, stop!", "");
        bekle("This is exceptional news.", "This is exceptional news.", "");
        bekle("Police! Don't fucking move!", "Police! Don't fucking move!", "");
        bekle("I know they're still tough as shit.", "I know they're still tough as shit.", "");
        bekle("Fifty? That cannot be true.", "Fifty? That cannot be true.", "");
        bekle("The number is quite high.", "The number is quite high.", "");
        bekle("Your education is over for today.", "Your education is over for today.", "");
        // 9.92: eskiden "Mark" sozlukte oldugu icin KORUNMUYORDU ve "isaret"
        // diye cevriliyordu (12 Kasim 9.91 kayitlari). Artik cumle ortasinda korunur.
        bekle("What would you have done if Mark didn't get powers?",
              "What would you have done if Qx1 didn't get powers?", "Mark");
        if (hata > 0) { System.out.println("KALKAN DUSTU: " + hata); System.exit(1); }
        System.out.println("Kalkan testi gecti: ozel adlar korunuyor, gercek kelimeler dokunulmuyor.");
    }
}

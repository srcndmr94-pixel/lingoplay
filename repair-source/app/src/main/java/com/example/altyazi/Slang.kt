package com.example.altyazi

/**
 * Suç / sokak argosu sözlüğü. İki iş görür:
 *  1. Satırda geçen argo için telefondaki yapay zekâya anlam ipucu verir
 *     ("jug = prison"), küçük model bunları kendi başına bilmiyor.
 *  2. Kesin kalıpları ML Kit'e gitmeden sade İngilizceye çevirir.
 */
object Slang {
    private const val OBJ = "(you|him|her|them|me|us|'em|that guy|the guy|this guy)"

    /**
     * Sokak ağzının TEK KAYNAĞI: gerçek olan ama hunspell listesinde bulunmayan
     * kelimeler.
     *
     * Burada durmasının sebebi, aynı listenin iki yerde tutulması gerekmesiydi:
     * OcrSozluk bunları "bilinen kelime" sayıp satırı çöp sanmamalı, SlangNormalizer
     * da bunları sade İngilizceye çevirmeli. İki ayrı liste tutulunca biri
     * güncellenip öteki unutuluyordu; "bruh" sözlükte olmadığı için
     * "Yeah. Go inside and ask for Raymond, bruh." satırı tamamen çöpe gidiyordu.
     */
    val SOKAK: Set<String> = setOf(
        "bruh", "brah", "bro", "bruv", "nah", "yeah", "yall", "aight", "tryna",
        "gonna", "wanna", "gotta", "hafta", "finna", "imma", "ima",
        "homie", "homies", "dawg", "holla", "legit", "whatchu", "whatcha",
        "lemme", "gimme", "outta", "kinda", "sorta", "dunno", "gotcha",
        "nuthin", "somethin", "nothin", "ain", "cuz", "coz", "sho",
    )

    /** Kalıp (regex, büyük/küçük harf duyarsız) -> anlamı. Belirsiz kelimeler sadece argo kalıbıyla eşleşir. */
    private val hints: List<Pair<Regex, String>> = listOf(
        // öldürmek
        "\\bic(e|ed|ing) $OBJ\\b|\\biced\\b" to "ice someone = kill (öldürmek)",
        "\\bwhack(ed)?\\b" to "whack = kill (öldürmek)",
        "\\bwast(e|ed) $OBJ\\b" to "waste someone = kill",
        "\\bcap(ped)? $OBJ\\b|\\bbust a cap\\b" to "cap someone = shoot (vurmak)",
        "\\bsmok(e|ed) $OBJ\\b" to "smoke someone = kill",
        "\\boff(ed)? $OBJ\\b" to "off someone = kill",
        "\\bpop(ped)? $OBJ\\b" to "pop someone = shoot/kill",
        "\\brub(bed)? out\\b" to "rub out = kill",
        "\\btake $OBJ out\\b|\\btook $OBJ out\\b" to "take someone out = kill",
        "\\bclip(ped)? $OBJ\\b" to "clip someone = kill",
        "\\bbump(ed)? $OBJ off\\b|\\bbump(ed)? off\\b" to "bump off = kill",
        "\\bsleep(s|ing)? with the fishes\\b" to "sleep with the fishes = be dead (murdered)",
        // hapis
        "\\b(the|in|to) jug\\b|\\bjug\\b" to "jug = prison (hapis)",
        "\\b(in|to) the can\\b" to "the can = prison",
        "\\b(in|to) the joint\\b" to "the joint = prison",
        "\\bslammer\\b" to "slammer = prison",
        "\\b(in|to) the pen\\b" to "the pen = prison",
        "\\bin stir\\b" to "stir = prison",
        "\\bbig house\\b" to "big house = prison",
        "\\bclink\\b" to "clink = prison",
        "\\b(in|to) the hole\\b" to "the hole = solitary confinement (hücre hapsi)",
        "\\bjuvie\\b" to "juvie = juvenile prison (ıslahevi)",
        "\\bon sabbatical\\b" to "on sabbatical = away (in prison)",
        "\\bdo(ing)? time\\b|\\bdid time\\b" to "do time = serve a prison sentence",
        "\\bscrews\\b|\\bthe screw\\b" to "screws = prison guards (gardiyanlar)",
        "\\bshiv\\b" to "shiv = improvised prison knife",
        "\\bcellmate\\b" to "cellmate = koğuş arkadaşı",
        "\\bmonkey[- ]suits?\\b" to "monkey suit = uniform",
        // kaçmak / paçayı kurtarmak
        "\\bskat(e|es|ed|ing)\\b" to "skate = get away with it, escape punishment (paçayı kurtarmak)",
        "\\bbeat the rap\\b" to "beat the rap = escape conviction",
        "\\bscram\\b" to "scram = get out fast",
        "\\blet's split\\b|\\bwe split\\b" to "split = leave",
        "\\bon the lam\\b" to "on the lam = on the run (firarda)",
        "\\bbreak ?out\\b|\\bbreakout\\b" to "break out = prison escape (firar)",
        // ispiyon
        "\\bsqueal(ed|ing|er)?\\b" to "squeal = snitch to police (ötmek)",
        "\\brat(s|ted|ting)?\\b" to "rat = snitch, informer (muhbir, ispiyoncu)",
        "\\bsnitch(ed|es|ing)?\\b" to "snitch = informer (muhbir)",
        "\\b(gonna|going to|will|'ll|started|start) sing(ing)?\\b" to "sing = confess/snitch to police (ötmek)",
        "\\bfink\\b" to "fink = informer",
        "\\bstool pigeon\\b" to "stool pigeon = informer",
        "\\bnarc\\b" to "narc = undercover cop / informer",
        // polis
        "\\bthe fuzz\\b" to "the fuzz = police",
        "\\bpigs\\b" to "pigs = police (aynasızlar)",
        "\\bthe heat\\b" to "the heat = police / police attention",
        "\\bfive-o\\b" to "five-o = police",
        "\\bit's twelve\\b|\\btwelve's\\b" to "twelve = police",
        "\\bniggas?\\b" to "nigga = guy, bro (adam, kanka, eleman); not about race",
        "\\bbricks?\\b" to "brick = kilo package of drugs (paket, mal)",
        "\\bwhere (the fuck |the hell )?\\w+ at\\b" to "where X at = where is X",
        "\\bfeds\\b" to "feds = federal agents",
        "\\bcopper\\b" to "copper = cop",
        "\\bbust(ed)?\\b" to "bust = arrest / raid (baskın, enselemek)",
        "\\bpinched\\b" to "pinched = arrested",
        "\\bcollared\\b" to "collared = arrested",
        // para
        "\\bdough\\b" to "dough = money",
        "\\bcheddar\\b" to "cheddar = money",
        "\\bmoolah\\b" to "moolah = money",
        "\\b(a|\\d+|two|three|five|ten|fifty|hundred) grand\\b" to "grand = 1000 dollars (bin dolar)",
        "\\bc-notes?\\b" to "C-note = 100 dollars",
        "\\b(my|your|his|her|their|our|the) cut\\b" to "cut = share of the money (pay)",
        "\\bbig score\\b|\\bthe score\\b" to "score = big theft / profit (vurgun)",
        "\\bthe take\\b" to "the take = stolen money (ganimet)",
        // silah
        "\\b(my|your|his|her|the) piece\\b(?! of)|\\bpacking a piece\\b" to "piece = gun (silah) when talking about weapons",
        "\\bheater\\b" to "heater = gun",
        "\\bgat\\b" to "gat = gun",
        "\\bstrapped\\b" to "strapped = armed (silahlı)",
        "\\btooled up\\b" to "tooled up = armed (silahlı)",
        "\\bpacking( heat)?\\b" to "packing = carrying a gun",
        // iş / suç
        "\\bheist\\b" to "heist = robbery (soygun)",
        "\\bthe job\\b" to "the job = the crime / robbery (iş, soygun) if criminals are talking",
        "\\b(the|easy) mark\\b" to "mark = victim of a con (enayi)",
        "\\bsucker\\b" to "sucker = dupe (enayi)",
        "\\bmooks\\b" to "mooks = thugs (adamlar)",
        "\\bgoons\\b" to "goons = thugs",
        "\\bwheelm[ae]n\\b" to "wheelman = getaway driver (kaçış şoförü)",
        "\\btag-along\\b" to "tag-along = someone who follows you around",
        "\\bon the spot\\b" to "on the spot = right there, at that moment",
        "\\bcapo\\b" to "capo = mob captain",
        "\\bmade man\\b" to "made man = full mafia member",
        "\\bthis stinks\\b|\\bit stinks\\b" to "this stinks = this is rotten/unfair/suspicious (bu iş kokuyor)",
        "\\bchump\\b" to "chump = fool",
        "\\bhomie\\b" to "homie = close friend",
        "\\bthe rap\\b" to "the rap = criminal charge",
        // genel deyimler (log'da yapay zekânın kaçırdıkları)
        "\\b\\p{Lu}\\p{Ll}+ dog\\b|\\bdawg\\b" to "'<Name> dog' / dawg = friendly address, buddy (kanka), not an animal",
        "\\bsalad (days|years)\\b" to "salad days = carefree youthful prime (gençliğin en güzel günleri)",
        "\\bon a roll\\b" to "on a roll = on a winning streak, things going great (işler tıkırında)",
        "\\bhard pass\\b" to "hard pass = definitely no (kesinlikle hayır)",
        "\\bweps\\b" to "weps = weapons (silahlar)",
        "\\bas you were\\b" to "as you were = carry on, continue what you were doing (devam edin / rahat)",
        "\\bhave $OBJ on\\b" to "have someone on = invite someone as a guest on a show (programa konuk almak)",
        "\\bbats?\\b" to "bat = the animal / Batman's bat symbol (yarasa), not a verb",
        "\\btop brass\\b" to "top brass = senior commanders (üst düzey amirler)",
        "\\bhave (their|his|her|your|my|our) backs?\\b" to "have someone's back = support/protect them (arkasında durmak)",
        "\\bput $OBJ up to\\b" to "put someone up to something = make them do it (birini bir işe kışkırtmak)",
        "\\bfor that matter\\b" to "for that matter = also, as well (hatta / o konuda)",
        "\\bto my knowledge\\b" to "to my knowledge = as far as I know (bildiğim kadarıyla)",
        "\\bi take it\\b" to "I take it = I assume (anladığım kadarıyla)",
        "\\bhit job\\b" to "hit job = a hostile attack piece / smear (karalama, linç)",
        "\\bcat burglar\\b" to "cat burglar = a skilled burglar who climbs into buildings (çatı hırsızı)",
        "\\bgo(es|ing)? after\\b|\\bwent after\\b" to "go after = attack, pursue (peşine düşmek, saldırmak)",
        // sokak argosu (kullanıcının 11 Kasım kaydı: uyuşturucu köşesi sahnesi)
        "\\btryna smoke\\b|\\bwanna smoke\\b|\\blooking to smoke\\b" to
            "'tryna smoke' here = asking if you want to buy/smoke WEED (ot/mal içmek), NOT cigarettes",
        "\\by'?all\\b|\\bya'?ll\\b" to "y'all = you (PLURAL); Turkish must use plural (siz / -sunuz)",
        "\\bcrackheads?\\b" to "crackhead = crack addict (keş), an insult",
        "\\bpick ?up\\b|\\bpickup\\b" to
            "in a drug/delivery scene 'pickup' = collecting a package (malı almak), not a vehicle",
        "\\bdrop ?off\\b" to "drop off = delivering a package (teslim etmek)",
        "\\bbruh\\b|\\bbro\\b|\\bbrah\\b" to "bruh/bro = friendly address (kanka, dostum)",
        "\\baight\\b|\\ba'ight\\b" to "aight = alright (tamam)",
        "\\bfo'? sho'?\\b" to "fo sho = for sure (kesinlikle)",
        "\\bwhatchu\\b|\\bwhatcha\\b" to "whatchu = what do you (ne ...-yorsun)",
        "\\bshake some shit\\b" to "shake some shit = stir up trouble (ortalığı karıştırmak)",
        "\\bwhat the fuck\\b" to "what the fuck = angry 'what is going on' (bu ne lan / ne oluyor)",
        // "mahalle" DEĞİL: kullanıcı orijinalliğin korunmasını istiyor ve
        // "blok" sokak ağzında Türkçede de kullanılıyor.

        // ----------------------------------------------------------------
        // GENEL SOKAK ARGOSU İPUÇLARI — oyuna özel değil.
        //
        // İpucu kalıp DEĞİL: çeviriyi zorlamıyor, yapay zekâya kelimenin
        // ANLAMINI söylüyor. Bu yüzden hiç görmediğim bir cümlede de işe
        // yarıyor ve en kötü durumda yok sayılıyor; yanlış çeviri üretmez.
        // Sabit karşılık yazmanın riskli olduğu, anlamı bağlama göre
        // değişen sözcükler buraya yazılıyor.
        // ----------------------------------------------------------------
        "\\bask(ing|ed)? for \\p{Lu}" to
            "'ask for <Name>' = birini sormak, içeri girip o kişiyi istemek; NOT 'görmeyi istemek'",
        "\\bcollect(ing|ed)?\\b" to "collect = parayı/malı almak (tahsil etmek), 'toplamak' değil",
        "\\bworkers?\\b" to "in a drug scene 'workers' = the dealers working the spot (çalışanlar, satıcılar)",
        "\\btrap(house)?\\b" to "trap / traphouse = uyuşturucu satılan ev (satış evi)",
        "\\bplug\\b" to "plug = tedarikçi, malı veren kişi",
        "\\bre-?up\\b" to "re-up = mal yenilemek, stok tazelemek",
        "\\bjack(ed|ing)?\\b" to "jack = soymak, gasp etmek (ya da araba çalmak)",
        "\\bslide(s|d)?\\b|\\bsliding\\b" to "slide = üstüne gitmek, baskın yapmak ya da uğramak",
        "\\bopps?\\b" to "opps = düşmanlar, karşı taraf",
        "\\bburner\\b" to "burner = izi sürülemeyen silah ya da telefon",
        "\\bstrap\\b(?! on)" to "strap = silah",
        "\\bglizzy\\b" to "glizzy = tabanca",
        "\\bhit a lick\\b|\\blick\\b" to "hit a lick = iyi bir vurgun yapmak; lick = soyulacak hedef",
        "\\bthe set\\b" to "the set = çetenin bölgesi, mahalle",
        "\\bo\\.?g\\.?\\b" to "OG = kıdemli, eski kurt",
        "\\bshorty\\b" to "shorty = küçük (genç biri) ya da kız",
        "\\bracks?\\b" to "rack = bin dolar (deste)",
        "\\bthe bag\\b" to "the bag = para, kazanç",
        "\\bno cap\\b" to "no cap = yalan yok (silah anlamındaki 'cap' değil)",
        "\\bdeadass\\b" to "deadass = ciddiyim, gerçekten",
        "\\bon god\\b|\\bon everything\\b" to "on God = yemin ederim",
        "\\bwhip\\b" to "whip = araba",
        "\\bpull(ed|ing)? up\\b" to "pull up = gelmek, çıkagelmek (araçla uğramak)",
        "\\bslide through\\b|\\bswing by\\b" to "slide through / swing by = uğramak",
        "\\brun it back\\b" to "run it back = baştan almak, bir daha yapmak",
        "\\bthere's smoke\\b|\\bgot smoke\\b" to "smoke (beef) = çatışma, sorun; sigara değil",
        "\\bmad\\b(?= \\p{Ll})" to "'mad' + sıfat = çok (mad tired = çok yorgun), kızgın değil",
        "\\bgang\\b|\\bgang gang\\b" to "gang = kanka diye seslenme de olabilir",
        "\\bbet\\b(?=[.,!?]|$)" to "bet = tamam, anlaştık",
        "\\bsay less\\b" to "say less = anladım, tamam",
        "\\bthe work\\b" to "the work = mal (uyuşturucu)",
        "\\bthe pack\\b" to "the pack = mal paketi",
        "\\b(?:a|the) drop\\b" to "a drop = teslim, bırakılan paket",
        "\\bmove(s|d)? (weight|work|packs?)\\b" to "move weight = mal satmak",
        "\\bfiends?\\b|\\bbaseheads?\\b" to "fiend / basehead = keş, bağımlı",
        "\\bsnitch(ing)? on\\b" to "snitch on = ispiyonlamak",
        "\\bbody(ied|ing)?\\b" to "body someone = öldürmek",
        "\\blay low\\b" to "lay low = ortalıkta görünmemek, sinmek",
        "\\bthe block\\b" to "the block = the street/corner they deal on (blok, o sokak), not a building block"
    ).map { (k, v) -> Regex("(?i)" + k.replace("\$OBJ", OBJ)) to v }

    /**
     * Satırda geçen argo terimlerinin anlamları (en fazla 3).
     *
     * 6'dan 3'e indi: ipuçları İSTEME ekleniyor ve istem her satırda baştan
     * işleniyor, yani her ipucu gecikmeye yazılıyor. 9.65 kaydında yapay zekâ
     * 3808 ms'ye çıkıp altyazı tempsuna yetişemez olmuştu. Sıradaki ilk
     * ipuçları en özgül olanlar (liste sırası dar kalıptan genele doğru),
     * o yüzden kesilen kısım en az işe yarayan kısım.
     */
    fun hintsFor(line: String): List<String> =
        hints.filter { it.first.containsMatchIn(line) }.map { it.second }.distinct().take(3)

    /** ML Kit'e gitmeden önce kesin kalıpları sade İngilizceye çevirir. */
    private val rewrites: List<Pair<Regex, String>> = listOf(
        "\\bice (you|him|her|them|me|us)\\b" to "kill $1",
        "\\b(whack|clip|off|waste|smoke) (you|him|her|them|me|us)\\b" to "kill $2",
        "\\bin the (jug|can|joint|slammer|pen|stir|clink|big house)\\b" to "in prison",
        "\\bto the (jug|can|joint|slammer|pen|stir|clink|big house)\\b" to "to prison",
        "\\b(you|he|she|they|we|i) skated?\\b" to "$1 got away with it",
        "\\b(you|he|she|they|we|i) skates?\\b" to "$1 get away with it",
        "\\bsquealed\\b" to "informed the police",
        "\\bsquealing\\b" to "informing the police",
        "\\bsquealer\\b" to "informer",
        "\\bratted (him|her|them|me|us|you) out\\b" to "betrayed $1 to the police",
        "\\bratted\\b" to "betrayed us to the police",
        "\\bon sabbatical\\b" to "away",
        "\\bjuvie\\b" to "juvenile prison",
        "\\btag-along[- ]buddy\\b" to "companion",
        "\\bon the spot\\b" to "right there",
        "\\bthe fuzz\\b" to "the police",
        "\\bpacking heat\\b" to "carrying a gun",
        "\\bon the lam\\b" to "on the run",
        // BURASI YALNIZ KELİME DAĞARCIĞI İÇİN. Dilbilgisi ve kısaltma
        // normalleştirmesi (ain't, gonna, wanna, gotta, y'all, tryna, aight,
        // fo sho, whatcha, dunno...) OfflineFix.kt içindeki SlangNormalizer'da
        // ve YALNIZCA orada. Burada kopyası tutulmaz.
        //
        // Neden: ikisi arka arkaya çalışıyor (Slang önce). Kopya kural
        // SlangNormalizer'ın daha akıllı kuralını eziyordu:
        //   "You ain't ask yourselves that?"
        //     yalnız SlangNormalizer -> "You didn't ask yourselves that?"  doğru
        //     kopya kural önce       -> "You are not ask yourselves that?" bozuk
        //   "Whatcha doing?"
        //     yalnız SlangNormalizer -> "what are you doing?"   doğru
        //     kopya kural önce       -> "what do you doing?"    bozuk
        "\\btryna smoke\\b|\\bwanna smoke\\b" to "looking to buy weed",
        // Cümle başındaki "Like," dolgu sözü: taslak "Beğenmek" diye çeviriyordu.
        "^like,\\s*" to "I mean, "
    ).map { Regex("(?i)" + it.first) to it.second }

    fun normalize(text: String): String {
        var t = text
        for ((re, rep) in rewrites) {
            t = re.replace(t) { m ->
                var r = rep
                for (g in 1..m.groupValues.lastIndex) r = r.replace("$" + g, m.groupValues[g])
                if (m.value.first().isUpperCase() && r.isNotEmpty()) r.substring(0, 1).uppercase() + r.substring(1) else r
            }
        }
        return t
    }
}

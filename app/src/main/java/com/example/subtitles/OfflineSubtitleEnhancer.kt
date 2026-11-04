package com.example.subtitles

import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * ÇEVRİM DIŞI altyazı kalitesi için düzeltme katmanı.
 *
 * Telefonun yerleşik çevirisini (ML Kit) şu sırayla sarar:
 *  1. OCR hatalarını düzeltir ("l always" -> "I've always", baştaki kopmuş "I" vb.)
 *  2. Cümlelere, gerekirse virgüllü parçalara bölüp SÖZLÜKTE arar (argo, küfür, komutlar, özel satırlar)
 *  3. Sözlükte olmayan parçayı yerleşik çeviriye verir
 *  4. Yerleşik çevirinin çevirmeden bıraktığı küfürleri Türkçeleştirir
 *
 * Sözlüğü siz büyütürsünüz: "ingilizce = türkçe" satırları (bkz. SubtitleGlossary.parse).
 */
object OcrTextCleaner {
    private val loneL = Regex("(?<![\\p{L}\\p{N}'])[l|](?=\\s|')")
    private val loneOneBeforeApostrophe = Regex("(?<![\\p{L}\\p{N}'])1(?=')")
    private val ivePattern = Regex("\\bI (always|never|just|only|also|really|ever) (been|had|done|seen|got|gone|known|made|told|tried)\\b")
    private val droppedI = Regex("^(got|gotta|gonna|wanna)\\b") // küçük harfle başlıyorsa baştaki "I" kopmuş demektir

    fun clean(raw: String): String {
        var t = raw.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
        t = t.replace(Regex("\\s+"), " ").trim()
        t = t.trimStart('-', '–', '—', ' ')
        t = loneL.replace(t, "I")
        t = loneOneBeforeApostrophe.replace(t, "I")
        t = ivePattern.replace(t) { "I've ${it.groupValues[1]} ${it.groupValues[2]}" }
        t = t.replace(Regex("^[|l]\\s*(?=I\\b)"), "")           // "|I already" -> "I already"
        t = t.replace(Regex("^[T|l](?=(already|always|never|just|am|was)\\b)"), "I ") // "Talready" -> "I already"
        t = t.replace(Regex("(?i)\\bthe y(?=['’]?(re|ll|ve|d)\\b)"), "they") // "the y're" -> "they're"
        t = t.replace(Regex("(?i)\\bthe y\\b"), "they")
        if (droppedI.containsMatchIn(t)) t = "I $t"
        return t
    }
}

class SubtitleGlossary private constructor(
    private val phrases: Map<String, String>,
    private val names: Set<String>
) {
    fun lookup(text: String): String? = phrases[keyOf(text)]
    fun isName(text: String): Boolean = text.trim().lowercase() in names
    fun startsWithName(text: String): Boolean {
        val first = text.trim().split(' ', '\'').firstOrNull()?.lowercase() ?: return false
        return first in names
    }

    companion object {
        fun keyOf(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

        /**
         * Satır biçimi:
         *   # yorum
         *   @Lu                      -> özel isim, çevrilmez
         *   holy shit = Hassiktir    -> "ingilizce = türkçe" (sondaki noktalama yazılmaz, metinden alınır)
         * Sonraki satırlar öncekini ezer; kendi satırlarınızı varsayılanın ALTINA ekleyin.
         */
        fun parse(text: String): SubtitleGlossary {
            val phrases = HashMap<String, String>()
            val names = HashSet<String>()
            for (line in text.lines()) {
                val l = line.trim()
                if (l.isEmpty() || l.startsWith("#")) continue
                if (l.startsWith("@")) { names += l.substring(1).trim().lowercase(); continue }
                val eq = l.indexOf('=')
                if (eq <= 0) continue
                val key = keyOf(l.substring(0, eq))
                val value = l.substring(eq + 1).trim().trimEnd('.', '!', '?', '…', ' ')
                if (key.isNotEmpty() && value.isNotEmpty()) phrases[key] = value
            }
            return SubtitleGlossary(phrases, names)
        }

        fun default(userEntries: String = ""): SubtitleGlossary = parse(DEFAULT_ENTRIES + "\n" + userEntries)

        const val DEFAULT_ENTRIES = """
# --- Komutlar, çatışma
police = Polis
freeze = Kıpırdama
hands up = Eller yukarı
put your hands up = Ellerini kaldır
drop it = Bırak onu
drop your weapon = Silahını bırak
drop the weapon = Silahı bırak
get down = Yere yat
get on the ground = Yere yat
on the ground = Yere yat
stay down = Yerde kal
stay with me = Benimle kal
don't move = Kıpırdama
don't fucking move = Kıpırdama lan
cover = Siper al
take cover = Siper alın
cover me = Beni kolla
move = Kımılda
move up = İlerle
push up = İlerle
go go go = Hadi hadi hadi
behind you = Arkanda
watch out = Dikkat et
look out = Dikkat
reload = Şarjör değiştir
i'm hit = Vuruldum
man down = Adam yaralandı
he's got a gun = Silahı var
he has a gun = Silahı var
shoot him = Vur onu
don't shoot = Ateş etme
hold your fire = Ateşi kesin
cease fire = Ateşi kesin
get out of here = Defol buradan
let's go = Hadi gidelim
come on = Hadi
hurry up = Çabuk ol
wait = Bekle
hold on = Bir dakika
shut up = Kapa çeneni
shut the fuck up = Kapa çeneni lan

# --- Küfür ve ünlemler
fuck = Siktir
fuck me = Siktir
fuck you = Siktir git
fuck off = Siktir git
fuck this = Siktir et bunu
fuck it = Siktir et
shit = Siktir
holy shit = Hassiktir
oh shit = Hassiktir
oh my god = Aman tanrım
damn = Lanet olsun
damn it = Lanet olsun
goddamn it = Lanet olsun
son of a bitch = Orospu çocuğu
motherfucker = Orospu çocuğu
bullshit = Saçmalık
asshole = Pislik herif
what the fuck = Ne bok
what the fuck is going on = Ne bok oluyor
what the fuck is going on here = Burada ne bok oluyor
what the fuck are you doing = Ne bok yiyorsun
what the hell = Ne oluyor yahu
what the hell is going on = Neler oluyor yahu
are you out of your mind = Aklını mı kaçırdın
aww = Ah
too bad = Ne yazık

# --- Günlük konuşma
are you okay = İyi misin
are you ok = İyi misin
thank you = Teşekkürler
thanks = Sağ ol
sorry = Özür dilerim
no way = Olamaz
what's going on = Neler oluyor
i don't know = Bilmiyorum
trust me = Bana güven
follow me = Beni takip et
this way = Bu taraftan
over here = Buraya

# --- Ek genel kalıplar
what the hell are you doing = Sen ne yapıyorsun be
what are you doing = Ne yapıyorsun
what do you want = Ne istiyorsun
what happened = Ne oldu
who are you = Sen kimsin
where are you = Neredesin
where are you going = Nereye gidiyorsun
get out = Çık dışarı
get out of my way = Çekil yolumdan
get away from me = Uzak dur benden
leave me alone = Beni rahat bırak
let me go = Bırak beni
let go = Bırak
help me = Yardım et bana
help = İmdat
run = Koş
run away = Kaç
go = Git
stop = Dur
stop it = Kes şunu
wait here = Burada bekle
stay here = Burada kal
stay back = Geri çekil
step back = Geri çekil
back off = Geri çekil
calm down = Sakin ol
relax = Sakin ol
take it easy = Sakin ol
easy = Yavaş ol
listen to me = Beni dinle
look at me = Bana bak
look = Bak
listen = Dinle
you idiot = Seni aptal
you bastard = Seni piç
you asshole = Seni pislik
you son of a bitch = Seni orospu çocuğu
you piece of shit = Seni pislik
piece of shit = Pislik
dumbass = Aptal
jackass = Salak
idiot = Aptal
scumbag = Pislik
bitch = Kaltak
bastard = Piç
suck my dick = Siktir git
kiss my ass = Siktir git
go to hell = Cehennem ol
go fuck yourself = Siktir git
get the fuck out = Defol
get the fuck out of here = Defol buradan
what the fuck was that = O da neydi böyle
what the hell was that = O da neydi böyle
are you fucking kidding me = Dalga mı geçiyorsun be
are you kidding me = Dalga mı geçiyorsun
you've got to be kidding me = Dalga geçiyor olmalısın
i'm going to kill you = Seni öldüreceğim
i'll kill you = Seni öldürürüm
you're dead = Öldün sen
you're gonna die = Öleceksin
i'm done = Bittim ben
i'm fine = İyiyim
i'm okay = İyiyim
i'm sorry = Özür dilerim
i can't = Yapamam
i can't do this = Bunu yapamam
i don't care = Umurumda değil
i don't understand = Anlamıyorum
i know = Biliyorum
i see = Anlıyorum
i got you = Seni tuttum
i've got you = Seni tuttum
i got it = Hallettim
got it = Anlaşıldı
copy that = Anlaşıldı
roger = Anlaşıldı
understood = Anlaşıldı
yes sir = Emredersiniz
no sir = Hayır efendim
yes = Evet
no = Hayır
yeah = Evet
yep = Evet
nope = Hayır
okay = Tamam
ok = Tamam
alright = Pekâlâ
all right = Pekâlâ
please = Lütfen
hello = Merhaba
hi = Selam
hey = Hey
goodbye = Hoşça kal
bye = Hoşça kal
good luck = Bol şans
be careful = Dikkatli ol
you ready = Hazır mısın
are you ready = Hazır mısın
ready = Hazır
let's do this = Hadi yapalım şunu
let's move = Hadi gidelim
let's get out of here = Hadi çıkalım buradan
we have to go = Gitmemiz lazım
we need to go = Gitmemiz lazım
we need to move = Hareket etmeliyiz
we gotta go = Gitmemiz lazım
we gotta move = Hareket etmeliyiz
we're out of time = Vaktimiz kalmadı
we're running out of time = Vaktimiz tükeniyor
there he is = İşte orada
there they are = İşte oradalar
he's dead = Öldü
they're coming = Geliyorlar
they're here = Geldiler
incoming = Geliyorlar
enemy down = Düşman indi
target down = Hedef vuruldu
clear = Temiz
all clear = Ortalık temiz
nice shot = Güzel atış
nice = Güzel
good job = Aferin
well done = Aferin
oh no = Olamaz
oh god = Aman tanrım
jesus = Aman tanrım
jesus christ = Hay Allah
oh my god = Aman tanrım
what = Ne
why = Neden
how = Nasıl
really = Gerçekten mi
seriously = Cidden mi
come here = Gel buraya
get over here = Gel buraya
"""

    /** ÖRNEK oyuna özel sözlük (isimler + kayıttaki satırlar). Kendi oyununuz için bunu kopyalayıp değiştirin. */
    const val GAME_ENTRIES = """
# --- İsimler (çevrilmez)
@Lu
@Boobie
@Méndez
@Ray
@Raymond
@cabrón

# --- Oyuna özel (sizin kayıttan)
it's him = O
it's fucking méndez = Siktiğimin Méndez'i bu
fuck boobie = Boobie'yi siktir et
we gotta get out of this = Bu işten sıyrılmamız lazım
i got boobie's bricks = Boobie'nin paketleri bende
hey man this ain't even loaded = Hey dostum, bunda mermi bile yok
can't you fucking handle a bunch of street pushers = Bir avuç sokak satıcısıyla bile başa çıkamıyor musunuz be
clean this shit up = Bu boku temizleyin
i've always been there for you = Hep yanında oldum
but this is what i get = Karşılığında aldığım bu mu yani
but this what i get = Karşılığında aldığım bu mu yani

aight hold up = Tamam, bekle
yeah he fucking knows = Evet, o kesinlikle biliyor
what the fuck you talking about = Ne bok konuşuyorsun sen
what the fuck are you talking about = Ne bok konuşuyorsun sen
my partner's off the grid right now you feel me = Ortağım şu an ortalarda yok, anlıyorsun ya
problem is he only worried about himself = Sorun şu ki sadece kendini düşünüyor
he seriously sent y'all over here right now = Sizi gerçekten şimdi buraya mı gönderdi
let's get these workers out of here man = Bu işçileri buradan çıkaralım dostum
i already told that motherfucker what the fuck is going on man = O orospu çocuğuna neler olduğunu zaten söyledim dostum
i already told that motherfucker what the fuck going on man = O orospu çocuğuna neler olduğunu zaten söyledim dostum
these two say they're for boobie = Bu ikisi Boobie için geldiklerini söylüyor
yo = Hey
raymond listen very closely = Raymond, çok dikkatli dinle
"""
    }
}

/** Argo / kısaltma yazımını standart İngilizceye çevirir ki yerleşik çeviri doğru anlasın. */
object SlangNormalizer {
    private val rules: List<Pair<Regex, String>> = listOf(
        "\\bI ain't\\b" to "I'm not",
        "\\b(you|we|they) ain't\\b" to "$1 aren't",
        "\\bain't\\b" to "isn't",
        "\\bgonna\\b" to "going to",
        "\\bwanna\\b" to "want to",
        "\\b(gotta|hafta)\\b" to "have to",
        "\\by'all\\b" to "you all",
        "\\blemme\\b" to "let me",
        "\\bgimme\\b" to "give me",
        "\\bI dunno\\b" to "I don't know",
        "\\bdunno\\b" to "don't know",
        "\\bkinda\\b" to "kind of",
        "\\bsorta\\b" to "sort of",
        "\\boutta\\b" to "out of",
        "\\b(coulda|woulda|shoulda)\\b" to "$1 have",
        "(?<![\\p{L}])'?(cause|cuz|coz)\\b" to "because",
        "(?<![\\p{L}])'em\\b" to "them",
        "\\bc'mon\\b" to "come on",
        "\\bgotcha\\b" to "got you",
        "\\bwhatcha\\b" to "what are you",
        "\\bima\\b" to "I'm going to",
        "\\btryna\\b" to "trying to",
        "\\bfinna\\b" to "about to",
        "\\bya\\b" to "you",
        "\\byou feel me\\b" to "you understand me",
        "\\baight\\b" to "alright",
        "\\byo\\b" to "hey",
        "\\bhold up\\b" to "wait",
        "\\bnah\\b" to "no",
        "(?<![\\p{L}])'bout\\b" to "about",
        "(?<![\\p{L}])'til\\b" to "until",
        "\\b(\\p{L}{2,})in'(?![\\p{L}])" to "$1ing"
    ).map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }

    fun normalize(text: String): String {
        var t = text
        for ((re, rep) in rules) {
            t = re.replace(t) { m ->
                var r = rep
                for (g in 1..m.groupValues.lastIndex) r = r.replace("$" + g, m.groupValues[g])
                if (m.value.first().isUpperCase() && r.isNotEmpty()) r.substring(0, 1).uppercase() + r.substring(1) else r
            }
        }
        return t
    }
}

/**
 * Küfürlü cümleler için: yerleşik çeviri küfürü yumuşatır, bozar ya da İngilizce bırakır.
 * Bu yüzden önce kelimeyi geçici bir "isim" ile (Zqa, Zqb...) değiştirir, çeviri sonrası Türkçe karşılığını yerine koyar.
 * Geçici isim çeviride kaybolursa cümle yedek yolla çevrilir (küfür sonradan Türkçeleştirilir).
 */
object ProfanityShield {
    class Slot(val token: String, val english: String, val turkish: String)
    class Shielded(val text: String, val slots: List<Slot>)

    // "pekiştirici" küfürler: çıkarılır, cümlenin anlamı bozulmaz.
    private val intensifiers = Regex("(?i)\\b(motherfucking|fucking|fuckin'?|goddamned|goddamn|freaking|effing)\\s+")
    private val whThe = Regex("(?i)\\b(what|who|where|why|how|when)\\s+the\\s+(fuck|fucking hell)\\b")
    private val theFuck = Regex("(?i)\\s+the\\s+fuck\\b")

    private val words: List<Pair<Regex, String>> = listOf(
        "son of a bitch(?:es)?" to "orospu çocuğu",
        "motherfuckers?" to "orospu çocuğu",
        "fuck (?:off|you)" to "siktir git",
        "bullshit" to "saçmalık",
        "horseshit" to "saçmalık",
        "shit(?:s|ty)?" to "bok",
        "crap" to "zırva",
        "assholes?" to "pislik herif",
        "dickheads?" to "göt herif",
        "bitch(?:es)?" to "kaltak",
        "bastards?" to "piç",
        "pussy" to "korkak",
        "fuck(?:ed|s)?" to "siktir"
    ).map { Regex("(?i)\\b${it.first}\\b") to it.second }

    private val names = "abcdefghijklmnopqrstuvwxyz".map { "Zq$it" }

    fun shield(text: String): Shielded {
        var t = text
        t = whThe.replace(t) { "${it.groupValues[1]} the hell" }
        t = intensifiers.replace(t, "")
        t = theFuck.replace(t, "")
        val slots = ArrayList<Slot>()
        for ((re, tr) in words) {
            t = re.replace(t) { m ->
                if (slots.size >= names.size) m.value
                else {
                    val token = names[slots.size]
                    slots += Slot(token, m.value, tr)
                    token
                }
            }
        }
        return Shielded(t, slots)
    }

    /** Geçici isimleri Türkçe karşılığıyla değiştirir; biri kayıpsa null (yedek yol gerekir). */
    fun restore(translated: String, slots: List<Slot>, tr: Locale): String? {
        var t = translated
        for (s in slots) {
            val re = Regex("(?i)\\b${s.token}(?:['’][\\p{L}]+)?")
            if (!re.containsMatchIn(t)) return null
            t = re.replace(t) { m ->
                val atStart = m.range.first == 0 || t.substring(0, m.range.first).trimEnd().let { it.isEmpty() || it.last() in ".!?…" }
                if (atStart) s.turkish.substring(0, 1).uppercase(tr) + s.turkish.substring(1) else s.turkish
            }
        }
        return t
    }

    /** Yedek yol: geçici isimleri İngilizce kelimeye geri koyar. */
    fun unshield(text: String, slots: List<Slot>): String {
        var t = text
        for (s in slots) t = t.replace(s.token, s.english)
        return t
    }
}

class EnhancedOfflineTranslationEngine(
    private val base: OfflineTranslationEngine,
    private val glossary: SubtitleGlossary = SubtitleGlossary.default(),
    private val cacheSize: Int = 300
) : OfflineTranslationEngine {

    private val tr = Locale.forLanguageTag("tr-TR")
    private val sentenceSplit = Regex("(?<=[.!?…])\\s+")
    private val trailingPunct = Regex("[.!?…]+$")
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > cacheSize
    }

    // Yerleşik çeviri bazen küfürü çevirmeden bırakır; yerinde Türkçeleştir.
    private val leftovers = listOf(
        Regex("(?i)\\bmotherfucking\\b") to "siktiğimin",
        Regex("(?i)\\bmotherfucker\\b") to "orospu çocuğu",
        Regex("(?i)\\bfuck(?:ing|in'?)\\b") to "siktiğimin",
        Regex("(?i)\\bfuck\\b") to "siktir",
        Regex("(?i)\\bbullshit\\b") to "saçmalık",
        Regex("(?i)\\bshit\\b") to "bok",
        Regex("(?i)\\basshole\\b") to "pislik herif",
        Regex("(?i)\\bbastard\\b") to "piç",
        Regex("(?i)\\bgoddamn(?:ed)?\\b") to "lanet"
    )

    override suspend fun translate(normalizedEnglish: String): String {
        val cleaned = OcrTextCleaner.clean(normalizedEnglish)
        if (cleaned.isBlank()) return ""
        val sentences = cleaned.split(sentenceSplit).filter { it.isNotBlank() }
        if (sentences.size == 1) return cached(sentences[0])
        // Uzun altyazı: cümleler aynı anda çevrilir, sıra korunur.
        return coroutineScope { sentences.map { async { cached(it) } }.awaitAll() }.joinToString(" ")
    }

    private suspend fun cached(sentence: String): String {
        synchronized(cache) { cache[sentence] }?.let { return it }
        val r = translateSentence(sentence)
        if (r.isNotBlank()) synchronized(cache) { cache[sentence] = r }
        return r
    }

    private suspend fun translateSentence(sentence: String): String {
        val punct = trailingPunct.find(sentence)?.value ?: ""
        val core = sentence.removeSuffix(punct).trim()
        if (core.isEmpty()) return sentence

        glossary.lookup(core)?.let { return it + punct }

        val parts = core.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            val resolved = parts.map { glossary.lookup(it) }
            val anyKnown = resolved.any { it != null } || glossary.isName(parts[0])
            if (anyKnown) return joinParts(parts, resolved, punct)
        }
        return mlTranslate(core + punct)
    }

    /** argo düzelt -> küfürü koru -> yerleşik çeviri -> küfürü Türkçe koy. */
    private suspend fun mlTranslate(text: String): String {
        val norm = SlangNormalizer.normalize(text)
        val sh = ProfanityShield.shield(norm)
        if (sh.slots.isEmpty()) return fix(base.translate(sh.text))
        val restored = ProfanityShield.restore(base.translate(sh.text), sh.slots, tr)
        if (restored != null) return fix(restored)
        return fix(base.translate(ProfanityShield.unshield(sh.text, sh.slots)))
    }

    private suspend fun joinParts(parts: List<String>, resolved: List<String?>, punct: String): String {
        val out = ArrayList<String>()
        var i = 0
        while (i < parts.size) {
            val known = resolved[i] ?: if (glossary.isName(parts[i])) parts[i] else null
            if (known != null) {
                val isGlossary = resolved[i] != null
                var text = if (i > 0 && isGlossary && !glossary.startsWithName(known)) lowerFirst(known) else known
                if (i == parts.lastIndex) text += punct
                out += text
                i++
            } else {
                // Bitişik bilinmeyen parçaları BİRLİKTE çevir (bağlam kopmasın).
                var j = i
                while (j < parts.size && resolved[j] == null && !glossary.isName(parts[j])) j++
                val run = parts.subList(i, j).joinToString(", ")
                val endsHere = j == parts.size
                out += mlTranslate(run + if (endsHere) punct else "").trimEnd(',')
                i = j
            }
        }
        return out.joinToString(", ")
    }

    private fun lowerFirst(s: String): String =
        if (s.isEmpty()) s else s.substring(0, 1).lowercase(tr) + s.substring(1)

    private fun fix(translated: String): String {
        var t = translated
        for ((re, rep) in leftovers) {
            t = re.replace(t) { m ->
                if (m.value.first().isUpperCase()) rep.substring(0, 1).uppercase(tr) + rep.substring(1) else rep
            }
        }
        return t
    }
}

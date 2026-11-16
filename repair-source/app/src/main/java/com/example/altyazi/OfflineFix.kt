package com.example.altyazi

import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * ÇEVRİM DIŞI altyazı kalitesi için düzeltme katmanı.
 *
 * Telefonun yerleşik çevirisini (ML Kit, FastTranslate) şu sırayla sarar:
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
        t = t.replace(Regex("\\b(of|in|is|to|for|on|at|as|was|be|down|up|out|with|from|like|into|about)a\\b"), "$1 a")   // "ofa" -> "of a"
        t = t.replace(Regex("\\b([Ww])el\\b(?=[.!?,]|\\s|$)"), "$1ell")   // "done wel." -> "done well."
        t = t.replace('ơ', 'o').replace('Ơ', 'O').replace('ư', 'u').replace('Ư', 'U')
        t = loneL.replace(t, "I")
        t = loneOneBeforeApostrophe.replace(t, "I")
        t = ivePattern.replace(t) { "I've ${it.groupValues[1]} ${it.groupValues[2]}" }
        t = t.replace(Regex("^\\S*\\p{L}\\S*[&#@%*]\\S*\\s+(?=\\p{Lu})"), "") // baştaki "PALN&S" gibi kırıntı
        t = t.replace(Regex("(?<=\\b\\p{Ll}{2,20})I(?=\\s)"), " I")   // "knowI got" -> "know I got"
        t = t.replace(Regex("^[|l]\\s*(?=I\\b)"), "")           // "|I already" -> "I already"
        // "Talready" -> "I already", "Thope" -> "I hope"
        // 9.64 kaydinda "Thope to never see either of you again." satiri
        // "Yine seni asla görmemek için." diye cikti: liste cok kisaydi.
        t = t.replace(
            Regex(
                "^[T|l](?=(already|always|never|just|am|was|hope|think|thought|told|" +
                    "need|want|know|knew|guess|mean|meant|swear|promise|wish|love|hate|" +
                    "feel|felt|saw|see|said|say|have|had|did|do|don't|can't|won't|will|" +
                    "would|could|should|understand|remember|forgot|believe|heard|hear|" +
                    "tried|try|made|make|found|find|lost|lose|got|get|went|go|gotta)\\b)"
            ),
            "I "
        )
        t = t.replace(Regex("\\bthe y(?=['’]?(re|ll|ve|d)\\b)", RegexOption.IGNORE_CASE), "they") // "the y're" -> "they're"
        t = t.replace(Regex("\\bthe y\\b", RegexOption.IGNORE_CASE), "they")
        // "Ijust" -> "I just", "am ll going" -> "am I going", "1 don't" -> "I don't", "mOve" -> "move", "sthit" -> "shit"
        t = t.replace(Regex("\\bI(just|was|am|know|think|have|had|did|didn't|don't|can't|will|never|always|need|want|got|mean|guess|told|said|thought|miss|missed|love|loved|like|hate|hope|feel|felt|wish|see|saw|swear|promise|believe|remember|forgot|understand|should|could|would|really|still|also|only|get|go|went|made|found|lost|heard|tried|knew)\\b"), "I $1")
        t = t.replace(Regex("\\b(am|do|did|can|could|should|would|will|was|have|had|shall)\\s+(?:ll|l1|1l|\\|l)\\b"), "$1 I")
        t = t.replace(Regex("(^|[.!?…]\\s+)1\\s+(?=(?:don't|didn't|can't|won't|am|was|have|had|know|think|will|just|need|want|got)\\b)"), "$1I ")
        t = t.replace(Regex("(?<=\\p{Ll})O(?=\\p{Ll})"), "o")
        t = t.replace(Regex("\\bsthit\\b"), "shit")
        // OCR bilesik kelimeyi boluyor ve ikinci yari ayri sozcuk gibi
        // cevriliyor: "our friend ship" -> "arkadas GEMIMIZ".
        t = t.replace(Regex("\\bfriend ship\\b", RegexOption.IGNORE_CASE), "friendship")
        // BUNLARI BİLEREK EKLEMİYORUZ: iki kelimeli hâlleri gerçek ve sık.
        //   "no body"    -> "No body was ever found."   (ceset!)
        //   "any body"   -> "Did you find any body?"
        //   "some thing" -> "a strange some thing" nadir ama meşru
        // Bu satırları bugün ekleyip kendi gözden geçirmemde kaldırdım:
        // "No body was ever found." -> "Nobody was ever found." anlamı
        // tersine çeviriyordu. Yalnız iki kelimeli hâli neredeyse hiç
        // geçmeyenler kalıyor.
        t = t.replace(Regex("\\bhim self\\b", RegexOption.IGNORE_CASE), "himself")
        t = t.replace(Regex("\\bmy self\\b", RegexOption.IGNORE_CASE), "myself")
        t = t.replace(Regex("\\byour self\\b", RegexOption.IGNORE_CASE), "yourself")
        // "in bred" yalnız sıfat konumunda ("in bred and deficient"):
        // "raised in bred captivity" gibi meşru kullanımı bozmayalım.
        t = t.replace(Regex("\\bin bred and\\b", RegexOption.IGNORE_CASE), "inbred and")
        t = t.replace(Regex("^Well (talk|see|go|be|get|need|have|find|make|do|meet|figure)\\b"), "We'll $1")
        // Sık OCR harf hataları: "themn" -> "them", "ternd" -> "tend", "veah" -> "yeah"
        t = t.replace(Regex("\\b(the|hi|who|fro|see|roo|tea)mn\\b"), "$1m")
        t = t.replace(Regex("\\bternd(s|ed|ing)?\\b"), "tend$1")
        t = t.replace(Regex("\\b([Vv])eah\\b")) { if (it.groupValues[1] == "V") "Yeah" else "yeah" }
        // "Il investigate" -> "I'll investigate" (kesme işareti kaybolmuş)
        t = t.replace(Regex("^[I|l]l (?=[a-z])"), "I'll ")
        // "II see you soon." / "I|l see you soon." -> "I'll see you soon."
        // 9.63 kaydında satır "II yakında görüşürüz." diye ekrana geldi.
        // İki/üç karakterlik I-l-| yığını, ardından gerçek bir fiil gelirse
        // "I'll" demektir. Fiil listesi şart: "II" roma rakamı da olabiliyor.
        t = t.replace(
            Regex(
                "(^|[.!?…]\\s+)[Il|]{2,3}\\s+(?=(see|be|get|go|tell|call|take|make|do|have|" +
                    "let|give|show|try|find|keep|put|send|meet|check|talk|wait|come|bring|" +
                    "need|want|say|know|think|look|stay|start|stop|help|handle|deal|catch|" +
                    "miss|remember|forget|explain|fix|kill|wait|watch|drive|leave)\\b)"
            )
        ) { it.groupValues[1] + "I'll " }
        // "lassume" -> "I assume", "lI stop" -> "I stop", "I'Il" -> "I'll"
        t = t.replace(Regex("(^|\\s)[l|](assume|know|think|guess|mean|was|am|have|had|did|do|don't|can|can't|will|would|could|never|always|just|need|want|thought|told|said|got|see|hope|believe|swear|promise|wish|love|hate)\\b"), "$1I $2")
        t = t.replace(Regex("\\b[l|]I\\b"), "I")
        t = t.replace(Regex("['’]Il\\b"), "'ll")
        // Prime X-Ray oyuncu yazısı altyazıya yapışmış: "SungWon Cho - Dr. PetersonAnd we..." -> "And we..."
        t = t.replace(Regex("^\\p{Lu}\\p{L}+(?: \\p{Lu}[\\p{L}.]*){0,2} [-–] (?:(?:Dr|Mr|Mrs|Ms)\\. ?)?\\p{Lu}\\p{Ll}+(?:\\s+|(?=\\p{Lu}\\p{Ll}))(?=\\p{Lu})"), "")
        // "they'I kill" -> "they'll kill" (ll -> I okunmuş)
        t = t.replace(Regex("(?<=\\b(?:they|you|we|he|she|it|I|that|there|who))['’][Il1]\\b"), "'ll")
        // Tek başına "T" çoğu zaman "I": "Until T figure out" -> "Until I figure out"
        t = t.replace(Regex("(^|(?<=\\p{Ll}) )T (?=(figure|figured|know|knew|think|thought|got|gotta|need|want|guess|mean|swear|can|can't|don't|didn't|was|am|will|just|have|had|said|told|see|saw|really)\\b)"), "$1I ")
        // Kopuk kesme işaretleri: "they re" -> "they're", "don t" -> "don't", "it s" -> "it's", "I m" -> "I'm"
        t = t.replace(Regex("\\b(they|you|we) re\\b", RegexOption.IGNORE_CASE), "$1're")
        t = t.replace(Regex("\\b(don|can|won|isn|aren|didn|doesn|wasn|weren|couldn|shouldn|wouldn|ain) t\\b", RegexOption.IGNORE_CASE), "$1't")
        t = t.replace(Regex("\\b(it|that|what|he|she|there|here|who|where|let) s\\b", RegexOption.IGNORE_CASE), "$1's")
        t = t.replace(Regex("\\bI m\\b"), "I'm")
        // Başta ekran kodu kırıntısı: "2933B8 Let me tell..." -> "Let me tell..."
        t = t.replace(Regex("^(?=[0-9A-F]*\\d)[0-9A-Z]{5,}\\s+(?=\\p{Lu})"), "")
        // Konuşmacı etiketinden sonra kalan kırıntı: "May: ) Never forget" -> "May: Never forget"
        t = t.replace(Regex("^(\\p{Lu}[\\p{L}'.]*(?: \\p{Lu}[\\p{L}'.]*)?:)\\s*[)\\]|}]+\\s*"), "$1 ")
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
quiet = Sessiz ol
be quiet = Sessiz ol
hardly = Pek sayılmaz
godspeed = Yolun açık olsun
mmm hmm = Hı hı
mm hmm = Hı hı
mhm = Hı hı
fo sho = Kesin
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
what the fuck = Bu ne lan
yo what the fuck = Bu ne lan
what the fuck is going on = Ne oluyor lan
what the fuck is going on here = Burada ne oluyor lan
what the fuck are you doing = Ne yapıyorsun lan
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
where the fuck you at = Neredesin lan
where you at = Neredesin
pack that shit up = Toplayın şu boku
ay = Hey
pack it up = Toplayın
yo phone = Hey, telefon
appreciate it = Sağ ol
i don't give a fuck = Sikimde bile değil
this stinks = Bu işte bir bit yeniği var
somebody squealed = Biri ötmüş
someone squealed = Biri ötmüş
who squealed = Kim öttü
not anymore = Artık değil
take that = Al bakalım
take that ya damned screws = Al bakalım, lanet gardiyanlar
sure buddy sure = Tabii dostum, tabii
and here's eddie now = İşte Eddie de geldi
here he is = İşte geldi
here we go = İşte başlıyoruz
suit yourself = Sen bilirsin
drift = Defol
i'm just saying = Sadece söylüyorum
im just saying = Sadece söylüyorum
just saying = Sadece söylüyorum
well don't = O zaman yapma
you know what = Bak ne diyeceğim
makes sense = Mantıklı
make sense = Mantıklı
trying to hold on = Tutunmaya çalışıyorum
who i am = Kim olduğum
who i was = Kim olduğum
every fight = Her kavga
every death = Her ölüm
not actual gameplay = Gerçek oyun görüntüsü değildir
here we go again = Yine başlıyoruz
finally = Nihayet
what's wrong = Ne oldu
i get it = Anlıyorum
i get that = Anlıyorum
i get that too = Bunu da anlıyorum
worth the wait = Beklemeye değdi
it was worth the wait = Beklemeye değdi
maybe i did get lucky = Belki de şansım yaver gitti
get lucky = Şansın yaver gitsin
it is what it is = Ne yapalım, durum bu
it's what it is = Ne yapalım, durum bu
my friend = Dostum
my man = Dostum
it's twelve = Polis geldi
twelve = Polis
twelve's here = Polis geldi
we going up = Yukarı çıkıyoruz
we're going up = Yukarı çıkıyoruz
we goin up = Yukarı çıkıyoruz
right inside = Hemen içeride
straight up = Dosdoğru ilerle
first door on the right = Sağdaki ilk kapı
pick up = Alacağız
a pickup = mal alma
a drop off = teslim etme
we're just here to collect = Biz sadece almaya geldik
were just here to collect = Biz sadece almaya geldik
why he ain't come to collect = Neden kendisi almaya gelmedi
you ain't ask yourselves that = Kendinize bunu sormadınız mı
look we could go back empty handed = Bak, elimiz boş dönebiliriz
we could go back empty handed = Elimiz boş dönebiliriz
boobie sent us for a pickup = Boobie bizi mal almaya gönderdi
he only worried about himself = Sadece kendini düşünüyor
problem is he only worried about himself = Sorun şu ki sadece kendini düşünüyor
they tooled up and everything = Tepeden tırnağa silahlılar
they're tooled up = Silahlılar
you feel me = Anladın mı
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
easy = Sakin ol
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
get the fuck out = Siktir git
get the fuck out of here = Siktir git buradan
what the fuck was that = Bu da neydi lan
the fuck is that = O ne lan
the fuck = Ne lan
who the fuck is this = Kim lan bu
where the fuck you at = Neredesin lan
fuck boobie = Boobie'yi siktir et
we gotta get out of this = Bu işten sıyrılmamız lazım
we got to get out of this = Bu işten sıyrılmamız lazım
oyeme = Dinle beni
oyeme mi hermano = Dinle beni kardeşim
mi hermano = kardeşim
mi amigo = dostum
we got a situation happening = Bir durum var
we got a situation = Bir durum var
what the hell was that = O da neydi böyle
are you fucking kidding me = Benimle dalga mı geçiyorsun lan
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
well = Şey
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
# --- 9.74 kalıplar (11 Kasım kaydından)
first things first = Önce en önemlisi
thank you, everyone = Herkese teşekkürler
thank you everyone = Herkese teşekkürler
thanks, everyone = Herkese teşekkürler
hey, guys = Selam millet
hey guys = Selam millet
hi, guys = Selam millet
great job = Harika iş
great job, guys = Harika iş çıkardınız
nice job = İyi iş
well done = Aferin
once upon a time = Bir zamanlar
no one will suspect a thing = Kimse bir şey fark etmeyecek
nobody will suspect a thing = Kimse bir şey fark etmeyecek
we have a lot to talk about = Konuşacak çok şeyimiz var
almost had me fooled = Neredeyse kandırıyordun
and at your service = Ve hizmetinizdeyiz
at your service = Hizmetinizdeyiz
trailer = Fragman
gameplay = Oynanış
guys = Millet
everyone = Herkes
surprise = Sürpriz

# --- 9.84 ünlemler ("hızlı çeviri başarısız" olan satırlar)
uh-oh = Eyvah
uh oh = Eyvah
oh = Ah
ow = Ah
ouch = Ah

# --- 9.85 (12 Kasım ikinci kayıt)
ugh = Öf
great = Harika
grab it = Kap şunu
you grab it = Sen kap
you don't say = Yok artık
he'll join = Katılacak
what's next = Sırada ne var

# --- 9.86 (12 Kasım üçüncü kayıt)
do not make me ask you twice = Bana iki kere sordurma
don't make me ask you twice = Bana iki kere sordurma
don't make me ask twice = Bana iki kere sordurma
billions upon billions = Milyarlarca
bilions upon billions = Milyarlarca

# --- İsimler (çevrilmez)
@Lu
@Boobie
@Méndez
@Ray
@Raymond
@cabrón

# --- Oyuna özel (sizin kayıttan)
it's him = İşte o
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
yeah he fucking knows = Evet, biliyor amına koyayım
what the fuck you talking about = Ne diyorsun lan sen
what the fuck are you talking about = Ne diyorsun lan sen
my partner's off the grid right now you feel me = Ortağım şu an ortalarda yok, anlıyorsun ya
problem is he only worried about himself = Sorun şu ki sadece kendini düşünüyor
he seriously sent y'all over here right now = Sizi gerçekten şimdi buraya mı gönderdi
let's get these workers out of here man = Bu işçileri buradan çıkaralım dostum
i already told that motherfucker what the fuck is going on man = O orospu çocuğuna ne halt döndüğünü zaten söyledim dostum
i already told that motherfucker what the fuck going on man = O orospu çocuğuna ne halt döndüğünü zaten söyledim dostum
these two say they're for boobie = Bu ikisi Boobie için geldiklerini söylüyor
yo = Hey
listen very closely = Çok dikkatli dinle
listen carefully = Dikkatle dinle

# --- 11 Kasım 9.61 kaydı: çeviri motoruna düşüp bozulan satırlar
# "the block" = uyuşturucu satılan sokak/blok. "Mahalle" fazla yumuşak ve
# kullanıcı orijinalliğin korunmasını istiyor: "blok" sokak ağzında da var.
that's the block right here = İşte o blok burası
alright that's the block right here = Tamam, işte o blok burası
this is the block = İşte blok burası
hold up = Bekle
nah = Yok
man = Dostum
crackheads = Keşler
crackhead = Keş
on it = Hallediyorum
i'm on it = Hallediyorum
woo = Woo
mmm hmm = Hı hı
# "it's him" hem tek başına hem ad ile birlikte geçiyor; adsız hâli
# "o o" diye çıkıyordu (çeviri motoru "it's"i de "him"i de "o" yapıyor).
it's him = İşte o
that's him = İşte o
it's her = İşte o
yall need something = Bir şey mi lazım
y'all need something = Bir şey mi lazım
you need something = Bir şey mi lazım
y'all know boobie = Boobie'yi tanıyor musunuz
you know boobie = Boobie'yi tanıyor musunuz
y'all tryna smoke = Mal mı arıyorsunuz
you tryna smoke = Mal mı arıyorsunuz
let me tell y'all something = Size bir şey söyleyeyim
let me tell you something = Size bir şey söyleyeyim
i guess they know we're coming = Sanırım geleceğimizi biliyorlar
be cool = Sakin ol
don't you know = Yapma, bilirsin
what's up = Naber
you know anything about that = Bu konuda bir şey biliyor musun
we're picking something up for him = Onun için bir şey alacağız
well we're picking something up for him = Şey, onun için bir şey alacağız
# Tek cümle iki satıra bölündüğü için ikisi ayrı ayrı yazılı.
until i figure out where the fuck he at and what the fuck going on = Onun nerede olduğunu ve ne halt döndüğünü anlayana kadar
until i figure out where the fuck he is and what the fuck is going on = Onun nerede olduğunu ve ne halt döndüğünü anlayana kadar
we in a holding pattern right here = Burada bekliyoruz
this has gotta be the door = Bu kapı olmalı
this has got to be the door = Bu kapı olmalı
looks like it = Öyle görünüyor
what you want = Ne istiyorsun
whatchu want = Ne istiyorsun
how you doing = Nasılsın
big guy = Koca adam
never heard of him = Hiç duymadım onu
nah never heard of him = Yok, hiç duymadım onu
put it on speakerphone = Hoparlöre al
our friends on the task force are on their way = Görev gücündeki arkadaşlarımız yolda
they say they're paying you a visit = Sana uğrayacaklarını söylüyorlar
just let them take whatever they want = Ne isterlerse almalarına izin ver
we need to find a way out = Bir çıkış yolu bulmamız lazım
come on we need to find a way out = Hadi, bir çıkış yolu bulmamız lazım
we're here to pick up = Malı almaya geldik
wrong way = Yanlış taraf
i always been there for you = Hep yanında oldum
err nothing = Hımm, bir şey yok
eh = Eh
aight = Tamam
huh = Ha
don't = Yapma
you know = Anlarsın ya
be cool = Sakin ol
he ain't here though = O burada değil ama
he isn't here though = O burada değil ama
he ain't here = O burada değil
he isn't here = O burada değil
ah = Ah
# Virgülle bölününce sona düşen "though" tek başına "ama" olup
# "o burada değil, ama." gibi bozuk cümle veriyordu; cümlenin tamamı yazılı.
yeah he ain't here though = Evet, o burada değil ama
yeah he isn't here though = Evet, o burada değil ama
wrong way this way = O taraf değil, bu taraftan
go inside and ask for raymond = İçeri gir, Raymond'u sor
go inside and ask to see raymond = İçeri gir, Raymond'u sor
yo ray = Hey Ray
for boobie = Boobie için
is raymond around = Raymond var mı
is raymond here = Raymond var mı
you said you was here for a pickup or a drop off = Malı almaya mı geldin, teslim etmeye mi
you said you were here for a pickup or a drop off = Malı almaya mı geldin, teslim etmeye mi
this has gotta be the door right = Bu kapı olmalı, değil mi
this has got to be the door right = Bu kapı olmalı, değil mi
they say they're paying you a visit so just let them take whatever they want okay = Sana uğrayacaklarını söylüyorlar, ne isterlerse almalarına izin ver, tamam mı
they say they're visiting you so just let them take whatever they want okay = Sana uğrayacaklarını söylüyorlar, ne isterlerse almalarına izin ver, tamam mı
like might have to fuck around and shake some shit know i'm saying = Yani, ortalığı karıştırmak zorunda kalabilirim, anladın mı
i mean might have to fuck around and shake some shit know i'm saying = Yani, ortalığı karıştırmak zorunda kalabilirim, anladın mı
let me tell y'all something at this point i really don't give a fuck = Size bir şey söyleyeyim, bu noktada hiç umurumda değil
let me tell you something at this point i really don't care = Size bir şey söyleyeyim, bu noktada hiç umurumda değil
i don't even wanna hear that shit = O boktan şeyi duymak bile istemiyorum
i don't even want to hear that shit = O boktan şeyi duymak bile istemiyorum

# --- 9.66 kaydindan genel deyimler
just chill = Sakin ol biraz
just chill here for a second = Bir saniye sakin ol
chill here for a second = Bir saniye sakin ol
chill = Sakin ol
chill out = Sakin ol
it was nuts = Çılgıncaydı
that was nuts = Çılgıncaydı
the catch-up will have to wait = Hasbihal beklesin
catch-up = Hasbihal
oh cono = Ah, kahretsin
cono = Kahretsin
oh coño = Ah, kahretsin
coño = Kahretsin
oye = Baksana
yeme mi hermano = Baksana kardeşim
this sucker = Bu şey
park this sucker = Bu şeyi park et
i'm used to it = Alıştım artık
stay down = Yerde kal
you home = Evde misin
the thought crossed my mind = Aklımdan geçti
that cannot be true = Bu olamaz
it's true = Doğru
we can beat them = Onları yenebiliriz
this is exceptional news = Bu olağanüstü bir haber
i'm as surprised as you are = Ben de senin kadar şaşkınım
it is good to see you again = Seni tekrar görmek güzel
never even thought to ask = Sormak hiç aklıma gelmedi

# --- 9.64 kaydı: taslak motorunun bozduğu GENEL karşılıklar
# Unvanlar talimatta yapay zekâ için yazılı ama TASLAK onları bilmiyordu:
#   "No, General." -> "Hayır, Genel."
general = General
colonel = Albay
major = Binbaşı
lieutenant = Teğmen
corporal = Onbaşı
private = Er
admiral = Amiral
marshal = Mareşal
chief = Şef
warden = Müdür
yes general = Emredersiniz, General
no general = Hayır, General
yes sir = Emredersiniz
no sir = Hayır efendim
yes ma'am = Emredersiniz
# "order" ticari sipariş de olabiliyor; askerî/toplumsal bağlamda "düzen".
#   "You will bring order." -> "Sipariş getireceksin."
you will bring order = Düzen getireceksin
bring order = Düzen getirmek
restore order = Düzeni sağlamak
keep order = Düzeni korumak
law and order = Kanun ve düzen
you will bring strength = Güç getireceksin
# "ranks" rütbe değil saf; "report to" bildirmek değil başvurmak.
from our ranks = Saflarımızdan
in our ranks = Saflarımızda
close ranks = Safları sıkılaştırmak
report to the barracks = Kışlaya git
report for duty = Göreve başvur
# Tekil/çoğul ve anlam kaymaları
act like one = Ona göre davran
clean yourself = Temizlen
your education is over for today = Bugünün eğitimi bitti
think well before you answer = Cevap vermeden önce iyi düşün
if you survive = Hayatta kalırsan
it is your day of adulthood = Bugün yetişkinliğe adım atma günün
make us stronger = Bizi güçlendirmek
it will only make us stronger = Bu bizi daha da güçlendirir
all is ours = Hepsi bizim
what else = Başka
by who = Kim tarafından
by whom = Kim tarafından
he was killed = Öldürüldü
correct = Doğru
enough = Yeter
good = İyi

# --- 9.63 kaydından GENEL deyimler (filme özel değil, taslak hepsini bozdu)
i beg to differ = Katılmıyorum
beg to differ = Katılmıyorum
get that = Aç şunu
i'll get that = Ben açarım
that broad = O karı
this broad = Bu karı
word from my boys is you're dead = Çocuklarımdan haber geldi, ölmüşsün
word on the street = Sokakta konuşulan
word is = Söylenene göre
have in common = Ortak noktası
what do these three men have in common = Bu üç adamın ortak noktası ne
late again = Yine geç kaldı
geez = Vay canına
considering what i pay = Ödediğim paraya bakarsan
you'd think = İnsan sanır ki
i need to know = Bilmem gerek
alive and well = Sağ ve salim
you are here = Buradasın
see you soon = Yakında görüşürüz
i'll see you soon = Yakında görüşürüz
that's part of the tradition = Bu da geleneğin bir parçası
and that's part of the tradition = Ve bu da geleneğin bir parçası
early christmas present = Erken Noel hediyesi
in the fridge = Buzdolabında
raising his voice = Sesini yükselterek
much louder = Çok daha yüksek
coming up the stairs = Merdivenlerden yukarı geliyor
straight towards = Dosdoğru üzerine
heavy door = Ağır kapı
who are you = Sen kimsin
who were you then = O zaman kimdin
what do you want from me = Benden ne istiyorsun
looks like our zombie's been spotted again = Zombimiz yine görülmüş gibi
near wycliffe center = Wycliffe Center yakınında
on their way = Yolda
i beg your pardon = Efendim
beats me = Bilmem
no idea = Hiçbir fikrim yok
fair enough = Olur, tamam
makes sense = Mantıklı
figures = Tahmin etmeliydim
your call = Sen karar ver
not a chance = İmkânı yok
over my dead body = Cesedimi çiğnemen gerekir
cut it out = Kes şunu
knock it off = Kes artık
give it a rest = Bırak şu işi
spit it out = Çıkar ağzından
out with it = Söyle hadi
hold your horses = Dur bakalım
easy does it = Yavaş yavaş
my hands are tied = Elim kolum bağlı
it's on me = Benden
on the house = İkram bizden

# ======================================================================
# GENEL SOKAK/AKSİYON DİYALOĞU — oyuna özel DEĞİL.
#
# Buradaki her satırın tek anlamı var ve hangi oyunda geçerse aynı şeyi
# söylüyor. Sözlük TAM CÜMLE (ya da virgülle ayrılmış parça) eşleştirdiği
# için bunlar cümle içinde yanlışlıkla basmıyor; yalnız satırın kendisi
# buysa devreye giriyor.
#
# Bağlama göre anlamı değişenler buraya YAZILMAZ, Slang.hints'e ipucu
# olarak yazılır: ipucu çeviriyi zorlamaz, yapay zekâya anlamı söyler.
# ======================================================================

# --- onay, anlaşma
know i'm saying = Anladın mı
know what i'm saying = Anladın mı
you know what i'm saying = Anladın mı
copy that = Anlaşıldı
roger that = Anlaşıldı
understood = Anlaşıldı
my bad = Benim hatam
no problem = Sorun değil
no worries = Sorun değil
it's all good = Her şey yolunda
i'm good = İyiyim
we good = İyiyiz
you good = İyi misin
hell no = Hayatta olmaz
hell yeah = İşte bu
for real = Gerçekten mi
are you serious = Ciddi misin
you gotta be kidding me = Şaka yapıyor olmalısın
not again = Yine mi

# --- çatışma
get down = Yere yat
watch out = Dikkat et
heads up = Dikkat
behind you = Arkanda
on your left = Solunda
on your right = Sağında
i'm hit = Vuruldum
reloading = Şarjör değiştiriyorum
hands up = Eller yukarı
don't move = Kıpırdama
drop it = Bırak onu
drop the gun = Silahı bırak
move it = Kımılda
stay with me = Benimle kal
stay here = Burada kal
i'm in position = Yerimdeyim
stand by = Bekle
that was close = Ucu ucuna
this ain't even loaded = Bunda mermi bile yok
it ain't even loaded = Bunda mermi bile yok
a bunch of street pushers = Bir avuç sokak satıcısı

# --- hareket, kaçış
let's roll = Hadi gidelim
let's get out of here = Hadi çıkalım buradan
we gotta go = Gitmemiz lazım
follow me = Beni takip et
wait up = Bekle beni
back off = Geri çekil
almost there = Neredeyse vardık
over here = Buraya
over there = Şurada
right there = İşte orada
get in = Bin
get in the car = Arabaya bin
step on it = Bas gaza
floor it = Bas gaza
pull over = Kenara çek

# --- konuşma kalıpları
what's good = Naber
what's happening = Neler oluyor
take it easy = Sakin ol
calm down = Sakin ol
shut up = Kapa çeneni
give me a second = Bir saniye
hold on a second = Bir saniye bekle
let me think = Bir düşüneyim
i'll be right back = Hemen dönüyorum
what now = Şimdi ne olacak
long time no see = Görüşmeyeli çok oldu
nice work = İyi iş

# --- 9.61 kaydından genelleşen parçalar (ad içermeyen hâlleri)
shake some shit = Ortalığı karıştırmak
at this point = Bu noktada
i really don't give a fuck = Hiç umurumda değil
i don't give a shit = Umurumda değil
on their way = Yolda
they're on their way = Yolda geliyorlar
a pickup or a drop off = Mal almaya mı, teslim etmeye mi
let's get these workers out of here = Bu işçileri buradan çıkaralım
i already told that motherfucker what the fuck is going on = O orospu çocuğuna ne halt döndüğünü zaten söyledim
i already told that motherfucker what the fuck going on = O orospu çocuğuna ne halt döndüğünü zaten söyledim
my partner's off the grid right now = Ortağım şu an ortalarda yok

# OCR'ın ikinci "r" eklediği ad dahil: çevrilmeyip olduğu gibi kalsın.
@Ernesto
@Nesto
@Errnesto
@cabron
@Lu"""
    }
}

/** Argo / kısaltma yazımını standart İngilizceye çevirir ki yerleşik çeviri doğru anlasın. */
/**
 * Bozuk okunmuş satırı yakalar: "And we thankKMI. CorTIgan Yor", "vo.untteri?hg", "ontiou00 Touooon".
 * Böyle bir okuma çevrilmez; uygulama bir sonraki (temiz) okumayı bekler. Yarım/bozuk her okuma
 * çevrilince ekranda saçma taslaklar dönüyor, yapay zekâ sırası uzuyordu.
 */
object OcrQuality {
    private val izinliKarma = Regex("^(?:Mc|Mac|De|Di|La|Le|Van|Von|iP|eB|YouT|PlayS|McK)")
    private val tekrarHarf = Regex("([a-z])\\1\\1", RegexOption.IGNORE_CASE)
    private val sesTekrar = Regex("^(?:o+h+|a+h+|h+m+|z+|m+h*m+|u+h+|e+h+|w+o+a*h*|sh+|ah+a+)[.!?,]*$", RegexOption.IGNORE_CASE)

    fun kelimeBozukMu(w0: String): Boolean {
        val w = w0.trim('.', ',', '!', '?', '"', '\'', '…', '-', ')', '(', '[', ']', ':', ';')
        if (w.count { it.isLetter() } < 3) return false
        if (sesTekrar.matches(w)) return false
        // küçük harften sonra büyük harf ve tekrar küçük: "CorTIgan", "sCUrfs", "thankKMI"
        if (Regex("\\p{Ll}\\p{Lu}").containsMatchIn(w) && !izinliKarma.containsMatchIn(w) && !w.all { !it.isLetter() || it.isUpperCase() }) return true
        // harf arasında rakam: "ontiou00", "Iolo0t"
        if (Regex("\\p{L}\\d|\\d\\p{L}").containsMatchIn(w) && !Regex("^\\d+(st|nd|rd|th|s|am|pm|k|m|x)$", RegexOption.IGNORE_CASE).matches(w)) return true
        // harf arasında noktalama: "vo.untteri?hg", "yel!ng", "Fi!st"
        if (Regex("\\p{L}[.!?;,|]\\p{L}").containsMatchIn(w) && !Regex("^(?:\\p{L}\\.){2,}$").matches(w0.trim())) return true
        // aynı harf üç kez: "Touooon", "Ihoooing"
        if (tekrarHarf.containsMatchIn(w)) return true
        return false
    }

    fun bozukMu(text: String): Boolean {
        val kelimeler = text.split(Regex("\\s+")).filter { w -> w.count { it.isLetter() } >= 3 }
        if (kelimeler.isEmpty()) return false
        val bozuk = kelimeler.count { kelimeBozukMu(it) }
        return if (kelimeler.size == 1) bozuk == 1 else bozuk > 0 && bozuk * 4 >= kelimeler.size   // en az dörtte biri bozuksa
    }
}

object SlangNormalizer {
    private val rules: List<Pair<Regex, String>> = listOf(
        // Sokak dilinde düşen "is/are": "we in a holding pattern", "where he at", "this what I get"
        "\\bwhere (the fuck |the hell )?(he|she|it|you|we|they|y'all) at\\b" to "where $1$2 is",
        "\\bwhat the (fuck|hell) (going on|happening)\\b" to "what the $1 is $2",
        // (yardımcı fiilden sonra değilse: "are we going", "did they" bozulmasın; "you" nesne de olabildiği için sadece cümle başında)
        "(?<!(?:^|[.!?,\\-] ?)(?:(?:where|what|why|how|when|who|so|and|but) )?(?:are|is|am|were|was|do|does|did|will|would|can|could|should|have|has|had) )(?<!\\b(?:if|let|make|see|hear|watch) )\\b(we|they) (in|on|at|out|good|fine|done|ready|here|not|all set|gonna|going|trying|getting|coming|talking)\\b" to "$1 are $2",
        "(?<!(?:^|[.!?,\\-] ?)(?:(?:where|what|why|how|when|who|so|and|but) )?(?:are|is|am|were|was|do|does|did|will|would|can|could|should|have|has|had) )(?<!\\b(?:if|let|make|see|hear|watch) )\\b(he|she) (in|on|at|out|good|fine|done|ready|here|not|gonna|going|trying|getting|coming|talking|only worried|worried)\\b" to "$1 is $2",
        "^you (gonna|trying|talking|coming|going)\\b" to "you are $1",
        "^(but |so |and )?(this|that) (what|how|why|where)\\b" to "$1$2 is $3",
        "(?<![\\p{L}])'fore\\b" to "before",
        "(,\\s*)man(?=[.!?]|$)" to "$1buddy",
        "\\bain't (ask|tell|say|see|know|do|go|get|hear|call|give|take|come|bring|leave|pay|send|show|stop|try|want|need|think|work)\\b" to "didn't $1",
        "\\bI ain't\\b" to "I'm not",
        "\\b(you|we|they) ain't\\b" to "$1 aren't",
        "\\bain't\\b" to "isn't",
        "\\bby (himself|herself|myself|yourself|yourselves|themselves|ourselves)\\b" to "alone",
        // --- 9.74: motorun bilmedigi kalip/deyimler. Turkce karsiligi yoktur,
        // motorun BILDIGI Ingilizceye cevirip dogru Turkceyi ondan aliyoruz.
        // "Welcome aboard the Venture." -> "Girisime gidelim" oluyordu.
        "\\bwelcome aboard\\b(?= the| to)" to "welcome to",
        "\\bwelcome aboard\\b" to "welcome",
        // "stepping things up" -> "bir seyleri bastiriyoruz"
        "\\bstepping things up\\b" to "taking things to the next level",
        "\\bstep things up\\b" to "take things to the next level",
        // "couldn't pass it up" -> "geçemedik"
        "\\bpass (it|this|that) up\\b" to "miss this chance",
        "\\bpass up (the|this|that) (chance|opportunity)\\b" to "miss this chance",
        // "larger than life" -> "hayattan daha buyuk"
        "\\blarger than life\\b" to "legendary",
        // "our goals are completely aligned" -> "hedeflerimiz hizalanir"
        "\\b(are|is) (completely |fully |totally |perfectly )?aligned\\b" to "$1 $2the same",
        // "Captured on PS5" -> "PS5'de yakalandi"
        "\\b(captured|recorded) on (PS5|PS4|PC|Xbox|Switch)\\b" to "recorded on $2",
        "\\bfootage captured on\\b" to "footage recorded on",
        // "there was an ogre" -> "bir ogre vardi"
        // "an ogre" -> "an giant" olmasin: artikel de duzeliyor. Buyuk/kucuk
        // harf yakalama grubundan geliyor, aynanin davranisina bagli degil.
        "\\b([Aa])n ogre\\b" to "$1 giant",
        // --- 9.77: 11 Kasım Batman kaydındaki deyimler
        "\\bno thin\\b" to "nothing",
        "\\bnothin\\b" to "nothing",
        // "will blow over soon" -> "yakında patlayacak" (TAM TERSİ)
        "\\bblow over\\b" to "pass",
        "\\bblows over\\b" to "passes",
        // "light a fire under X" -> "X'in altında ateş yakmak"
        "\\blight a fire under\\b" to "put pressure on",
        // "I'm beat" -> "Ben dövüldüm"
        "\\bI['’]m beat\\b" to "I am exhausted",
        "\\bI am beat\\b" to "I am exhausted",
        // "alma mater" -> "Alma Mater"
        "\\balma mater\\b" to "old university",
        // "Sorry to break in" -> "Kırdığım için üzgünüm"
        "\\bsorry to break in\\b" to "sorry to interrupt",
        "\\bbreak in\\b(?=,|\\.|$)" to "interrupt",
        // "goes screwy" -> "vidalamaya gider"
        "\\bgoes screwy\\b" to "loses his mind",
        "\\bgo screwy\\b" to "lose their minds",
        // "cast a pall on" -> "pall attılar"
        "\\bcast a (?:bit of a )?pall on\\b" to "cast a shadow over",
        // "getting out of hand" -> "elinden çıkıyor"
        "\\bgetting out of hand\\b" to "getting out of control",
        "\\bout of hand\\b" to "out of control",
        // "keep your pants on" -> "pantolonunu giy"
        "\\bkeep your pants on\\b" to "be patient",
        // "hand out endowments" -> vakif baglami
        "\\byour foundation\\b" to "your charity organization",
        "\\bthe foundation\\b(?= doesn| does| gives| hands)" to "the charity organization",
        // "second to none" -> "hicbiri ikinci"
        "\\bsecond to none\\b" to "the best there is",
        // "you all right" -> "Hepiniz dogru mu"
        "\\byou all right\\b" to "are you okay",
        "\\bare you all right\\b" to "are you okay",
        // "all this way" -> "Butun bunlar"
        "\\ball this way\\b" to "this whole distance",
        // "poindexter" -> "PoindExter"
        "\\bpoindexter\\b" to "nerd",
        "\\bpoindexters\\b" to "nerds",
        // --- 9.86: 12 Kasım üçüncü kayıt
        // "the end of our race" -> "Yarışımızın sonu" / YZ: "Koşumuzun sonu".
        // İyelik ya da niteleyiciyle gelen "race" ırk/tür demektir.
        "\\b(our|his|her|their|my|your|entire|whole|human|alien|dying|ancient) race\\b" to "$1 species",
        // "are calling it the Scourge virus" -> "virüsünü arıyorlar" (telefon)
        "\\b(are|is|were|was) calling (it|him|her|them|this|that)\\b" to "$1 naming $2",
        // "Then clearly they didn't..." -> "Sonra açıkça": burada "o hâlde"
        "^then,? (clearly|obviously)\\b" to "so $1",
        "\\bogres\\b" to "giants",
        "\\bogre\\b" to "giant",
        // "First things first" -> "Ilk once ilk seyler"
        "^first things first\\b" to "to begin with",
        "\\bgonna\\b" to "going to",
        "\\bwanna\\b" to "want to",
        // SIRA ÖNEMLİ: "has/have gotta" -> "got to", yalın "gotta" -> "have to".
        // Tek kural yazılınca "This has gotta be the door" -> "This has HAVE TO
        // be the door" oluyor ve bozuk İngilizce çeviri motoruna gidiyordu.
        "\\b(has|have|'s|'ve)\\s+(gotta|hafta)\\b" to "$1 got to",
        "\\b(gotta|hafta)\\b" to "have to",
        // "y'all" tek yazım değil: "ya'll" ve "yall" da sık. Eksik bırakılınca
        // "Ya'll need something?" kısaltma kuralına takılıp "you'll" oluyordu.
        //
        // KARŞILIĞI "you all" DEĞİL, "you". Çeviri motoru "you all" görünce
        // "Hepiniz" diyor ve cümlenin fiili tekil kalıyor; sonuç hem yanlış
        // hem Türkçe değil. Kullanıcının 11 Kasım kaydından:
        //   "No. Y'all know Boobie?"      -> "Hepiniz Boobie'yi biliyor musun?"
        //   "Y'all tryna smoke?"          -> "Hepiniz ot satın almak istiyor musunuz?"
        //   "Let me tell y'all something" -> "Size HER ŞEYİ söyleyeyim"
        // Türkçede çokluk fiil ekinde taşınır, ayrı sözcük gerekmez; "you"
        // bırakmak doğal cümle veriyor. Yapay zekâ yolunda çokluk bilgisi
        // Slang.hints'teki "y'all = you (PLURAL)" ipucuyla korunuyor.
        "\\by'?all\\b|\\bya'?ll\\b" to "you",
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
        "^sup\\b" to "What's up",
        "\\bfo(?: |')sho\\b" to "for sure",
        "\\bfa sho\\b" to "for sure",
        "\\bweps\\b" to "weapons",
        "\\bhard pass\\b" to "definitely not",
        "\\bon a roll\\b" to "doing great",
        "\\bdawg\\b" to "buddy",
        "\\bsalad (days|years)\\b" to "best youthful days",
        "\\bgotcha\\b" to "got you",
        // "whatcha doing" = what ARE you; "whatchu want" = what DO you.
        // Ayrı kelimeler, ayrı açılımlar. Tek kurala bağlanırsa biri bozulur.
        "\\bwhatcha\\b" to "what are you",
        "\\bwhatchu\\b" to "what do you",
        "\\bima\\b" to "I'm going to",
        "\\btryna\\b" to "trying to",
        "\\bfinna\\b" to "about to",
        "\\bya\\b" to "you",
        "\\byou feel me\\b" to "you understand me",
        "\\baight\\b" to "alright",
        "\\byo (momma|mama|mom|mother|ass|house|car|boy|girl|man|people|crew|place|brother|sister|family|money|shit)\\b" to "your $1",
        "\\b(momma|mama)\\b" to "mother",
        "\\b(your|my|his|her) mother house\\b" to "$1 mother's house",
        "\\bniggas\\b" to "guys",
        "\\bnigga\\b" to "guy",
        "\\btooled up\\b" to "armed",
        // "Is Raymond around?" -> taslak "Raymond çevresinde mi?" diyordu.
        // "around" cümle SONUNDA olmalı; "around the corner" bozulmasın.
        "\\bis ([^\\W\\d_][\\w']*) around(?=[?!.]|$)" to "is $1 here",
        "\\b(he|she|they) around(?=[?!.]|$)" to "$1 here",
        // "Go inside and ask for Raymond" -> taslak fiili tamamen düşürüyordu.
        "\\bask for\\b" to "ask to see",
        "\\bpaying (\\w+) a visit\\b" to "visiting $1",
        "\\bpay (\\w+) a visit\\b" to "visit $1",
        "\\byo\\b" to "hey",
        "\\bget (your|ya) ass\\b" to "catch you",
        "\\bwound up (a little |a bit |pretty |really |real |so |too )?tight\\b" to "$1tense",
        "\\bI get (that|it)( too)?(?=[.!?,]|$)" to "I understand $1$2",
        "\\bget it\\?" to "understand?",
        "\\bcellmates?\\b" to "prison roommate",
        "\\b(former|ex-?) (\\w+ )?intelligence\\b" to "$1 $2intelligence officer",
        "^(anybody|anyone|somebody|someone|whoever) (messes|fucks|screws|touches|tries) " to "If $1 $2 ",
        "\\banswer to (my|the|his|her|our|your) boss\\b" to "will answer to $1 boss",
        "\\bburg\\b" to "town",
        "\\bwhat the (fuck|hell) going on\\b" to "what the $1 is going on",
        "\\b(we|we're|we are) in a holding pattern\\b" to "we are waiting",
        "\\bholding pattern\\b" to "waiting",
        "\\b(don't|do not|doesn't|didn't) give a damn\\b" to "$1 care",
        "^ay,\\s*" to "Hey, ",
        "\\b(mooks|goons)\\b" to "thugs",
        "\\b(damned|damn|fucking|goddamn|dirty|stinking|lousy) screws\\b" to "$1 prison guards",
        "\\bthe screws\\b" to "the prison guards",
        "\\bwheelm[ae]n\\b" to "getaway driver",
        "\\bmonkey[- ]suits?\\b" to "uniforms",
        "\\bfit right in\\b" to "fit in perfectly",
        "\\btop of the world\\b" to "king of the world",
        "\\bput (his |their |your |my |her )?hands on (me|you|him|her|us|them)\\b" to "touch $2",
        "\\breach out to\\b" to "contact",
        "\\breach out\\b" to "get in touch",
        "\\blooking for a situation\\b" to "looking for work",
        "\\bwhat's the deal\\b" to "what is going on",
        "\\bhang on\\b" to "wait",
        "\\bhang out\\b" to "spend time",
        "\\bfigure (it|this|that) out\\b" to "solve $1",
        "\\bmy bad\\b" to "my mistake",
        "\\bno big deal\\b" to "not important",
        "\\bbig deal\\b" to "important thing",
        "\\bfor real\\b" to "really",
        "\\bget lost\\b" to "go away",
        "\\bbeat it\\b" to "go away",
        "\\bpiss(ed)? off\\b" to "angry",
        "\\bfreak(ing)? out\\b" to "panicking",
        "\\bscrew(ed)? up\\b" to "ruined",
        "\\bmess(ed)? up\\b" to "ruined",
        "\\bcatch you later\\b" to "see you later",
        "\\bhit me up\\b" to "call me",
        "\\bcut it out\\b" to "stop it",
        "\\bknock it off\\b" to "stop it",
        "\\bget a move on\\b" to "hurry up",
        // --- 10.11: 13 Kasım 10.9 kaydı (Google da bunları kelime kelime çeviriyor)
        // "Brian was quite the stud back in the day" -> "Brian o gün oldukça da damıydı"
        // 10.12: "ladies' man" Google'da "bayan bir erkek" oldu; "womanizer" -> "çapkın".
        "\\b(quite the|such a|a real|a total|was a|is a) stud\\b" to "$1 womanizer",
        "\\bback in the day\\b" to "back then",
        // "let's take in the view" -> "manzarayı ele alalım"
        "\\btake in (the|this) (view|sights|scenery)\\b" to "enjoy $1 $2",
        "\\bkeep it down\\b" to "be quiet",
        "\\bwatch your back\\b" to "be careful",
        "\\bgot your back\\b" to "will protect you",
        "\\bin a nutshell\\b" to "briefly",
        "\\bon the house\\b" to "free",
        "\\boff the grid\\b" to "unreachable",
        "\\bhold up\\b" to "wait",
        "\\bnah\\b" to "no",
        "(?<![\\p{L}])'bout\\b" to "about",
        "(?<![\\p{L}])'til\\b" to "until",
        // Kıvrık kesme işareti (’) de kabul: OCR ikisini de üretiyor.
        "\\b(\\p{L}{2,})in['’](?![\\p{L}])" to "$1ing"
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
    /** tails: cümlenin sonuna eklenecek Türkçe küfür/ünlem ("amına koyayım"); sertlik kaybolmasın diye. */
    class Shielded(val text: String, val slots: List<Slot>, val tails: List<String> = emptyList())

    private const val HARD = "amına koyayım"
    private const val DAMN = "lanet olsun"
    private const val MILD = "be"

    // Kural: küfür SİLİNMEZ. Cümle içinde Türkçeye birebir oturmayan pekiştirici küfür
    // ("fucking", "the fuck") İngilizce cümleden çıkarılır ama sertliği cümlenin SONUNA
    // Türkçe olarak eklenir: "Don't fucking move!" -> "Kıpırdama, amına koyayım!"
    private class Rule(val re: Regex, val replacement: String, val tail: String?)
    private fun rule(p: String, r: String, tail: String?) = Rule(Regex("$p", RegexOption.IGNORE_CASE), r, tail)

    private val rules: List<Rule> = listOf(
        // "I don't give a fuck" -> "Umurumda değil" + "amına koyayım"
        rule("\\b(don't|do not|doesn't|didn't)\\s+give\\s+a\\s+fuck\\b", "$1 care", HARD),
        rule("\\b(don't|do not|doesn't|didn't)\\s+give\\s+a\\s+shit\\b", "$1 care", MILD),
        // "I'm fucked" / "we're so fucked" -> "mahvolduk" + "amına koyayım"
        rule("\\b(I'm|I am|we're|we are|you're|you are|he's|he is|she's|she is|they're|they are|it's|it is)\\s+(so\\s+|totally\\s+|completely\\s+|really\\s+)?fucked(?!\\s+up)\\b", "$1 $2ruined", HARD),
        // "fucked up" sıfat: "fucked up shit" / "that's fucked up" -> "berbat"
        rule("\\bfucked[- ]up (?:shit|stuff|things?)\\b", "terrible things", HARD),
        rule("\\b(is|was|that's|it's|this is|so|really|pretty)\\s+fucked[- ]up\\b", "$1 terrible", HARD),
        // "fucked up" (fiil) -> "messed up" + sertlik
        rule("\\bfucked\\s+up\\b", "messed up", HARD),
        rule("\\bfucking\\s+up\\b", "messing up", HARD),
        rule("\\bfucks\\s+up\\b", "messes up", HARD),
        rule("\\bfuck\\s+up\\b", "mess up", HARD),
        rule("\\bfuck(?:ing|in'?)\\s+with\\b", "messing with", HARD),
        rule("\\bfucks\\s+with\\b", "messes with", HARD),
        rule("\\bfuck\\s+with\\b", "mess with", HARD),
        // "what the fuck ..." -> "what the hell ..." + sertlik
        rule("\\b(what|who|where|why|how|when)\\s+the\\s+(?:fuck|fucking hell)\\b", "$1 the hell", HARD),
        rule("\\s+the\\s+fuck\\b", "", HARD),
        rule("\\bfucking\\s+hell\\b", "damn it", HARD),
        // pekiştiriciler
        rule("\\bmotherfucking\\s+", "", HARD),
        rule("\\bfuck(?:ing|in'?)\\s+", "", HARD),
        rule("\\bgoddamn(?:ed)?\\s+", "", DAMN),
        rule("\\b(?:freaking|effing|frickin'?|friggin'?)\\s+", "", MILD),
        // "shit" bağlama göre: eşya, dayak, büyük bela
        rule("\\b(my|your|his|her|our|their) shit\\b", "$1 stuff", null),
        rule("\\bthe (?:shit|crap|hell) out of (me|you|him|her|them|us|it)\\b", "$1 badly", HARD),
        rule("\\bin deep shit\\b", "in big trouble", HARD),
        rule("\\bget your shit together\\b", "pull yourself together", MILD),
        // deyimler
        rule("\\blife's a bitch\\b", "life is hard", DAMN),
        rule("\\bbitch(?:ing)?\\s+about\\b", "complaining about", null)
    )

    // Bağlama göre: aynı kelime farklı yerde farklı anlam taşır. Özel kalıplar ÖNCE gelir.
    private val words: List<Pair<Regex, String>> = listOf(
        "holy shit" to "hassiktir",
        "oh,? shit" to "hassiktir",
        "no shit" to "yok artık",
        "pieces? of shit" to "pislik",
        "shit happens" to "olur böyle şeyler",
        "holy crap" to "vay anasını",
        "oh,? crap" to "hay aksi",
        "fuck no" to "hayatta olmaz",
        "fuck (?:that|this) shit" to "siktir et bunu",
        // Ünlem "shit" (başta ya da "Ah, shit!"): "siktir". Önünde kelime varsa ("that shit!") isimdir.
        "(?<![\\p{L}'’] )shit(?=\\s*[,!?.…]|$)" to "siktir",
        "fuck yeah" to "işte bu be",
        "son of a bitch(?:es)?" to "orospu çocuğu",
        "motherfuckers?" to "orospu çocuğu",
        "go fuck yourself" to "siktir git",
        "fuck (?:off|you)" to "siktir git",
        "fuck (?:this|that|it)" to "siktir et",
        "bullshit" to "saçmalık",
        "horseshit" to "saçmalık",
        "shitty" to "berbat",
        "shits?" to "bok",
        "crap" to "zırva",
        "assholes?" to "pislik herif",
        "dickheads?" to "göt herif",
        "bitch(?:es)?" to "kaltak",
        "bastards?" to "piç",
        "(?<=(?:ungrateful|dirty|filthy|little|cheap|stupid|you) )tramps?" to "sürtük",
        "pussy" to "korkak",
        "fuck(?:ed|s)?" to "siktir"
    ).map { Regex("\\b${it.first}\\b", RegexOption.IGNORE_CASE) to it.second }

    private val names = "abcdefghijklmnopqrstuvwxyz".map { "Zq$it" }

    // "this fucking car" -> "bu lanet olası araba": isimden önceki küfür YERİNDE kalır.
    private val inPlace = Regex("\\b(this|that|the|my|your|his|her|our|their|a|an|these|those|some|every|no|whole|one)\\s+(motherfucking|fucking|fuckin'?|goddamn(?:ed)?|damn|damned)\\s+(?=\\p{L})", RegexOption.IGNORE_CASE)

    fun shield(text: String): Shielded {
        var t = text
        val tails = ArrayList<String>()
        val slots = ArrayList<Slot>()
        // Yer tutucu yerine İngilizce "damn": yerleşik çeviri "lanet para" der; yer tutucu "lanet olası parası" gibi bozuyordu.
        t = inPlace.replace(t) { m -> m.groupValues[1] + " damn " }
        for (r in rules) {
            if (r.re.containsMatchIn(t)) {
                t = r.re.replace(t, r.replacement)
                if (r.tail != null && r.tail !in tails) tails += r.tail
            }
        }
        t = t.replace(Regex("\\s{2,}"), " ").trim()
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
        // Cümlede zaten "siktir/orospu çocuğu" gibi sert bir küfür kalıyorsa ayrıca "be" eklemeye gerek yok.
        val finalTails = if (slots.isNotEmpty()) tails.filter { it != MILD } else tails
        return Shielded(t, slots, finalTails.take(1))
    }

    /** Çeviriye cümle sonu küfrünü ekler: "Kıpırdama!" + "amına koyayım" -> "Kıpırdama, amına koyayım!" */
    fun addTails(translated: String, tails: List<String>): String {
        if (tails.isEmpty() || translated.isBlank()) return translated
        val tail = tails.first()
        if (translated.contains(tail, ignoreCase = true)) return translated
        val m = Regex("[.!?…]+$").find(translated.trimEnd())
        val body = translated.trimEnd().removeSuffix(m?.value ?: "").trimEnd(',', ' ')
        val punct = m?.value ?: ""
        return if (tail == MILD) "$body $tail$punct" else "$body, $tail$punct"
    }

    /** Geçici isimleri Türkçe karşılığıyla değiştirir; biri kayıpsa null (yedek yol gerekir). */
    fun restore(translated: String, slots: List<Slot>, tr: Locale): String? {
        var t = translated
        for (s in slots) {
            val re = Regex("\\b${s.token}(?:['’][\\p{L}]+)?", RegexOption.IGNORE_CASE)
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

/**
 * Ses efekti etiketleri: "[suspenseful music playing]" -> "[gerilim müziği çalıyor]".
 * ML Kit'e gitmez. OCR'ın köşeli parantezi I, l, |, (, { diye okumasını da tolere eder.
 */
object SoundTags {
    /**
     * EKSİK SÖZCÜK DEFTERİ.
     *
     * translatePart bilinmeyen sözcüğü SESSİZCE düşürüyordu:
     *   "[exciting music playing]"  -> "[müzik çalıyor]"   ("exciting" uçtu)
     *   "[metallic creaking]"       -> "[gıcırdıyor]"      ("metallic" uçtu)
     *   "[tracks squeaking]"        -> "[gıcırdıyor]"      ("tracks" uçtu)
     * Kullanıcı kaydında hiçbir iz kalmıyordu, bu yüzden sürüm sürüm aynı
     * sınıf hata çıkıyordu. Artık eksik sözcük buraya yazılıyor ve
     * CaptureService tanı satırında gösteriyor: boşluk görünür hâle geliyor.
     */
    private val eksikler = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    /** Biriken eksik sözcükleri verir ve defteri boşaltır. */
    fun eksikleriAl(): List<String> = synchronized(eksikler) {
        val l = eksikler.toList()
        eksikler.clear()
        l
    }

    private fun eksikYaz(w: String) {
        if (w.length < 4 || !w.all { it.isLetter() }) return
        synchronized(eksikler) { if (eksikler.size < 60) eksikler += w }
    }

    private val words: Map<String, String> = mapOf(
        "insects" to "böcekler", "trilling" to "ötüyor", "triling" to "ötüyor",
        "crickets" to "cırcır böcekleri", "cricket" to "cırcır böceği", "leaves" to "yapraklar", "leaf" to "yaprak",
        "exclaims" to "sesleniyor", "clang" to "metal çınlaması", "clangs" to "metal çınlaması",
        "squeal" to "gıcırtı", "squealing" to "gıcırdıyor", "thudding" to "tok sesle vuruyor", "truck" to "kamyon",
        "deep" to "derin", "sigh" to "iç çekiş", "chirp" to "ötüyor", "voice" to "ses",
        "music" to "müzik", "song" to "şarkı", "suspenseful" to "gerilim", "dramatic" to "dramatik", "tense" to "gergin",
        "ominous" to "tekinsiz", "eerie" to "ürkütücü", "upbeat" to "hareketli", "soft" to "hafif", "sad" to "hüzünlü",
        "somber" to "hüzünlü", "action" to "aksiyon", "rock" to "rock", "jazz" to "caz", "classical" to "klasik", "gentle" to "yumuşak",
        "playing" to "çalıyor", "plays" to "çalıyor", "continues" to "devam ediyor", "continuing" to "devam ediyor",
        "swells" to "yükseliyor", "builds" to "yükseliyor", "rises" to "yükseliyor", "intensifies" to "yoğunlaşıyor",
        "fades" to "kısılıyor", "stops" to "duruyor", "ends" to "bitiyor", "resumes" to "yeniden başlıyor",
        "grunts" to "homurtular", "grunt" to "homurtu", "grunting" to "homurdanıyor",
        "groans" to "inlemeler", "groan" to "inleme", "groaning" to "inliyor", "moans" to "inlemeler", "moaning" to "inliyor",
        "pained" to "acılı", "struggling" to "boğuşuyor", "struggles" to "boğuşma", "straining" to "zorlanıyor",
        "cracking" to "çatırtı", "crack" to "çatırtı", "cracks" to "çatırtı", "crunch" to "çatırtı",
        "gunshot" to "silah sesi", "gunshots" to "silah sesleri", "gunfire" to "silah sesleri", "gun" to "silah",
        "cocks" to "kuruluyor", "cocking" to "kuruluyor", "reloads" to "dolduruluyor",
        "whimpers" to "sızlanma", "whimpering" to "sızlanıyor", "screams" to "çığlıklar", "scream" to "çığlık",
        "screaming" to "çığlık atıyor", "shrieks" to "çığlıklar", "laughs" to "gülüyor", "laughing" to "gülüyor",
        "laughter" to "kahkahalar", "chuckles" to "kıkırdıyor", "chuckling" to "kıkırdıyor", "sighs" to "iç çekiyor",
        "sighing" to "iç çekiyor", "gasps" to "nefesi kesiliyor", "gasping" to "nefes nefese", "panting" to "soluyor", "gasp" to "nefesi kesiliyor",
        "running" to "çalışıyor", "guitar" to "gitar", "unintelligible" to "anlaşılmaz", "facetious" to "alaycı", "shock" to "şaşkınlık",
        "chanting" to "tezahürat", "chant" to "tezahürat", "chants" to "tezahürat", "group" to "grup",
        "smoke" to "duman", "yelp" to "çığlık", "yelping" to "çığlık atıyor",
        "breathing" to "nefes alıyor", "heavily" to "derin derin", "coughing" to "öksürüyor", "coughs" to "öksürüyor",
        "crying" to "ağlıyor", "cries" to "ağlıyor", "sobbing" to "hıçkırıyor", "sobs" to "hıçkırıyor",
        "whispering" to "fısıldıyor", "whispers" to "fısıldıyor", "shouting" to "bağırıyor", "shouts" to "bağırıyor",
        "yelling" to "bağırıyor", "yells" to "bağırıyor", "indistinct" to "anlaşılmaz", "chatter" to "konuşmalar",
        "chattering" to "konuşmalar", "speaking" to "konuşuyor", "speaks" to "konuşuyor", "spanish" to "İspanyolca",
        "footsteps" to "ayak sesleri", "door" to "kapı", "opens" to "açılıyor", "closes" to "kapanıyor", "slams" to "çarpıyor",
        "knocking" to "kapı çalınıyor", "knocks" to "kapı çalınıyor", "phone" to "telefon", "ringing" to "çalıyor",
        "rings" to "çalıyor", "buzzing" to "vızıldıyor", "vibrating" to "titriyor", "explosion" to "patlama",
        "electricity" to "elektrik", "electrical" to "elektriksel", "electric" to "elektrik",
        "explosions" to "patlamalar", "siren" to "siren", "sirens" to "sirenler", "wailing" to "çalıyor",
        "engine" to "motor", "revving" to "gürlüyor", "revs" to "gürlüyor", "tires" to "lastikler", "screeching" to "gıcırdıyor",
        "glass" to "cam", "shatters" to "kırılıyor", "shattering" to "kırılıyor", "thud" to "gümleme", "thuds" to "gümlemeler",
        "punch" to "yumruk", "punches" to "yumruklar", "fighting" to "dövüş", "fight" to "dövüş", "sounds" to "sesleri",
        "sound" to "sesi", "growls" to "hırlıyor", "growling" to "hırlıyor", "barking" to "havlıyor", "dog" to "köpek",
        "thunder" to "gök gürültüsü", "rain" to "yağmur", "wind" to "rüzgâr", "silence" to "sessizlik", "beeping" to "bip sesi",
        "static" to "parazit", "radio" to "telsiz", "helicopter" to "helikopter", "and" to "ve", "loud" to "yüksek",
        "distant" to "uzaktan", "muffled" to "boğuk", "sniffs" to "burnunu çekiyor", "groani" to "inleme",
        "inhales" to "nefes alıyor", "exhales" to "nefes veriyor", "clears" to "temizliyor", "throat" to "boğazını",
        "applause" to "alkış", "starts" to "çalışıyor", "starting" to "çalışıyor", "idling" to "rölantide", "alarm" to "alarm", "blaring" to "çalıyor", "blares" to "çalıyor",
        "beeps" to "bip sesi", "heavy" to "derin derin", "low" to "alçak", "rumble" to "gümbürtü", "rumbling" to "gürlüyor",
        "rumbles" to "gürlüyor", "crashing" to "çarpışma", "crash" to "çarpışma", "smashing" to "kırılıyor",
        "splash" to "su sesi", "splashing" to "su sesi", "creaks" to "gıcırdıyor", "creaking" to "gıcırdıyor",
        "squeaks" to "gıcırdıyor", "rattling" to "tıkırdıyor", "rattles" to "tıkırdıyor", "hissing" to "tıslıyor",
        "humming" to "mırıldanıyor", "hums" to "mırıldanıyor", "yelps" to "çığlık",
        "kicks" to "tekmeler", "kick" to "tekme", "slap" to "tokat", "stabbing" to "bıçaklama", "squelch" to "vıcık ses",
        "body" to "ceset", "falls" to "düşüyor", "thumps" to "gümlüyor", "lock" to "kilit", "unlocks" to "açılıyor",
        "car" to "araba", "horn" to "korna", "honking" to "korna çalıyor", "honks" to "korna çalıyor",
        "door's" to "kapı", "open" to "açık", "shut" to "kapanıyor", "bell" to "zil", "cheering" to "tezahürat", "crowd" to "kalabalık", "clicks" to "tıklıyor", "click" to "tık",
        "thunderous" to "gök gürültüsü gibi", "roar" to "kükreme", "roars" to "kükrüyor", "roaring" to "kükrüyor",
        "booming" to "gümbürdüyor", "booms" to "gümbürdüyor", "boom" to "gümbürtü", "stomping" to "ağır adımlar",
        "stomps" to "ağır adımlar", "bellowing" to "böğürüyor", "bellows" to "böğürüyor", "snarling" to "hırlıyor",
        "snarls" to "hırlıyor", "howling" to "uluyor", "howls" to "uluyor",
        "scoffs" to "küçümseyerek gülüyor", "scoff" to "küçümseyerek gülüyor", "scoffing" to "küçümseyerek gülüyor",
        "clicking" to "tıkırtı", "increasing" to "artıyor", "intensifies" to "şiddetleniyor",
        "prisoners" to "mahkûmlar", "inmates" to "mahkûmlar", "chuckles" to "kıkırdıyor", "chuckling" to "kıkırdıyor",
        // Konuşan etiketleri: "[driver]", "[man]" -> "[sürücü]", "[adam]".
        // Eskiden bunlar sözlükte olmadığı için "[ses efekti]" oluyordu.
        // 11 Kasım 9.63 kaydı (Batman filmi): eksik oldukları için parantez
        // onarımı da çalışmıyordu — onarım, içindeki her sözcüğü tanımazsa
        // etiketi etiket saymıyor. Sonuç kırpılmış etiketler:
        //   "[grunts and pants]" -> "[homurtular ve]"      ("pants" yok)
        //   "[tires screech]"    -> "[lastikler]"          ("screech" yok)
        //   "[high-pitched ringingl" -> "[çalıyor]"        ("high-pitched" yok)
        //   "snaps" parantezi bozuk okunmus -> "[ses efekti]"  ("snaps" yok)
        // 11 Kasım 9.64 kaydı (Invincible): eksik oldukları için hem etiketten
        // kelime düşüyordu hem de parantez onarımı çalışmıyordu — onarım,
        // içindeki her sözcüğü tanımazsa etiketi etiket saymıyor:
        //   "[militaristic music playingl" -> "[müzik çalıyor]"
        //   "[short breaths]"              -> "[ses efekti]"
        //   "I energetic music playing]"   -> "Enerjik Müzik Oynarken]"
        //   "I grunting fiercely]"         -> "Ben şiddetle homurdanıyorum]"
        //   "[children in unison] ..."     -> "[ses efekti] ..."
        // 9.65 kaydi (Invincible 2): yine eksik sozcukler
        //   "I atmospheric music playing]"  -> "I atmosferik müzik çalma]" (onarilmadi)
        //   "[haunting vocalizing over atmospheric music]" -> "[müzik]"
        //   "[alert tone blaring]"          -> "[çalıyor]"
        //   "[sharp gasping breath]"        -> "[nefes nefese nefes]"
        // ====================================================================
        // TARAMAYLA BULUNAN EKSİKLER (kayıt beklemeden).
        //
        // 11 Kasım: "bu katman başka neyi sessizce düşürüyor?" diye sorup
        // altyazı ses ipuçlarında sık geçen 215 kelimeyi tarattım; 97'si
        // eksikti. translatePart tanımadığı kelimeyi sessizce atıyor, yani
        // her biri bir gün "[ses efekti]" ya da kırpılmış etiket olarak
        // ekrana gelecekti. Kaydı bekleyip tek tek düzeltmek yerine sınıfı
        // birden kapatmak.
        // ====================================================================
        // zil, kapı, ev
        "doorbell" to "kapı zili", "ding" to "çan sesi", "dong" to "çan sesi",
        "chime" to "çan sesi", "beep" to "bip sesi", "elevator" to "asansör",
        "escalator" to "yürüyen merdiven",
        // su
        "water" to "su", "dripping" to "damlıyor", "pouring" to "dökülüyor",
        "flushing" to "sifon çekiliyor", "boiling" to "kaynıyor", "bubbling" to "fokurduyor",
        // malzeme sesleri
        "clothes" to "kıyafet", "rustling" to "hışırdıyor", "paper" to "kâğıt",
        "tearing" to "yırtılıyor", "zipper" to "fermuar", "velcro" to "cırt cırt",
        "metal" to "metal", "scraping" to "sürtünüyor", "squeaking" to "gıcırdıyor",
        "clanking" to "takırdıyor", "crackling" to "çıtırdıyor",
        // ofis, cihaz
        "keyboard" to "klavye", "typing" to "yazıyor", "mouse" to "fare",
        "printer" to "yazıcı", "scanner" to "tarayıcı", "feedback" to "cızırtı",
        "distortion" to "bozulma", "echo" to "yankı", "reverb" to "yankı",
        // araçlar
        "bus" to "otobüs", "truck" to "kamyon", "motorcycle" to "motosiklet",
        "airplane" to "uçak", "jet" to "jet", "train" to "tren", "subway" to "metro",
        "brakes" to "frenler", "reload" to "şarjör değiştiriyor",
        // insan sesleri
        "baby" to "bebek", "audience" to "seyirci", "murmuring" to "mırıldanıyor",
        "muttering" to "söyleniyor", "stammering" to "kekeliyor", "stuttering" to "kekeliyor",
        "singing" to "şarkı söylüyor", "whistling" to "ıslık çalıyor",
        "yawns" to "esniyor", "snores" to "horluyor", "sneezes" to "hapşırıyor",
        "hiccups" to "hıçkırıyor", "belches" to "geğiriyor", "burps" to "geğiriyor",
        "gulps" to "yutkunuyor", "swallows" to "yutkunuyor", "wheezes" to "hırıldıyor",
        "vomiting" to "kusuyor", "booing" to "yuhalıyor",
        // hayvan
        "barks" to "havlıyor", "meows" to "miyavlıyor", "purrs" to "mırıldıyor",
        "chirping" to "cıvıldıyor", "hisses" to "tıslıyor",
        // tıp
        "heartbeat" to "kalp atışı", "pulse" to "nabız", "monitor" to "monitör",
        "flatline" to "kalp durdu", "defibrillator" to "defibrilatör",
        // dövüş, fantastik
        "sword" to "kılıç", "clashing" to "çarpışıyor", "arrow" to "ok",
        "slicing" to "kesiyor", "magic" to "büyü", "sparkle" to "parıltı",
        "portal" to "geçit", "teleport" to "ışınlanma", "fire" to "ateş",
        // müzik
        "melody" to "ezgi", "theme" to "tema", "playful" to "oyunbaz",
        // nitelik
        "harsh" to "sert", "sudden" to "ani", "fast" to "hızlı", "slow" to "yavaş",
        "rapid" to "hızlı", "steady" to "düzenli", "rhythmic" to "ritmik",
        "overlapping" to "üst üste", "simultaneous" to "aynı anda",
        "inaudible" to "duyulmuyor",
        // hâl zarfları (ZARF kümesinde de var: fiilden ÖNCE gelirler)
        "loudly" to "yüksek sesle", "slowly" to "yavaşça", "suddenly" to "aniden",
        "violently" to "şiddetle", "frantically" to "çılgınca",
        "desperately" to "umutsuzca",
        // 9.66 kaydi (Invincible 2): yine eksik
        //   "Fine. [loud thump]"      -> "[yüksek]"      ("thump" yok)
        //   "-[creature yowling]"     -> "[ses efekti]"
        //   "-[curtain opens"         -> "[açılıyor]"
        //   "[intriguing music playingl" -> "[müzik çalıyor]"
        "thump" to "gümleme", "thumped" to "gümledi",
        "creature" to "yaratık", "creatures" to "yaratıklar",
        "yowling" to "uluyor", "yowls" to "uluyor", "mewls" to "mırlıyor",
        "mewling" to "mırlıyor", "purring" to "mırıldıyor", "purrs" to "mırıldıyor",
        "curtain" to "perde", "curtains" to "perdeler",
        "intriguing" to "merak uyandıran", "quirky" to "tuhaf", "upbeat" to "hareketli",
        "uplifting" to "coşkulu", "hopeful" to "umutlu", "whimsical" to "şakacı",
        "epic" to "destansı", "heroic" to "kahramanca", "mysterious" to "gizemli",
        // --- 9.75: kayıtta sessizce düşen sözcükler + aynı sınıftan batı
        "exciting" to "heyecanlı", "emotional" to "duygusal", "metallic" to "metalik",
        "belabored" to "zorlanan", "shivering" to "titreyerek", "tracks" to "raylar",
        "triumphant" to "muzaffer", "melancholy" to "melankolik", "melancholic" to "melankolik",
        "haunting" to "ürkütücü", "serene" to "huzurlu", "peaceful" to "huzurlu",
        "frantic" to "telaşlı", "urgent" to "acil", "menacing" to "tehditkâr",
        "sinister" to "uğursuz", "foreboding" to "kötücül", "whimsy" to "şakacı",
        "solemn" to "ağırbaşlı", "reflective" to "düşündürücü", "wistful" to "hüzünlü",
        "bittersweet" to "buruk", "joyful" to "neşeli", "cheerful" to "neşeli",
        "lively" to "canlı", "energetic" to "hareketli", "pulsing" to "nabız gibi",
        "pounding" to "gümbürdüyor", "thumping" to "gümlüyor", "driving" to "sürükleyici",
        "swelling" to "yükselen", "soaring" to "yükselen", "distorted" to "bozuk",
        "faint" to "hafif",
        "echoing" to "yankılanan", "reverberating" to "yankılanan",
        "orchestral" to "orkestral", "instrumental" to "enstrümantal",
        "electronic" to "elektronik", "synth" to "synth", "piano" to "piyano",
        "violin" to "keman", "drums" to "davul",
        "choral" to "koro", "operatic" to "opera", "folk" to "halk",
        "hopeless" to "umutsuz", "tragic" to "trajik", "romantic" to "romantik",
        "comedic" to "komik", "whirring" to "vınlıyor",
        "sizzling" to "cızırdıyor", "clattering" to "takırdıyor",
        "grinding" to "öğütüyor", "snapping" to "çatırdıyor",
        "splintering" to "parçalanıyor",
        "wheezing" to "hırıldıyor", "sniffling" to "burnunu çekiyor",
        "trembling" to "titriyor",
        "labored" to "zorlanan", "ragged" to "kesik kesik", "shaky" to "titrek",
        // --- 9.85: 12 Kasım ikinci kayıt ("SES SÖZCÜĞÜ EKSİK: rousing, chittering, beetles")
        "rousing" to "coşturucu", "mournful" to "yaslı", "chittering" to "cırlıyor",
        "beetles" to "böcekler", "beetle" to "böcek", "bed" to "yatak",
        "banging" to "çarpıyor", "rhythmically" to "ritmik şekilde",
        "softly" to "usulca", "quietly" to "sessizce",
        // --- 9.84: 12 Kasım kaydı ("SES SÖZCÜĞÜ EKSİK: thrilling") + aynı sınıf
        "thrilling" to "sürükleyici", "adventurous" to "maceralı", "mischievous" to "muzip",
        "sentimental" to "duygulu", "tender" to "şefkatli", "poignant" to "dokunaklı",
        "inspirational" to "ilham verici", "majestic" to "görkemli", "grand" to "görkemli",
        "sweeping" to "geniş", "dark" to "karanlık", "light" to "hafif",
        "powering" to "güçleniyor", "charging" to "şarj oluyor", "exploding" to "patlıyor",
        "approaching" to "yaklaşıyor", "fading" to "kayboluyor", "begins" to "başlıyor",
        // --- 9.77: kayıtta "[ses efekti]" olan ses sözcükleri
        "keys" to "anahtarlar", "jangling" to "şıngırdıyor", "jangles" to "şıngırdıyor",
        "jingling" to "çıngırdıyor",
        "locks" to "kilitleniyor",
        "ticking" to "tıkırdıyor",
        "crunching" to "çatırdıyor", "flapping" to "çırpınıyor", "gurgling" to "guruldama",
        "slurping" to "höpürdetiyor", "chewing" to "çiğniyor", "swallowing" to "yutkunuyor",
        "yawning" to "esniyor",
        "clapping" to "el çırpıyor",
        "atmospheric" to "atmosferik", "ethereal" to "ruhani",
        "vocalizing" to "ses veriyor", "vocalization" to "ses",
        "over" to "eşliğinde", "alert" to "uyarı", "tone" to "sesi", "tones" to "sesleri",
        "sharp" to "keskin",
        "militaristic" to "askeri",
        "intense" to "yoğun",
        "fierce" to "sert", "fiercely" to "sertçe", "strained" to "zorlanarak",
        "weakly" to "zayıfça", "faintly" to "hafifçe", "sharply" to "keskin",
        "short" to "kısa", "breaths" to "nefesler", "breath" to "nefes",
        "gagging" to "öğürüyor", "gags" to "öğürüyor", "choking" to "boğuluyor",
        "soldiers" to "askerler", "soldier" to "asker", "cadet" to "öğrenci",
        "children" to "çocuklar", "child" to "çocuk", "unison" to "hep bir ağızdan",
        "crowd's" to "kalabalığın", "pain" to "acıyla",
        "pants" to "nefes nefese", "pant" to "nefes nefese",
        "screech" to "gıcırdıyor", "screeches" to "gıcırdıyor", "screeched" to "gıcırdadı",
        "high-pitched" to "tiz", "low-pitched" to "kalın", "piercing" to "keskin",
        "snaps" to "çıt sesi", "snap" to "çıt sesi",
        "shriek" to "çığlık", "shrieking" to "çığlık atıyor",
        "clatter" to "takırtı", "clatters" to "takırdıyor",
        "dispatcher" to "telsiz görevlisi", "dispatch" to "merkez",
        "whimper" to "sızlanma", "whines" to "inliyor", "whining" to "inliyor",
        "footstep" to "ayak sesi", "retching" to "öğürüyor",
        "spitting" to "tükürüyor",
        "driver" to "sürücü", "man" to "adam", "woman" to "kadın", "boy" to "çocuk",
        "girl" to "kız", "men" to "adamlar", "women" to "kadınlar", "narrator" to "anlatıcı",
        "announcer" to "spiker", "guard" to "gardiyan", "officer" to "memur",
        "reporter" to "muhabir", "doctor" to "doktor",
        "computer" to "bilgisayar", "tv" to "televizyon", "pa" to "anons",
        // Kayıttan eksik çıkanlar
        "crashes" to "çarpma sesi", "gate" to "kapı", "intensifying" to "yükseliyor",
        "sting" to "vurgusu", "buzzes" to "vızıldıyor", "zaps" to "elektrik sesi",
        "whooshing" to "uğultu", "whoosh" to "uğultu",
        // --- 9.97: 51 kaydın tamamı tarandı, "SES SÖZCÜĞÜ EKSİK" çıkanların hepsi
        "strains" to "zorlanıyor", "window" to "pencere", "respirator" to "solunum cihazı",
        "quiet" to "sessiz", "people" to "insanlar", "welding" to "kaynak sesi",
        "weapon" to "silah", "weak" to "zayıf", "thunking" to "tok ses", "thunk" to "tok ses",
        "skittering" to "tıkırdıyor", "shudders" to "ürperiyor", "shuddering" to "ürperiyor",
        "rocks" to "kayalar", "rapidly" to "hızla", "deeply" to "derin derin",
        "indistinctly" to "anlaşılmaz biçimde", "heart" to "kalp", "beating" to "atıyor",
        "cackling" to "kahkaha atıyor", "cackles" to "kahkaha atıyor",
        "hoarsely" to "boğuk sesle", "giggling" to "kıkırdıyor",
    )

    /**
     * "X continues" kalıbında X'in İSİM hâli.
     *
     * Kelime kelime çevirince "[yelling continues]" -> "bağırıyor devam ediyor"
     * çıkıyordu; Türkçesi bozuk. Doğrusu "bağırma devam ediyor".
     */
    private val devamAdi: Map<String, String> = mapOf(
        "yelling" to "bağırma", "yells" to "bağırma", "shouting" to "bağrışma",
        "screaming" to "çığlıklar", "screams" to "çığlıklar", "blaring" to "siren sesi",
        "music" to "müzik", "chatter" to "konuşmalar", "chattering" to "konuşmalar",
        "laughter" to "kahkahalar", "laughing" to "gülüşmeler", "crying" to "ağlama",
        "panting" to "soluma", "chanting" to "tezahürat", "grunting" to "homurdanma",
        "groaning" to "inleme", "ringing" to "çalma", "beeping" to "bip sesi",
        "alarm" to "alarm", "gunfire" to "silah sesleri", "explosion" to "patlama",
        "applause" to "alkış", "barking" to "havlama", "sirens" to "sirenler",
        "siren" to "siren", "whispering" to "fısıltılar", "coughing" to "öksürük",
        "breathing" to "nefes sesi", "footsteps" to "ayak sesleri", "fighting" to "dövüş",
        "rumbling" to "gürültü", "humming" to "uğultu", "static" to "parazit",
        "thunder" to "gök gürültüsü", "rain" to "yağmur", "wind" to "rüzgâr",
        "cheering" to "tezahürat", "sobbing" to "hıçkırık", "growling" to "hırlama",
        "moaning" to "inleme",
    )
    private val skip = setOf("the", "a", "an", "both", "softly", "quietly", "continue", "in")
    // "gerilim müziği" (isim tamlaması) ama "dramatik müzik" (sıfat)
    private val adjectives = setOf("suspenseful", "action", "rock", "jazz", "guitar", "piano")
    /**
     * HÂL ZARFLARI. Türkçede zarf fiilden ÖNCE gelir, İngilizcede sonra:
     *   "grunting fiercely"  -> "sertçe homurdanıyor",  "homurdanıyor sertçe" DEĞİL
     *   "breathing heavily"  -> "derin derin nefes alıyor"
     *   "cadet gasps in pain"-> "öğrenci acıyla nefesi kesiliyor"
     * Eksik sözcükler eklenince bu sıra hatası görünür hâle geldi: önce
     * kelimeler düşüyordu, düşmeyi durdurunca sıra yanlış kaldı.
     */
    private val ZARF = setOf(
        "fiercely", "weakly", "heavily", "loudly", "quietly", "sharply", "faintly",
        "slowly", "violently", "angrily", "deeply", "badly", "painfully", "pain",
        "frantically", "desperately", "nervously", "breathlessly", "softly",
        "hard", "lightly", "rapidly", "wildly", "bitterly", "nearby", "distantly",
        "suddenly", "steadily", "repeatedly", "briefly", "constantly", "rhythmically",
        "hoarsely", "indistinctly",
    )

    private val tagRe = Regex("[\\[({]([^\\[\\](){}]*)[\\])}]?")

    private fun isSoundWords(s: String): Boolean {
        val toks = s.lowercase().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        return toks.isNotEmpty() && toks.all { w ->
            val x = w.trim('\'', '.', '-')
            x in words || x in skip || (x.endsWith("l") && x.dropLast(1) in words) || x == "and"
        }
    }

    /**
     * OCR'ın bozduğu köşeli parantezleri onarır (çeviriden ÖNCE, tüm zincir için):
     *  "Lgroup chanting] All hail" -> "[group chanting] All hail"
     *  "|chanting continues] -[suspenseful music playingl" -> "[chanting continues] -[suspenseful music playing]"
     *  "yelps, groans]" -> "[yelps, groans]"
     */
    fun repair(text: String): String {
        var t = text
        // Açılış parantezi I, l, L, | okunmuş
        t = t.replace(Regex("(^|\\s|-)[IlL|]\\s?([a-z][a-z ,'’-]*[\\])])")) { m ->
            if (isSoundWords(m.groupValues[2].dropLast(1))) m.groupValues[1] + "[" + m.groupValues[2] else m.value
        }
        // Açılış hiç yok: "yelps, groans]"
        Regex("^([a-z][a-z ,'’-]*)[\\])]").find(t)?.let { m ->
            if (isSoundWords(m.groupValues[1])) t = "[" + t
        }
        // Kapanış "l" okunmuş ya da hiç yok (satır sonunda)
        Regex("\\[([a-z][a-z ,'’-]*)$").find(t)?.let { m ->
            val inner = m.groupValues[1].trimEnd()
            val lastTok = inner.substringAfterLast(' ')
            val fixed = if (lastTok.endsWith("l") && lastTok.dropLast(1).lowercase() in words) inner.dropLast(1) else inner
            if (isSoundWords(fixed)) t = t.substring(0, m.range.first) + "[" + fixed + "]"
        }
        // İki parantez de yok, açılış I/l/L okunmuş: "Lguitar music playing" -> "[guitar music playing]"
        Regex("^[IlL|]([a-z][a-z ,'’-]*)$").find(t.trim())?.let { m ->
            if (isSoundWords(m.groupValues[1])) t = "[" + m.groupValues[1].trimEnd() + "]"
        }
        // Açılış "{" ya da "(" okunmuş: "{chanting continues]", "(snaps]"
        // BÜYÜK harf de kabul: konuşmacı adı. 9.63 kaydında "{Barbara] Corrigan?"
        // yalnız küçük harf arandığı için onarılmıyor, etiket tanınmıyordu.
        // "(" gerçek metinde "{"den çok daha sık geçiyor, o yüzden koşulsuz
        // değiştirmiyoruz: içerik ses sözcüklerinden oluşmalı. Yoksa
        // "(see you later)" gibi sıradan bir parantez etiket sanılırdı.
        t = t.replace(Regex("([{(])([a-z][a-z ,'’-]*)(?=[\\])])")) { m ->
            if (isSoundWords(m.groupValues[2])) "[" + m.groupValues[2] else m.value
        }
        t = t.replace(Regex("(^|\\s|-)[{(](?=\\p{Lu}[\\p{L}'’.]*(?: \\p{Lu}[\\p{L}'’.]*)?])")) { it.groupValues[1] + "[" }

        // Bozuk açılıştan SONRA BOŞLUK: "I shrieks, whimpers" -> "[shrieks, whimpers]"
        // Yukarıdaki kurallar boşluksuz hâli ("Ishrieks") yakalıyordu; OCR araya
        // boşluk koyunca ses etiketi diyalog sanılıp çevriliyordu:
        //   "I shrieks, whimpers" -> "Çığlık atıyorum, kısımlar"
        Regex("^[IlL|]\\s+([a-z][a-z ,'’-]*?)[l]?$").find(t.trim())?.let { m ->
            val inner = m.groupValues[1].trimEnd()
            if (isSoundWords(inner)) t = "[" + inner + "]"
        }
        // Konuşmacı etiketinin kapanışı ")" okunmuş: "[Batman) What..." -> "[Batman] What..."
        t = t.replace(Regex("^\\[(\\p{Lu}[\\p{L}'’.]*(?: \\p{Lu}[\\p{L}'’.]*)?)\\)"), "[$1]")
        // Satır sonundaki etiket: iki parantez de bozuk "-lunintelligible strugglingl" -> "-[unintelligible struggling]"
        Regex("(\\s|-)[IlL|]([a-z][a-z ,'’-]*?)l$").find(t)?.let { m ->
            val inner = m.groupValues[2].trimEnd()
            if (isSoundWords(inner)) t = t.substring(0, m.range.first) + m.groupValues[1] + "[" + inner + "]"
        }
        // Kapanış ")" okunmuş: "[pained gasp)" -> "[pained gasp]"
        t = t.replace(Regex("\\[([a-z][a-z ,'’-]*)\\)")) { "[" + it.groupValues[1] + "]" }
        return t
    }

    /** Metin tamamen ses etiketlerinden oluşuyorsa Türkçesini döner, değilse null. */
    fun translate(text: String): String? {
        var t = text.trim()
        if (t.isEmpty()) return null
        val realOpen = t.first() in "[({"
        // ÖZGÜN metinde köşeli parantez var mıydı? Aşağıdaki "l -> [" kuralı
        // ("Igunshot]" gibi okumalar için) sıradan bir kelimeyi de bozabiliyor
        // ("later)" -> "[ater)"), o yüzden karar özgün metne göre veriliyor.
        val kosegeliVar = t.contains('[')
        // OCR: "Igun cocks]" / "lgunshot)" -> "[gun cocks]" / "[gunshot)"
        //
        // KOŞULA BAĞLI. Önceden koşulsuzdu ve İngilizcenin en sık kelimesini
        // bozuyordu: "It's", "I'm", "I'll" baştaki "I"sı köşeli paranteze
        // dönüşüyordu. Kullanıcının 11 Kasım 9.73 kaydından:
        //   "[whispering] It's perfect."  ->  "[fısıldıyor] [ses efekti]"
        // ("It's perfect." -> "[t's perfect." -> hiçbir ses sözcüğü yok ->
        //  "ses efekti"). Artık yalnız ardından gelen metin GERÇEKTEN ses
        // sözcüklerinden oluşuyorsa parantez sayılıyor.
        t = Regex("(^|\\s)[Il|](?=[a-z])").replace(t) { m ->
            val kalan = t.substring(m.range.last + 1)
            val ic = kalan.takeWhile { it !in "])}" }
            if (isSoundWords(ic.trim())) m.groupValues[1] + "[" else m.value
        }
        val hasClose = t.any { it in "])}" }
        if (!realOpen && !hasClose) {
            // "Igrunt, groan" -> iki parantez de okunmamış; tüm kelimeler ses kelimesiyse etiket say.
            if (!t.startsWith("[")) return null
            val inner = t.substring(1)
            if (inner.isBlank() || inner.any { it.isUpperCase() }) return null
            val toks = inner.lowercase().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            if (toks.isEmpty() || toks.any { it.trim('\'', '.') !in words && it !in skip }) return null
            return "[" + translateContent(inner) + "]"
        }
        val tags = tagRe.findAll(t).toList()
        if (tags.isEmpty()) return null
        if (tagRe.replace(t, "").trim('-', '–', ' ').isNotBlank()) return null
        val out = ArrayList<String>()
        for (m in tags) {
            val content = m.groupValues[1].trim()
            if (content.isEmpty()) return null
            // "[Dez groaning]" -> baştaki isim korunur, gerisi ses etiketi
            // Ad BİRDEN FAZLA kelime olabilir: "[Space Racer grunts]",
            // "[Infinity Ray powering up]". Küçük harfli kuyruk ses kısmıdır.
            val nameM = AD_SES.find(content)
            if (nameM != null && nameM.groupValues[2].none { it.isUpperCase() }) {
                val kuyruk = nameM.groupValues[2].trim()
                val adlar = nameM.groupValues[1].trim().replace(" and ", " ve ")
                val rol = rolKuyrugu(kuyruk)
                if (rol != null) { out += "[$adlar$rol]"; continue }
                val fiil = ozneliFiil[kuyruk.lowercase().trimEnd('l')] ?: ozneliFiil[kuyruk.lowercase()]
                out += "[" + adlar + " " + (fiil ?: translateContent(kuyruk)) + "]"
                continue
            }
            if (content.any { it.isUpperCase() }) return null
            val cevrilen = translateContent(content)
            // NORMAL PARANTEZ belirsizdir: altyazıda "[...]" neredeyse her zaman
            // ses etiketi, "(...)" ise sıradan cümle de olabiliyor. İçinden
            // HİÇBİR ses sözcüğü tanınmadıysa bu bir ses etiketi değildir;
            // null dönüp satırın normal çeviri yoluna gitmesini sağlıyoruz.
            // Yoksa "(see you later)" ekrana "[ses efekti]" diye basılıyordu.
            // Köşeli parantezde eski davranış korunuyor: bilinmeyen bir ses
            // etiketi için "[ses efekti]" yazmak, İngilizce bırakmaktan iyi.
            if (cevrilen == "ses efekti" && !kosegeliVar) return null
            out += "[" + cevrilen + "]"
        }
        return out.joinToString(" ")
    }

    /**
     * AD + FİİL etiketinde fiil hâli. words haritası bu sözcükleri İSİM
     * olarak çeviriyor ("groans" -> "inlemeler"), tek başına etiket için
     * doğru: "[groans]" -> "[inlemeler]". Ama önünde özne varsa fiil
     * gerekir: "[Alien groans]" "[Alien inlemeler]" oluyordu.
     */
    // "and" ile bağlı iki ad da kabul: "[Allen and Telia moaning loudly]"
    private val AD_SES = Regex("^((?:\\p{Lu}[\\p{L}'’]+\\s+(?:and\\s+)?){1,3})(\\p{Ll}.*)$")

    /** "[Nolan over implant]", "[Geldarian warrior over speaker]" */
    private val KUYRUK_ARAC = Regex("^(?:(\\w+)\\s+)?(?:over|on|via|through)\\s+(?:the\\s+)?(\\w+)$")

    /** Ayrılma hâli eki: implant -> implanttan, hoparlör -> hoparlörden. */
    private fun ayrilmaEki(kelime: String): String {
        val k = kelime.lowercase()
        val sonUnlu = k.lastOrNull { it in "aeıioöuü" } ?: 'a'
        val ince = sonUnlu in "eiöü"
        val sert = k.lastOrNull() in listOf('p', 'ç', 't', 'k', 'f', 'h', 's', 'ş')
        return (if (sert) "t" else "d") + (if (ince) "en" else "an")
    }

    /**
     * Konuşmacı etiketinin küçük harfli kuyruğu SES değil, ROL / ARAÇ ise.
     * "over" ses haritasında "eşliğinde" demek (müzik için); o yüzden
     * "[Allen over implant]" "[Allen eşliğinde]" oluyordu.
     *   "over implant"          -> ", implanttan"
     *   "warrior"               -> " savaşçı"
     *   "warrior over speaker"  -> " savaşçı, hoparlörden"
     */
    private fun rolKuyrugu(kuyruk: String): String? {
        val t = kuyruk.lowercase().trim().trimEnd(')', ']')
        roller[t]?.let { return " $it" }
        val m = KUYRUK_ARAC.find(t) ?: return null
        val arac = araclar[m.groupValues[2]] ?: return null
        val rolSoz = m.groupValues[1]
        val rol = if (rolSoz.isEmpty()) "" else (roller[rolSoz] ?: return null)
        return (if (rol.isEmpty()) "" else " $rol") + ", " + arac + ayrilmaEki(arac)
    }

    private val ozneliFiil: Map<String, String> = mapOf(
        "groans" to "inliyor", "moans" to "inliyor", "grunts" to "homurdanıyor",
        "screams" to "çığlık atıyor", "yelps" to "ciyaklıyor",
        "whimpers" to "sızlanıyor", "pants" to "soluyor", "giggles" to "kıkırdıyor",
        "scoffs" to "burun kıvırıyor",
    )

    private fun translateContent(content: String): String {
        // ROL etiketi mi? "[provost]" ses değil, KİŞİ. Ses sözcüğü
        // aramadan önce bakılıyor; yoksa "[ses efekti]" oluyor.
        rolCevir(content)?.let { return it }
        // "groans, heavy breathing" -> her virgüllü parça ayrı, araya virgül
        val parts = content.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            val tr = parts.map { translatePart(it) }.filter { it != "ses efekti" }
            return if (tr.isEmpty()) "ses efekti" else tr.joinToString(", ")
        }
        return translatePart(content)
    }

    // Kelime kelime çevrilemeyen kalıplar.
    private val phrases = mapOf(
        // Standalone closed-caption cues, not full spoken dialogue overrides.
        "insects trilling" to "cırcır böcekleri ötüyor",
        "insects triling" to "cırcır böcekleri ötüyor",
        "deep sigh" to "derin bir iç çekiş",
        "crickets chirping" to "cırcır böcekleri ötüyor",
        "leaves rustling" to "yapraklar hışırdıyor",
        "distorted voice" to "bozulmuş ses",
        "no audible dialogue" to "konuşma duyulmuyor",
        "no dialogue" to "konuşma yok",
        "inaudible" to "anlaşılmıyor",
        "indistinct" to "anlaşılmıyor",
        "indistinct chatter" to "anlaşılmayan konuşmalar",
        "indistinct conversation" to "anlaşılmayan konuşma",
        "indistinct talking" to "anlaşılmayan konuşmalar",
        "speaking foreign language" to "yabancı dilde konuşuyor",
        "speaking spanish" to "İspanyolca konuşuyor",
        "silence" to "sessizlik",
        // Yaygın kapalı altyazı ses işaretleri: özel bir oyuna/video repliğine bağlı değil.
        "both groaning" to "ikisi de inliyor",
        "both grunting" to "ikisi de homurdanıyor",
        "both groans" to "ikisi de inliyor",
        "tires squeal" to "lastikler gıcırdıyor",
        "tires squealing" to "lastikler gıcırdıyor",
        "brakes screeching" to "frenler gıcırdıyor",
        "footsteps thudding" to "tok ayak sesleri",
        "horn honking" to "korna çalıyor",
        "truck horn honking" to "kamyon kornası çalıyor",
        "fading groan" to "giderek azalan inleme",
        "engine revving" to "motor devirleniyor",
        "car engine running" to "araba motoru çalışıyor",
        "engine running" to "motor çalışıyor",
        "both gasp" to "ikisinin de nefesi kesiliyor",
        "facetious shock" to "sahte şaşkınlık",
        "panting continues" to "soluma devam ediyor",
        "chanting continues" to "tezahürat devam ediyor",
        "group chanting" to "grup tezahürat ediyor",
        "crowd chanting" to "kalabalık tezahürat ediyor",
        "man panting" to "adam soluyor",
        "woman panting" to "kadın soluyor",
        "pained gasp" to "acıyla nefesi kesiliyor",
        "smoke hissing" to "duman tıslıyor",
        "grunts and groans" to "homurtular ve inlemeler",
        "groans and grunts continue" to "inlemeler ve homurtular sürüyor",
        "fighting grunts and groans" to "dövüş sesleri, homurtular ve inlemeler",
        // "buzzing" tek başına "titriyor" demek (telefon); kapı için yanlış.
        "electricity buzzing" to "elektrik cızırtısı",
        "electrical buzzing" to "elektrik cızırtısı",
        "electric buzzing" to "elektrik cızırtısı",
        "soft buzzing" to "hafif vızıltı",
        "phone buzzing" to "telefon titriyor",
        "gate buzzing" to "kapı vızıldıyor",
        "buzzer sounds" to "zil çalıyor",
        "door buzzing" to "kapı vızıldıyor",
        "music sting" to "müzik vurgusu",
        "brakes screech" to "frenler gıcırdıyor",
        "tires screeching" to "lastikler gıcırdıyor",
        "pained yells" to "acı dolu bağırışlar",
        "yells grunts" to "bağırışlar ve homurtular",
        // İsim + fiil: "thuds" tek başına isim ("gümlemeler"), burada fiil.
        "body thuds" to "ceset gümlüyor",
        "bodies thud" to "cesetler gümlüyor",
        "short breaths" to "kısa kısa nefesler",
        "children in unison" to "çocuklar hep bir ağızdan",
        "crowd in unison" to "kalabalık hep bir ağızdan",
        // "gasping breath" ikisi de nefes: "nefes nefese nefes" cikiyordu.
        "sharp gasping breath" to "keskin bir nefes",
        "gasping breath" to "kesik nefes",
        "sharp breath" to "keskin nefes",
        "alert tone blaring" to "uyarı sesi çalıyor",
        "alert tone" to "uyarı sesi",
        "haunting vocalizing" to "ürkütücü ses",
        "haunting vocalizing over atmospheric music" to "atmosferik müzik eşliğinde ürkütücü ses",
        "vocalizing over atmospheric music" to "atmosferik müzik eşliğinde ses",
        "loud thump" to "yüksek bir gümleme",
        "creature yowling" to "yaratık uluyor",
        "creature mewls" to "yaratık mırlıyor",
        "curtain opens" to "perde açılıyor",
        "intriguing music playing" to "merak uyandıran müzik çalıyor",
        // "softly"/"quietly" skip listesinde: tek basina dusuyor, kalip olarak yaziliyor.
        "moans softly" to "hafifçe inliyor",
        "groans softly" to "hafifçe inliyor",
        "groans quietly" to "hafifçe inliyor",
        "speaks softly" to "hafifçe konuşuyor",
        "sighs softly" to "hafifçe iç çekiyor",
        // 9.75: sıfat+fiil Türkçede tuhaf duruyor ("metalik gıcırdıyor"),
        // isim tamlaması doğru: kalıp olarak yazılıyor.
        "radio static" to "telsiz cızırtısı",
        "moaning loudly" to "yüksek sesle inliyor",
        "bed banging rhythmically" to "yatak ritmik şekilde çarpıyor",
        "beetles screeching" to "böcekler ciyaklıyor",
        "powering up" to "güçleniyor", "powering down" to "kapanıyor",
        "metallic creaking" to "metal gıcırtısı",
        "metallic clanking" to "metal takırtısı",
        "metallic screeching" to "metal çığlığı",
        "tracks squeaking" to "raylar gıcırdıyor",
        "belabored breathing" to "zorlu nefes alışı",
        "exciting music playing" to "heyecanlı müzik çalıyor",
        "emotional music playing" to "duygusal müzik çalıyor"
    )

    /**
     * Parantezi düşmüş ses etiketi mi?
     *
     * OCR köşeli parantezleri yiyebiliyor. Kullanıcının 7 Kasım kaydından:
     *   "suspenseful music continues" -> "Süslü müzik devam ediyor"
     *   "fighting grunts and groans"  -> "Grunts ve ingilizlerle savaşmak"
     * Oysa parantezli hâlleri doğru çevriliyor.
     *
     * Temkinli davranıyoruz: küçük harfle başlamalı, noktalama ile bitmemeli,
     * en çok 6 kelime olmalı ve TÜM kelimeleri ses etiketi sözlüğünde bulunmalı.
     * Böylece "The music continues" gibi gerçek bir replik etiket sanılmaz.
     */
    fun parantezsizEtiket(text: String): Boolean {
        val s = text.trim()
        if (s.isEmpty() || s.first().isUpperCase()) return false
        if (s.last() in ".!?[]") return false
        if (s.contains('[') || s.contains(']')) return false
        val tokens = s.lowercase().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (tokens.size !in 2..6) return false
        val key = tokens.joinToString(" ") { it.trim('\'', '-', '.') }
        if (phrases.containsKey(key)) return true
        var bilinen = 0
        for (raw in tokens) {
            val w = raw.trim('\'', '-', '.')
            if (w in skip) continue
            if (w !in words) return false
            bilinen++
        }
        return bilinen >= 2
    }

    /**
     * ROL ETİKETLERİ — adsız konuşmacı.
     *
     * Altyazıda adı olmayan konuşmacı KÜÇÜK harfli rolle yazılıyor:
     * "[provost]", "[reporter]", "[man on radio]". inlineTag küçük harfli
     * etiketi SES etiketi saydığı için bunlar "[ses efekti]" oluyordu.
     * Kullanıcının 11 Kasım kaydından: "[provostl] Indeed! ..." ->
     * "[ses efekti] Gerçekten! ...". Artık rol olarak çevriliyor.
     */
    private val roller: Map<String, String> = mapOf(
        "delegate" to "delege", "representative" to "temsilci",
        "provost" to "rektör", "dean" to "dekan", "principal" to "müdür",
        "reporter" to "muhabir", "anchor" to "sunucu", "announcer" to "spiker",
        "narrator" to "anlatıcı", "interviewer" to "muhabir",
        "man" to "adam", "woman" to "kadın", "boy" to "çocuk", "girl" to "kız",
        "men" to "adamlar", "women" to "kadınlar", "child" to "çocuk",
        "doctor" to "doktor", "nurse" to "hemşire", "officer" to "memur",
        "guard" to "gardiyan", "driver" to "şoför", "pilot" to "pilot",
        "soldier" to "asker", "captain" to "kaptan", "operator" to "operatör",
        "teacher" to "öğretmen", "professor" to "profesör", "student" to "öğrenci",
        "judge" to "yargıç", "lawyer" to "avukat", "waiter" to "garson",
        "clerk" to "memur", "receptionist" to "resepsiyonist",
        "dispatcher" to "telsiz görevlisi", "computer" to "bilgisayar",
        "voice" to "ses", "both" to "ikisi birden",
        "warrior" to "savaşçı", "commander" to "komutan", "scientist" to "bilim insanı",
        "technician" to "teknisyen", "villager" to "köylü", "elder" to "yaşlı",
    )

    /**
     * "X on radio" bileşiğindeki ARAÇ adları. roller haritasından ayrı
     * tutuluyor: "[radio]" tek başına bir SES etiketidir ("telsiz"), kişi
     * değil. İkisi aynı haritada olunca rolCevir önce çalıştığı için bare
     * "[radio]" -> "radyo" oluyordu ve words'teki "telsiz" eziliyordu.
     */
    private val araclar: Map<String, String> = mapOf(
        "radio" to "telsiz", "tv" to "televizyon", "phone" to "telefon",
        "intercom" to "anons", "speaker" to "hoparlör", "comms" to "telsiz",
        "implant" to "implant", "pa" to "anons",
    )

    /** Etiket bir ROL mü? "provost" / "man on radio" -> rol çevirisi. */
    private fun rolCevir(content: String): String? {
        val t = content.lowercase().trim()
        roller[t]?.let { return it }
        // Numbered, anonymous speaker labels are roles, never sound effects.
        Regex("^(representative|delegate)\\s+([1-9][0-9]?)$").matchEntire(t)?.let { match ->
            val role = roller[match.groupValues[1]] ?: return@let
            return "$role ${match.groupValues[2]}"
        }
        // OCR "]" yerine "l" okuyor: "[provostl]" -> "provost"
        if (t.endsWith("l")) roller[t.dropLast(1)]?.let { return it }
        // "man on radio", "woman over the phone", "reporter on tv"
        val m = ROL_ARACLI.find(t) ?: return null
        val kisi = roller[m.groupValues[1]] ?: return null
        val arac = araclar[m.groupValues[2]] ?: return null
        return arac + bulunmaEki(arac) + "ki " + kisi
    }

    /**
     * Bulunma hâli eki: ünlü uyumu + sert sessiz benzeşmesi.
     * "telsiz" -> "telsizde" (ince), "televizyon" -> "televizyonda" (kalın),
     * "anons" -> "anonsta" (s sert). Elle "daki" yazınca "telsizdaki"
     * gibi bozuk çıkıyordu.
     */
    private fun bulunmaEki(kelime: String): String {
        val k = kelime.lowercase()
        val sonUnlu = k.lastOrNull { it in "aeıioöuü" } ?: 'a'
        val ince = sonUnlu in "eiöü"
        val sertSessiz = k.lastOrNull() in listOf('p', 'ç', 't', 'k', 'f', 'h', 's', 'ş')
        return when {
            sertSessiz && ince -> "te"
            sertSessiz -> "ta"
            ince -> "de"
            else -> "da"
        }
    }

    private val ROL_ARACLI = Regex("^(\\w+)\\s+(?:on|over)\\s+(?:the\\s+)?(\\w+)$")

    /** Aynı sözcüğün sözlükte bulunan başka bir çekimi (-ing / -s / -es / -ed). */
    private fun cekimVaryanti(w: String): String? {
        val c = ArrayList<String>()
        if (w.length > 5 && w.endsWith("ing")) {
            val k = w.dropLast(3)                       // giggl-ing
            c += listOf(k + "s", k + "es", k + "e", k + "les", k)
            if (k.length > 2 && k[k.length - 1] == k[k.length - 2]) c += k.dropLast(1) + "s"
        }
        if (w.length > 4 && w.endsWith("es")) c += w.dropLast(2) + "ing"
        if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            val k = w.dropLast(1)                       // sniffle-s
            c += listOf(k, k + "ing")
            if (k.endsWith("e")) c += k.dropLast(1) + "ing"
        }
        if (w.length > 4 && w.endsWith("ed")) {
            val k = w.dropLast(2)                       // jangl-ed
            c += listOf(k + "s", k + "es", k + "ing", k.dropLast(1) + "ing")
        }
        return c.firstOrNull { it != w && it in words }
    }

    private fun translatePart(content: String): String {
        val tokens = content.lowercase().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        run {
            val key = tokens.joinToString(" ") { it.trim('\'', '-', '.') }
            phrases[key]?.let { return it }
            if (key.endsWith("l")) phrases[key.dropLast(1)]?.let { return it }   // "dialoguel" (OCR: ] -> l)
        }
        // "<X> continues" -> "<X'in isim hâli> devam ediyor"
        // ("bağırıyor devam ediyor" değil, "bağırma devam ediyor")
        if (tokens.size in 2..4 && tokens.last().trim('\'', '-', '.') in
            setOf("continues", "continue", "continuing")
        ) {
            val onek = tokens.dropLast(1).map { it.trim('\'', '-', '.') }
            var ad = onek.lastOrNull()?.let { devamAdi[it] }
            if (ad != null) {
                // "gerilim müziği" (isim tamlaması) ama "dramatik müzik" (sıfat)
                if (ad == "müzik" && onek.size >= 2 && onek[onek.size - 2] in adjectives) {
                    ad = "müziği"
                }
                val sifat = onek.dropLast(1).mapNotNull { words[it] }.joinToString(" ")
                return (if (sifat.isBlank()) ad else "$sifat $ad") + " devam ediyor"
            }
        }
        val res = ArrayList<String>()
        val zarflar = ArrayList<String>()
        var known = 0
        for ((i, raw) in tokens.withIndex()) {
            var w = raw.trim('\'', '-', '.')
            if (w !in words && w.endsWith("l") && w.dropLast(1) in words) w = w.dropLast(1) // "playingl" -> "playing"
            if (w !in words) {
                // OCR harf karışıklığı: "booning" -> "booming", "beepirng" -> "beeping"
                val aday = sequenceOf(w.replace('n', 'm'), w.replace("rn", ""), w.replace("rn", "m"), w.replace("ir", "i"))
                    .plus(w.indices.asSequence().filter { w[it] == 'n' }.map { w.substring(0, it) + "m" + w.substring(it + 1) })
                    .firstOrNull { it in words }
                if (aday != null) w = aday
            }
            // 9.97: GENEL ÇEKİM EŞLEMESİ. Sözlükte "giggles" var ama "giggling"
            // yok, "sniffling" var ama "sniffles" yok: aynı sesin başka çekimi
            // bilinmiyor diye etiket "[ses efekti]" oluyordu. Bilinen bir çekimi
            // varsa o kullanılır; yeni oyunlarda da tek tek eklemek gerekmez.
            if (w !in words) cekimVaryanti(w)?.let { w = it }
            if (w in skip) continue
            val tr = words[w]
            if (tr == null) { eksikYaz(w); continue }
            known++
            val metin = if (w == "music" && i > 0 && tokens[i - 1].trim('\'') in adjectives) "müziği" else tr
            // Zarflar ayrı toplanıyor; aşağıda fiilin ÖNÜNE konuyor.
            if (w in ZARF) zarflar += metin else res += metin
        }
        if (known == 0) return "ses efekti"
        // Zarf, kalan son öğenin (fiilin) hemen önüne girer. Başa koymak
        // özneli etiketlerde yanlış olurdu: "acıyla öğrenci nefesi kesiliyor".
        for (z in zarflar) res.add(maxOf(0, res.size - 1), z)
        return res.joinToString(" ")
    }
}

/**
 * Şeritteki OCR satırlarından ALTYAZI bloğunu seçer.
 * Altyazının üstünde başka bir yazı (X-Ray oyuncu listesi, görev yazısı) olabilir;
 * en alttaki bloğu alıp, araya BELİRGİN boşluk girince duruyoruz.
 * Boşluk = üstteki satırın ALT kenarı ile alttakinin ÜST kenarı arası; satır yüksekliğine göre ölçülür.
 * (Eskiden üst kenarlar arası ölçülüyordu; iki satırlı her altyazının ilk satırı atılıyordu.)
 */
object SubtitleBlocks {
    class Line(val top: Int, val bottom: Int, val text: String)

    fun bottomBlock(lines: List<Line>): List<Line> {
        if (lines.size <= 1) return lines
        val sorted = lines.sortedBy { it.top }
        val out = ArrayList<Line>()
        var below = sorted.last()
        out += below
        for (i in sorted.size - 2 downTo 0) {
            val l = sorted[i]
            val lineH = maxOf(1, maxOf(l.bottom - l.top, below.bottom - below.top))
            val gap = below.top - l.bottom
            if (gap > lineH * 0.9f) break          // arada bir satırdan fazla boşluk: başka blok
            out += l
            below = l
            if (out.size >= 3) break               // altyazı en fazla üç satırdır
        }
        return out.reversed()
    }
}

/** Yerleşik çeviri (ML Kit) cevap vermediğinde fırlatılır; dışarıya null olarak döner. */
class OfflineUnavailable : Exception()

/**
 * Uygulamanın çevrim dışı çevirisi. CaptureService şöyle kullanır:
 *   OfflineFix.translate(metin) { fast?.translate(it) }
 */
class OfflineFixEngine(
    private val glossary: SubtitleGlossary = SubtitleGlossary.default(SubtitleGlossary.GAME_ENTRIES),
    private val cacheSize: Int = 300
) {

    private val tr = Locale.forLanguageTag("tr-TR")
    private val sentenceSplit = Regex("(?<=[.!?…])\\s+")
    private val trailingPunct = Regex("[.!?…]+$")
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > cacheSize
    }

    // Yerleşik çeviri bazen küfürü çevirmeden bırakır; yerinde Türkçeleştir.
    private val leftovers = listOf(
        Regex("\\bmotherfucking\\b", RegexOption.IGNORE_CASE) to "siktiğimin",
        Regex("\\bmotherfucker\\b", RegexOption.IGNORE_CASE) to "orospu çocuğu",
        Regex("\\bfuck(?:ing|in'?)\\b", RegexOption.IGNORE_CASE) to "siktiğimin",
        Regex("\\bfuck\\b", RegexOption.IGNORE_CASE) to "siktir",
        Regex("\\bbullshit\\b", RegexOption.IGNORE_CASE) to "saçmalık",
        Regex("\\bshit\\b", RegexOption.IGNORE_CASE) to "bok",
        Regex("\\basshole\\b", RegexOption.IGNORE_CASE) to "pislik herif",
        Regex("\\bbastard\\b", RegexOption.IGNORE_CASE) to "piç",
        Regex("\\bgoddamn(?:ed)?\\b", RegexOption.IGNORE_CASE) to "lanet",
        Regex("\\bdamn\\b", RegexOption.IGNORE_CASE) to "lanet"
    )

    private var mlFn: suspend (String) -> String? = { null }

    /**
     * 10.10 — GÜÇLÜ MOTOR (Google Çeviri). Ayarlıysa cümle ONA OLDUĞU GİBİ
     * gider: argo düzeltme ve küfür kalkanı atlanır. Bu iki katman zayıf
     * telefon motoru için yazılmıştı ve Google'ın anladığı deyimleri bozuyordu
     * (13 Kasım 10.9 kaydı):
     *   "we're in some deep fucking shit" -> "derin bir bok, amına koyayım"
     *   "This is so fucked!"              -> "Bu çok siktir!"
     *   "Fuck, oh no!"                    -> "Siktir git, oh hayır!"
     * Google cevap vermezse ya da korunan adı kaybederse eski yol çalışır.
     */
    @Volatile var gucluFn: (suspend (String) -> String?)? = null

    /** "Sözlüğüm" (kullanıcının kendi satırları): hazır sözlüğün ÖNÜNDE gelir. */
    @Volatile private var user: SubtitleGlossary? = null

    fun clearCache() = synchronized(cache) { cache.clear() }

    fun setUserEntries(text: String) {
        user = if (text.isBlank()) null else SubtitleGlossary.parse(text)
        synchronized(cache) { cache.clear() }
    }

    private fun look(text: String): String? = user?.lookup(text) ?: glossary.lookup(text)
    private fun isName(text: String): Boolean = user?.isName(text) == true || glossary.isName(text)
    private fun startsWithName(text: String): Boolean = user?.startsWithName(text) == true || glossary.startsWithName(text)

    private val trChars = Regex("[çğışöüÇĞİŞÖÜ]")
    // "(?U)" YOK: Android'in ICU motoru o bayrağı tanımaz ve çöker; ICU'da
    // "\\b" zaten Unicode farkında olduğu için "Çok güzel" doğru eşleşir.
    private val trWords = Regex("\\b(ve|bir|bu|için|daha|fazla|ile|değil|ama|çok|gibi|olan|nedeniyle|edinin|ekran|uygulama)\\b", RegexOption.IGNORE_CASE)

    /** Ekrandaki yazı zaten Türkçeyse (telefon uyarısı, Türkçe altyazı) çevrilmez. */
    fun isAlreadyTurkish(text: String): Boolean =
        trChars.findAll(text).count() >= 2 || trWords.findAll(text).count() >= 2

    private val videoMeta = Regex("\\b\\d+ (saniye|dakika|saat|gün|hafta|ay|yıl) önce\\b|\\b\\d+ (seconds?|minutes?|hours?|days?|weeks?|months?|years?) ago\\b|\\b\\d+([,.]\\d+)? ?(mn|b|bin|k|m) (görüntüleme|views?)\\b|görüntüleme|\\bsubscribe\\b|abone ol", RegexOption.IGNORE_CASE)
    private val copyright = Regex("(©|\\(c\\)|\\ball rights reserved\\b|\\b(19|20)\\d{2}\\b.*\\b(llc|inc|ltd|entertainment|studios?|games)\\b)", RegexOption.IGNORE_CASE)
    private val creditLine = Regex("^[\\p{Lu}(][\\p{L}'’.()]*( [\\p{Lu}'’\"][\\p{L}'’\".]*)* ?[-–] ?[\\p{Lu}'’\"][\\p{L}'’\".]*( [\\p{L}'’\".]+)*$")

    // Jeneriğin altyazıyla aynı satıra karışması: "Eric Morgan Stuart - CommissionerrS. Landry approached..."
    // Uygulama/menü adlarından ibaret satırlar: "WhatsApp Instagram", "prime prime", "Profilleri dzenle", "Bölümler"
    private val appMenu = Regex("(?i)^(?:(?:whatsapp|instagram|facebook|youtube|netflix|prime|prime video|spotify|tiktok|telegram|twitter|disney\\+?|hbo|max|mubi|exxen|blutv|gain|tabii)[\\s,·|]*)+$|" +
        "^(?:profilleri d\\S*zenle|b\\S*l\\S*mler|ana sayfa|izlemeye devam et|fragman|bölüm \\d+|sezon \\d+)$")
    private val creditMerged = Regex("^\\p{Lu}\\p{Ll}+(?: (?:\\p{Lu}\\.|\\p{Lu}\\p{Ll}+)){1,3} ?[-–] ?\\p{Lu}\\p{Ll}+")

    /** Altyazı olmayan satır: Türkçe arayüz yazısı, X-Ray / oyuncu kartı, telif, "Ad Soyad - Rol" jeneriği. */
    fun isNoiseLine(line: String): Boolean {
        val t = line.trim()
        if (creditMerged.containsMatchIn(t)) return true
        // Kısa, TAMAMI BÜYÜK HARF, noktalamasız satır: uygulama/kanal/kutucuk adı ("SEND FILES", "LIMBO").
        // Altyazı büyük harfle yazılsa bile genelde noktalama taşır ("NO! GET DOWN!").
        run {
            val words = t.split(Regex("\\s+")).filter { w -> w.any { it.isLetter() } }
            val letters = t.filter { it.isLetter() }
            if (words.size in 1..4 && letters.length >= 3 && letters.all { it.isUpperCase() } &&
                t.none { it in ".!?,…'\"-" }) return true
        }
        if (isAlreadyTurkish(t)) return true
        if (appMenu.matches(t)) return true
        if (t.contains("x-ray", ignoreCase = true)) return true
        if (videoMeta.containsMatchIn(t)) return true
        if (copyright.containsMatchIn(t)) return true
        if (creditLine.matches(t)) {
            // Her kelime büyük harfle başlıyorsa (isim listesi) jenerik say.
            val words = t.split(' ', '-', '–').filter { w -> w.any { it.isLetter() } }
            if (words.size >= 3 && words.count { w -> w.first { it.isLetter() }.isUpperCase() } >= words.size - 1) return true
        }
        return false
    }

    // Başta en çok üç kelimelik AD olabilir ("[Nolan sighs]"); ardından
    // küçük harfli ses kısmı ŞART, yoksa "[Allen]" gibi çıplak konuşmacı
    // etiketi ses etiketi sanılırdı.
    private val inlineTag = Regex("\\[(?:\\p{Lu}[\\p{L}'’]+ (?:and )?){0,3}[a-z][a-z ,'’-]*]")
    private val openSpeaker = Regex("^\\[(\\p{Lu}[\\p{Ll}'’]+)\\s+(?=\\p{Lu})(?![^\\]]*])")
    private val tagPrefix = Regex("^\\[([^\\]]{1,30})]\\s*")

    private val languages = mapOf(
        "spanish" to "İspanyolca", "french" to "Fransızca", "german" to "Almanca", "italian" to "İtalyanca",
        "russian" to "Rusça", "japanese" to "Japonca", "chinese" to "Çince", "mandarin" to "Çince",
        "korean" to "Korece", "portuguese" to "Portekizce", "arabic" to "Arapça", "latin" to "Latince",
        "turkish" to "Türkçe", "hindi" to "Hintçe", "polish" to "Lehçe", "ukrainian" to "Ukraynaca"
    )

    /** "In Spanish" (altyazıda konuşmanın dilini belirten etiket) -> "İspanyolca"; değilse null. */
    fun languageLabel(name: String): String? {
        val m = Regex("^\\s*(?:in|speaking)\\s+(\\p{L}+)\\s*$", RegexOption.IGNORE_CASE).find(name) ?: return null
        return languages[m.groupValues[1].lowercase()]
    }

    /** Yapay zekâya gidecek metin: OCR hataları düzeltilmiş, argo/küfür OLDUĞU GİBİ. */
    fun cleanForAi(text: String): String = OcrTextCleaner.clean(text).ifBlank { text }

    /** Sadece kullanıcının kendi yazdığı kesin çeviri ve ses etiketi önceliklidir. */
    fun lookupUserOrSound(text: String): String? {
        SoundTags.translate(text)?.let { return it }
        val cleaned = OcrTextCleaner.clean(text)
        val punct = trailingPunct.find(cleaned)?.value ?: ""
        val core = cleaned.removeSuffix(punct).trim()
        if (core.isEmpty()) return null
        return user?.lookup(core)?.let { harfiEslestir(core, it) + punct }
    }

    /** Sözlükte tam karşılığı varsa döner (ML Kit'e hiç gitmez). */
    fun lookup(text: String): String? {
        SoundTags.translate(text)?.let { return it }
        val cleaned = OcrTextCleaner.clean(text)
        val punct = trailingPunct.find(cleaned)?.value ?: ""
        val core = cleaned.removeSuffix(punct).trim()
        if (core.isEmpty()) return null
        look(core)?.let { return harfiEslestir(core, it) + punct }
        return Phrases.lookup(core)?.let { harfiEslestir(core, it) + punct }
    }

    /**
     * KARŞILIK, KAYNAĞIN BÜYÜK/KÜÇÜK HARFİNİ İZLER.
     *
     * Altyazı bir cümleyi iki satıra bölüyor ve ikinci satır küçük harfle
     * başlıyor ("on the floors below then coming up the stairs,"). Sözlük
     * karşılığı büyük harfli saklandığı için ikinci satır da büyük harfle
     * başlıyordu. Tersi de oluyordu: karşılığı küçük harfle yazdığım girdiler
     * ("this way = bu taraftan") cümle başında küçük harf kalıyordu.
     *
     * Kural tek: kaynak büyük harfle başlıyorsa çeviri de başlar, küçükse
     * küçük kalır. Böylece sözlükte karşılıkları BÜYÜK harfli saklamak
     * güvenli oluyor; cümle içi parçaları joinParts zaten küçültüyor.
     */
    private fun harfiEslestir(kaynak: String, ceviri: String): String {
        if (ceviri.isEmpty()) return ceviri
        val ilk = kaynak.firstOrNull { it.isLetter() } ?: return ceviri
        val c = ceviri.first()
        if (!c.isLetter()) return ceviri
        return if (ilk.isUpperCase()) {
            if (c.isUpperCase()) ceviri else ceviri.substring(0, 1).uppercase(tr) + ceviri.substring(1)
        } else {
            if (c.isLowerCase()) ceviri else ceviri.substring(0, 1).lowercase(tr) + ceviri.substring(1)
        }
    }

    // İki konuşmacının tek satırda olduğu altyazı: "Why didn't you? -I'm no killer."
    private val dialogTurn = Regex("(?<=[.!?…\\]])\\s*[-–]\\s*(?=[\\[\\p{Lu}'\"])")
    // OCR commonly sees an interrupted turn as "I don't need to- -There's nothing...".
    // The first utterance and the next speaker must be translated separately.
    private val interruptedTurn = Regex("(?<=\\p{L})-\\s+-\\s*(?=[\\[\\p{Lu}'\"])")

    /** ml: yerleşik çeviri; null dönerse (model yok vb.) sonuç da null olur. */
    suspend fun translate(
        text: String,
        preferModel: Boolean = false,
        ml: suspend (String) -> String?
    ): String? {
        mlFn = ml
        SoundTags.translate(text)?.let { return it }
        // Her konuşmacının repliği AYRI çevrilir; birleşince yerleşik çeviri anlamı karıştırıyordu
        // ("Neden sen değildin - Hiçbir katil yok").
        val turns = text.split(interruptedTurn).flatMap { it.split(dialogTurn) }
            .map { it.trim() }.filter { it.isNotEmpty() }
        // PARÇA BAŞARISIZSA SATIRIN TAMAMI ÇÖPE GİTMEZ.
        // Eskiden tek bir replik ya da tek bir ses etiketi çevrilemediğinde
        // "return null" ile bütün satır düşüyordu; ekran bomboş kalıyordu.
        // Artık çevrilemeyen parça olduğu gibi kalıyor, çevrilenler görünüyor.
        // Hiçbir parça çevrilemediyse eskisi gibi null dönüyor.
        if (turns.size > 1) {
            val out = ArrayList<String>()
            var ceviren = 0
            for (turnText in turns) {
                val tr = if (preferModel) translate(turnText, preferModel, ml)
                         else lookup(turnText) ?: translate(turnText, preferModel, ml)
                if (tr != null) ceviren++
                out += (tr ?: turnText)
            }
            if (ceviren == 0) return null
            return out.joinToString(" - ")
        }
        // Satır içindeki ses etiketleri: "[laughs] That's the ticket!" -> "[gülüyor] ..."
        val inner = inlineTag.findAll(text).toList()
        if (inner.isNotEmpty() && inlineTag.replace(text, "").any { it.isLetter() }) {
            val out = ArrayList<String>()
            var last = 0
            var ceviren = 0
            for (m in inner) {
                val before = text.substring(last, m.range.first).trim()
                if (before.any { it.isLetter() }) {
                    val tr = translate(before, preferModel, ml)
                    if (tr != null) ceviren++
                    out += (tr ?: before)
                }
                out += (SoundTags.translate(m.value)?.also { ceviren++ } ?: m.value)
                last = m.range.last + 1
            }
            val after = text.substring(last).trim()
            if (after.any { it.isLetter() }) {
                val tr = translate(after, preferModel, ml)
                if (tr != null) ceviren++
                out += (tr ?: after)
            }
            if (ceviren == 0) return null
            return out.joinToString(" ")
        }
        // "[Beckett Put 'em..." (kapanış parantezi okunamamış konuşmacı adı)
        openSpeaker.find(text)?.let { m ->
            val tr = translate(text.substring(m.range.last + 1), preferModel, ml) ?: return null
            return "[" + m.groupValues[1] + "] " + tr
        }
        // "[Matches] Hey..." -> konuşmacı etiketi çevrilmez
        tagPrefix.find(text)?.let { m ->
            val rest = text.substring(m.range.last + 1)
            if (rest.any { it.isLetter() }) {
                val tr = translate(rest, preferModel, ml) ?: return null
                return "[" + m.groupValues[1] + "] " + tr
            }
        }
        val cleaned = OcrTextCleaner.clean(text)
        if (cleaned.isBlank()) return null
        // Önce en güçlü mevcut motordan tam cümle çevirisi istenir.
        // Bu yol yalnız DENGELİ modda etkindir: ek ücretli API için
        // kullanıcının mevcut opt-in ayarı korunur. Bağlama bağlı kısa
        // replikler sabit oyun/sahne tablosuna kilitlenmez.
        // Kullanıcının kendi sözlüğü hâlâ her zaman önceliklidir.
        if (preferModel && gucluFn != null) {
            lookupUserOrSound(cleaned)?.let { return it }
            try {
                val candidate = mlTranslate(cleaned, naturalFirst = true)
                val problem = TranslationQualityGate.check(cleaned, candidate)
                // Küfür yoğunluğu bir *uyarıdır*; doğal İngilizce yapısını
                // otomatik kaba bir Türkçe ekle yamamak yerine çıktı kontrol
                // aşamasında bağımsız motorlarla kıyaslanabilir.
                if (problem == null || problem == TranslationQualityGate.Problem.LOST_PROFANITY)
                    return candidate
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Motor yoksa eski sözlük/yerel motor yoluna düşülür.
            }
        }
        val sentences = cleaned.split(sentenceSplit).filter { it.isNotBlank() }
        // Google açıkken YALNIZ çok cümleli tek altyazıyı parçalama.
        // Tek cümlelerin sözlüğü/hızı önceki sürümdeki gibi kalır.
        // Tam eşleşen kullanıcı sözlüğü bu yolda da önceliklidir.
        // Google başarısızsa aynı mlTranslate eski Firefox/ML Kit'e düşer.
        if (gucluFn != null && sentences.size > 1 && cleaned.length <= 480 &&
            sentences.none { lookup(it) != null }
        ) {
            lookup(cleaned)?.let { return it }
            return try { cachedRun(cleaned) } catch (_: OfflineUnavailable) { null }
        }
        return try {
            if (sentences.size == 1) return cached(sentences[0])
            // Hız: sözlükte olan cümleler anında; olmayan ARDIŞIK cümleler TEK çağrıyla çevrilir.
            val out = ArrayList<String>()
            val run = ArrayList<String>()
            var ceviren = 0
            // Bir cümle çevrilemezse (motor o an cevap vermezse) o cümle İngilizce
            // kalıyor, satırın gerisi görünüyor. Eskiden tek cümlenin hatası
            // bütün satırı siliyordu.
            suspend fun flush() {
                if (run.isEmpty()) return
                val birlesik = run.joinToString(" ")
                out += try {
                    val r = if (run.size == 1) cached(run[0]) else cachedRun(birlesik)
                    ceviren++
                    r
                } catch (e: OfflineUnavailable) {
                    birlesik
                }
                run.clear()
            }
            for (sn in sentences) {
                val hit = lookup(sn)
                if (hit != null) { flush(); out += hit; ceviren++ } else run += sn
            }
            flush()
            if (ceviren == 0) null else out.joinToString(" ")
        } catch (e: OfflineUnavailable) {
            null
        }
    }

    private suspend fun cachedRun(text: String): String {
        // Google engellenince Firefox/ML Kit'in geçici yanıtı dönmüş olabilir.
        // Bu geçici yanıt Google'a aitmiş gibi önbellekte kalmamalı.
        val canCache = gucluFn == null
        if (canCache) synchronized(cache) { cache[text] }?.let { return it }
        val r = mlTranslate(text)
        if (canCache && r.isNotBlank()) synchronized(cache) { cache[text] = r }
        return r
    }

    /**
     * Çeviri motoruna giden TEK kapı. Özel adlar burada kalkan altına alınıyor.
     *
     * Küfrü motordan koruyan bir mekanizma vardı (ProfanityShield yer tutucu
     * koyup sonra geri alıyor), ama ADLAR için yoktu. Kullanıcının 11 Kasım
     * 9.66 kaydında motor tanımadığı özel adı sözcük sanıp çeviriyordu:
     *   "perform the Toolock pul"          -> "Benden ALETİNİ yapmamı ..."
     *   "thrown into a Viltrumite prison"  -> "KAYDIRILMIŞ bir hapishaneye ..."
     *   "because of our friend ship"       -> "Arkadaş GEMİMİZDEN dolayı"
     * Ad yer tutucuya dönüşüp çeviriden sonra AYNEN geri konuyor; motor onu
     * bilinmeyen bir sözcük olarak olduğu gibi geçiriyor.
     */
    private suspend fun base(text: String): String {
        val k = NameShield.shield(text)
        val cevrilen = mlFn(k.text)?.takeIf { it.isNotBlank() } ?: throw OfflineUnavailable()
        return NameShield.restore(cevrilen, k.slots)
    }

    private suspend fun cached(sentence: String): String {
        val canCache = gucluFn == null
        if (canCache) synchronized(cache) { cache[sentence] }?.let { return it }
        val r = translateSentence(sentence)
        if (canCache && r.isNotBlank()) synchronized(cache) { cache[sentence] = r }
        return r
    }

    /**
     * Satırın TAMAMI elle yazılmış kalıplardan karşılanıyor mu?
     *
     * Yan etkisiz: çeviri motoruna hiç gitmez, yalnızca sözlüğe bakar.
     * AiGuard bununla "taslağa güvenilir mi" sorusunu yanıtlıyor.
     *
     * Ayrımın sebebi 11 Kasım 26 dakikalık kaydı: yapay zekâ ELLE YAZILMIŞ
     * karşılıkları bozuyor ("Sağ ol" -> "Teşekkürler", "Hemen içeride" ->
     * "İçeride"), buna karşılık ÇEVİRİ MOTORU çıktısını düzeltiyor
     * ("Bilirsin, tanklar" -> think-tank, "Taşı, Taşı" -> Kımılda).
     * Yani kaynak belli: kalıptan geldiyse dokunma, motordan geldiyse denetle.
     */
    fun tamamenKaliptanMi(text: String): Boolean {
        val cleaned = OcrTextCleaner.clean(text)
        if (cleaned.isBlank()) return false
        var bakildi = false
        for (sn in cleaned.split(sentenceSplit)) {
            val s = sn.trim()
            if (s.isBlank()) continue
            val punct = trailingPunct.find(s)?.value ?: ""
            val core = s.removeSuffix(punct).trim()
            if (core.isEmpty()) continue
            bakildi = true
            if (look(core) != null || Phrases.lookup(core) != null) continue
            val parts = core.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1 &&
                parts.all { look(it) != null || Phrases.lookup(it) != null || isName(it) }
            ) continue
            return false
        }
        return bakildi
    }

    private suspend fun translateSentence(sentence: String): String {
        val punct = trailingPunct.find(sentence)?.value ?: ""
        val core = sentence.removeSuffix(punct).trim()
        if (core.isEmpty()) return sentence

        look(core)?.let { return harfiEslestir(core, it) + punct }
        Phrases.lookup(core)?.let { return harfiEslestir(core, it) + punct }
        // Cümlenin tamamı özel addan ibaretse çevirisi kendisidir.
        // Virgüllü hâli ("Raymond, Raymond.") aşağıdaki parça yolunda
        // zaten korunuyordu, tek adlık cümle ise çeviri motoruna gidip
        // bozuluyordu: "Errnesto." -> motor ne döndürürse o.
        if (isName(core)) return core + punct

        val parts = core.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            val resolved = parts.map { look(it) ?: Phrases.lookup(it) }
            val anyKnown = resolved.any { it != null } || isName(parts[0])
            if (anyKnown) return joinParts(parts, resolved, punct)
        }
        return mlTranslate(core + punct)
    }

    /** argo düzelt -> küfürü koru -> yerleşik çeviri -> küfürü Türkçe koy. */
    private suspend fun mlTranslate(text: String, naturalFirst: Boolean = false): String {
        gucluFn?.let { g ->
            // Google kendi deyim/argo bağlamını çözebiliyor. Burada İngilizceyi
            // yeniden yazmak anlamı ve küfür tonunu bozabiliyor. Google'a
            // özgün OCR-onarılmış cümle gider; yalnız özel adları koruruz.
            // Google başarısızsa ALTTAKİ mevcut yerleşik motorun argo ve
            // küfür kuralları değişmeden çalışmaya devam eder.
            val k = NameShield.shield(GoogleSourcePreparation.prepare(text))
            val r = try { g(k.text) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
            if (!r.isNullOrBlank() && k.slots.all { r.contains(it.token) }) {
                val candidate = fix(NameShield.restore(r, k.slots))
                val recovered = if (!naturalFirst && TranslationQualityGate.check(text, candidate) ==
                    TranslationQualityGate.Problem.LOST_PROFANITY) {
                    // The raw OPUS/Google benchmark is unchanged; only the live
                    // overlay restores the emphatic tone if the engine omitted it.
                    ProfanityShield.addTails(candidate, ProfanityShield.shield(text).tails)
                } else candidate
                // A confidently reversed command is never accepted from the
                // engine. Ask the installed fallback instead.
                if (!TranslationQualityGate.mustReject(TranslationQualityGate.check(text, recovered))) {
                    return recovered
                }
            }
        }
        val norm = SlangNormalizer.normalize(Slang.normalize(GoogleSourcePreparation.prepare(text)))
        val sh = ProfanityShield.shield(norm)
        if (sh.slots.isEmpty()) return ProfanityShield.addTails(fix(base(sh.text)), sh.tails)
        val restored = ProfanityShield.restore(base(sh.text), sh.slots, tr)
        if (restored != null) return ProfanityShield.addTails(fix(restored), sh.tails)
        return ProfanityShield.addTails(fix(base(ProfanityShield.unshield(sh.text, sh.slots))), sh.tails)
    }

    /**
     * Virgülle ayrılmış TEK PARÇA hitaplar: "Hey, man." -> "Selam, dostum."
     *
     * SlangNormalizer'ın "man" kuralı virgül+cümle sonu arıyor; satır parçalara
     * bölününce parçaya "man" tek başına düşüyor ve kural yetişemiyor, çeviri
     * motoru da onu "adam" diye çeviriyordu. 11 Kasım kaydından:
     *   "Hey, man."                    -> "Hey, adam."
     *   "Ah, man, it's been non-stop." -> "Ah, adam, durmadı."
     */
    private val HITAP = mapOf(
        "man" to "dostum", "bro" to "kanka", "brother" to "kardeşim",
        "buddy" to "dostum", "dude" to "ahbap", "bruh" to "kanka",
        "homie" to "kanka", "pal" to "dostum", "mate" to "dostum",
        "bruv" to "kanka", "fam" to "kanka", "boss" to "patron",
    )

    private suspend fun joinParts(parts: List<String>, resolved: List<String?>, punct: String): String {
        val out = ArrayList<String>()
        var i = 0
        while (i < parts.size) {
            // Hitap sözcüğü: cümlenin ilk parçası DEĞİLSE kişiye seslenmedir.
            val hitap = if (i > 0 && resolved[i] == null)
                HITAP[parts[i].lowercase().trim('!', '?', '.', ' ')] else null
            if (hitap != null) {
                out += hitap + (if (i == parts.lastIndex) punct else "")
                i++
                continue
            }
            val known = resolved[i] ?: if (isName(parts[i])) parts[i] else null
            if (known != null) {
                val isGlossary = resolved[i] != null
                var text = if (i > 0 && isGlossary && !startsWithName(known)) lowerFirst(known) else known
                if (i == parts.lastIndex) text += punct
                out += text
                i++
            } else {
                // Bitişik bilinmeyen parçaları BİRLİKTE çevir (bağlam kopmasın).
                var j = i
                while (j < parts.size && resolved[j] == null && !isName(parts[j])) j++
                val run = parts.subList(i, j).joinToString(", ")
                val endsHere = j == parts.size
                val tr = mlTranslate(run + if (endsHere) punct else "")
                val parca = if (endsHere) tr else tr.trimEnd(',', '.', '!', '?', '…', ';', ' ')
                // Cümle ORTASINDAKİ parça: motor her parçayı büyük harfle
                // başlatıyor ("Evet, Bunu kendim buldum", "Merak etme,
                // Arkadaşlar"). Kaynak parça küçük harfle ya da "I" zamiriyle
                // başlıyorsa Türkçesi de küçük başlar; özel ad korunur.
                val kucukBaslar = i > 0 && (run.firstOrNull()?.isLowerCase() == true ||
                    run.startsWith("I ") || run.startsWith("I'") || run.startsWith("I’"))
                out += if (kucukBaslar && !startsWithName(parca)) lowerFirst(parca) else parca
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

/**
 * ÖZEL AD KALKANI.
 *
 * Çeviri motoru tanımadığı büyük harfli kelimeyi sözcük sanıp çeviriyor.
 * Kullanıcının 11 Kasım 9.66 kaydından:
 *   "Toolock"    -> "aletini"      (tool + lock)
 *   "Viltrumite" -> "kaydırılmış"
 *   "friend ship"-> "arkadaş gemisi"  (OCR "friendship"i bölmüş)
 *
 * Çözüm küfür kalkanının aynısı: ad geçici bir yer tutucuya ("Qx1") dönüşür,
 * çeviriden sonra AYNEN geri konur. Motor yer tutucuyu bilinmeyen sözcük
 * sayıp olduğu gibi geçiriyor.
 *
 * NEYİ KALKANLAR: büyük harfle başlayan, 4+ harfli, İNGİLİZCE SÖZLÜKTE
 * OLMAYAN kelimeler. Sözlük şartı önemli: "Alien", "Because", "The" gibi
 * gerçek kelimeler kalkanlanmaz, yoksa sıradan cümleler çevrilmez hâle
 * gelirdi. Sözlük (124 bin kelime) henüz yüklenmediyse kalkan devreye
 * girmez — emin olmadan müdahale etmiyoruz.
 */
/**
 * AD DEFTERİ — altyazı akışından özel ad öğrenir.
 *
 * Sorun: NameShield yalnız SÖZLÜKTE OLMAYAN kelimeleri koruyordu. Sözlükte
 * kelime olarak da bulunan adlar korunmuyor ve çevrilip anlamı bozuluyordu:
 *   "here on the Venture"        -> "burada girişimde"
 *   "Alien and I will go in alone." -> "Yabancı ve ben yalnız gideceğim."
 *   "Salt..."                    -> "Tuz..."
 *
 * Çözüm: adları altyazının KENDİSİ söylüyor. Aynı kayıtta şunlar geçiyordu:
 *   [Alien]  [Telia]  [Nolan]  [Tuva]  [the Captain]
 * Köşeli parantezli konuşmacı etiketi kesin kanıttır. İkinci kanıt, cümle
 * ORTASINDA büyük harfle başlayan kelimedir ("the Venture is a tough old
 * lady") — cümlenin ilk kelimesi sayılmaz, orada her kelime büyük başlar.
 *
 * Böylece ilk birkaç satırdan sonra uygulama o yapımın adlarını öğreniyor ve
 * elle sözlük girdisi yazmaya gerek kalmıyor: her film/oyun için kendi
 * kendine çalışıyor.
 */
/**
 * 9.96 — PARANTEZSİZ KONUŞAN ADI.
 *
 * Bazı oyunlar konuşanı satırın başına parantezsiz, yapışık yazıyor. 12 Kasım
 * 9.95 kaydından:
 *   "Man in Black No shape. No shadow. Yet it moves through all."
 *   "Soul I touched a Lóng Pillar bound in chains..."
 * Ad cümlenin parçası sanılıp çevriliyordu: "Black'deki adam şekli yok",
 * "Ruh zincirlerine bağlı...". Çözüm: aynı büyük harfli öbek iki ayrı satırın
 * başında tekrar ediyor ve ardından yine büyük harfle yeni cümle başlıyorsa
 * bu öbek konuşan adıdır; satır "[Man in Black] No shape..." biçimine
 * getirilir. Köşeli etiketler zaten her katmanda ad olarak korunuyor.
 */
/**
 * 9.97 — BOZUK OKUNAN ETİKET PARANTEZLERİ (genel).
 *
 * 51 kaydın taramasında OCR'ın parantezi şu biçimlerde bozduğu görüldü:
 *   "{beeping| -One second."          { ve | parantez sanılmış
 *   "[hoarselyl Help."               kapanış ] -> l, ardından konuşma
 *   "[recorded Hey, this is Eve."    kapanış hiç okunmamış
 * Etiket kapanmayınca konuşma etiketin içinde kalıyor, çeviride ikisi
 * karışıyordu ("[Hoarselyl Yardım."). Bu kurallar her içerikte geçerli.
 */
object EtiketOnar {
    private val SUSLU_AC = Regex("(^|-)\\{(?=\\p{Ll})")
    private val BORU_KAPA = Regex("\\[(\\p{Ll}[\\p{Ll} ,'’-]*?)[|}](?=\\s|$)")
    private val L_KAPA = Regex("\\[(\\p{Ll}[\\p{Ll} ,'’-]*\\p{Ll})l(?=\\s+\\p{Lu})(?![^\\[\\]]*\\])")
    private val ACIK_SES = Regex(
        "\\[(?!(?:the|a|an)\\s)(\\p{Ll}[\\p{Ll}'’-]*(?: \\p{Ll}[\\p{Ll}'’-]*){0,3})\\s+(?=\\p{Lu}\\p{Ll})(?![^\\[\\]]*\\])",
    )

    fun onar(s: String): String {
        if (s.indexOf('[') < 0 && s.indexOf('{') < 0) return s
        var t = SUSLU_AC.replace(s, "\$1[")
        t = BORU_KAPA.replace(t, "[\$1]")
        t = L_KAPA.replace(t, "[\$1]")
        t = ACIK_SES.replace(t, "[\$1] ")
        return t
    }
}

object KonusanOnEki {
    private val kilit = Any()
    private val sonSatirlar = ArrayDeque<String>()
    private val bilinen = LinkedHashSet<String>()

    /** Tek başına konuşan adı sayılmayacak, cümle başında sık geçen sözcükler. */
    private val YASAK_TEK = setOf(
        "i", "the", "a", "an", "and", "but", "so", "or", "oh", "no", "yes", "yeah", "well",
        "what", "why", "how", "who", "where", "when", "you", "we", "he", "she", "it", "they",
        "this", "that", "these", "those", "then", "now", "hey", "okay", "ok", "my", "your",
        "our", "his", "her", "their", "if", "is", "are", "do", "did", "can", "let", "please",
        "sorry", "thank", "thanks", "look", "listen", "wait", "come", "go", "get", "stop",
        "press", "hold", "tap", "use", "select", "new", "just", "not", "all", "there", "here",
        "maybe", "perhaps", "still", "only", "even", "never", "always", "some", "every",
    )

    /** Ad öbeğinin içinde küçük harfle geçebilen bağlaçlar: "Man in Black". */
    private val BAGLAC = setOf("in", "of", "the", "de", "la", "von", "van", "al", "du")

    private val BOSLUK = Regex("\\s+")

    fun temizle() = synchronized(kilit) {
        sonSatirlar.clear()
        bilinen.clear()
    }

    fun durum(): String = synchronized(kilit) { bilinen.joinToString(", ") }

    // Ad sözcüğü: büyük harfle başlar, içinde küçük harf de vardır ("O", "X",
    // "CI" gibi tuş/çöp okumaları ad sayılmaz).
    private fun adSozcugu(w: String): Boolean =
        w.length >= 2 && w.first().isUpperCase() && w.drop(1).any { it.isLowerCase() } &&
            w.all { it.isLetter() || it == '\'' || it == '’' || it == '-' }

    /** Adın ardından gelen kısım gerçek cümle mi (en az bir 3+ harfli küçük kelime)? */
    private fun cumleMi(w: List<String>): Boolean =
        w.any { k -> k.count { it.isLowerCase() } >= 3 }

    private fun buyukBaslar(w: String): Boolean = w.firstOrNull()?.isUpperCase() == true

    private fun sozler(w: List<String>): Set<String> =
        w.map { k -> k.lowercase().filter { it.isLetter() } }.filter { it.length >= 3 }.toSet()

    /** İki sözcük dizisinin ortak (3+ harfli) sözcük oranı, kısa olana göre. */
    private fun ortakOran(a: List<String>, b: List<String>): Double {
        val x = sozler(a)
        val y = sozler(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / minOf(x.size, y.size)
    }

    /** İki satırın başında ortak, konuşan adı olabilecek öbek; yoksa null. */
    fun ortakOnEk(a: List<String>, b: List<String>): String? {
        var n = 0
        while (n < a.size && n < b.size && n < 4 && a[n] == b[n]) n++
        for (k in n downTo 1) {
            if (k >= a.size || k >= b.size) continue
            val on = a.subList(0, k)
            if (!adSozcugu(on.first()) || !adSozcugu(on.last())) continue
            if (on.any { !adSozcugu(it) && it !in BAGLAC }) continue
            if (on[0].lowercase() in YASAK_TEK) continue
            if (k == 1 && on[0].length < 3) continue
            if (!buyukBaslar(a[k]) || !buyukBaslar(b[k])) continue
            // "Old Man Gen: ..." — ad iki noktayla sürüyor, öbek eksik kalır.
            if (a[k].endsWith(":") || b[k].endsWith(":")) continue
            // Sonraki sözcük de aynı ve ad gibiyse öbek bitmemiştir ("Old" | "Man Gen").
            if (a[k] == b[k] && adSozcugu(a[k])) continue
            if (!cumleMi(a.subList(k, a.size)) || !cumleMi(b.subList(k, b.size))) continue
            // 10.0: aynı satırın iki bozuk okuması iki ayrı replik değildir.
            // "Afailed Transcension subject, arelessly..." / "... carelessly..."
            // yüzünden "Afailed" konuşan adı sanılmıştı. Kalanlar büyük ölçüde
            // aynı sözcüklerden oluşuyorsa öbek ad değildir.
            if (ortakOran(a.subList(k, a.size), b.subList(k, b.size)) >= 0.5) continue
            if (a.subList(k, a.size) == b.subList(k, b.size)) continue
            return on.joinToString(" ")
        }
        return null
    }

    fun uygula(satir: String): String {
        val s = satir.trim()
        if (s.length < 6 || s.startsWith("[") || s.startsWith("(")) return satir
        val w = s.split(BOSLUK).filter { it.isNotEmpty() }
        // "Old Man Gen: ..." zaten iki noktalı konuşan biçimi; dokunma.
        if (w.take(4).any { it.endsWith(":") }) return satir
        val adlar: List<String>
        synchronized(kilit) {
            if (sonSatirlar.lastOrNull() != s) {
                for (onceki in sonSatirlar) {
                    val ad = ortakOnEk(w, onceki.split(BOSLUK).filter { it.isNotEmpty() })
                    if (ad != null) {
                        bilinen.add(ad)
                        break
                    }
                }
                sonSatirlar.addLast(s)
                while (sonSatirlar.size > 6) sonSatirlar.removeFirst()
            }
            adlar = bilinen.sortedByDescending { it.length }
        }
        for (ad in adlar) {
            if (!s.startsWith("$ad ")) continue
            val kalan = s.substring(ad.length).trim()
            if (kalan.isEmpty() || !kalan.first().isUpperCase()) continue
            if (kalan.substringBefore(' ').endsWith(":")) continue
            return "[$ad] $kalan"
        }
        return satir
    }
}

object AdDefteri {
    /** "[Alien]", "[Nolan over implant", "[the Captain]" */
    private val ETIKET = Regex("\\[([^\\[\\]]{1,40})]?")
    private val KELIME = Regex("\\b\\p{Lu}[\\p{L}]{2,}\\b")
    private val CUMLE_AYIR = Regex("(?<=[.!?…])\\s+")
    // "Wait, Art." gibi açık seslenmeler, kısa adlar için güvenilir kanıt.
    // "art" sözcüğünün diğer kullanımları isim sayılmaz.
    private val SESLENME = Regex("\\b(?:Wait|Hey|Listen|Thanks|Sorry),\\s+([A-Z][a-z]{2,})(?=[.!?,]|$)")

    /** Etiket içinde ada eşlik eden, adın kendisi OLMAYAN sözcükler. */
    private val ETIKET_DOLGU = setOf(
        "the", "over", "on", "via", "through", "in", "at", "and", "to",
        "implant", "radio", "phone", "comms", "intercom", "speaker", "tv",
        "voiceover", "vo", "os", "cont", "continues", "whispering",
    )

    /**
     * Büyük harfle yazılsa da ÇEVRİLMESİ gereken kelimeler. Bunlar deftere
     * girmez; yoksa "the Captain" -> "Kaptan" çevirisi bozulurdu.
     */
    private val ASLA_AD = setOf(
        // unvanlar
        "captain", "doctor", "doc", "mister", "missus", "sir", "madam",
        "lord", "lady", "king", "queen", "prince", "princess", "president",
        "general", "colonel", "major", "sergeant", "chief", "boss",
        "professor", "officer", "detective", "agent", "judge", "mayor",
        "father", "mother", "mom", "mum", "dad", "papa", "mama",
        "grandma", "grandpa", "uncle", "aunt", "son", "brother", "sister",
        // diller, uluslar
        "english", "turkish", "french", "spanish", "german", "italian",
        "russian", "chinese", "japanese", "korean", "arabic", "greek",
        "american", "british", "european", "african", "asian", "latin",
        // takvim
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
        "sunday", "january", "february", "march", "april", "june", "july",
        "august", "september", "october", "november", "december",
        // kurum, yer, genel
        "earth", "moon", "sun", "god", "heaven", "hell", "christmas",
        "easter", "internet", "police", "army", "navy", "government",
        "senate", "congress", "court", "church", "hospital", "school",
        "university", "company", "council", "state", "city", "street",
        "north", "south", "east", "west", "christ", "lordy",
        // kurum adları: ÇEVRİLİR. "The Coalition of Planets" defterdeki
        // "the + büyük harf" kuralıyla ad sanılıyor, taslak "Gezegenlerin
        // Coalition" çıkıyordu (12 Kasım kaydı).
        "coalition", "empire", "alliance", "federation", "republic",
        "kingdom", "union", "order", "guild", "agency", "bureau",
        "department", "ministry", "academy", "resistance", "rebellion",
        "fleet", "command", "committee", "board", "association",
        "foundation", "institute", "league", "corps", "legion", "clan",
        "tribe", "planet", "planets", "galaxy", "universe", "world",
    )

    /**
     * AÇIK KALMIŞ konuşmacı etiketini kapatır.
     *
     * OCR kapanış parantezini düşürüyor, bazen açılışı da "(" okuyor:
     *   "(Space Racer To me!"        "[Alien We came here because..."
     * Adın nerede bittiği tek başına bilinemez; ama defter biliyor: baştan
     * itibaren ETİKETTEN öğrenilmiş adlar sayılıyor (en çok üç kelime).
     * Böylece yapay zekâ "[Yabancı] ..." diye adı çevirmeye kalkmıyor.
     */
    /** Etiketin başına yapışmış "I"/"l"/"|": "[INolan]" -> "[Nolan]". */
    private val YAPISIK_ETIKET = Regex("\\[([Il|])(\\p{Lu}[\\p{L}'’]+)")
    private val PARCA_AYIR = Regex("(?<=\\s)-(?=\\s*[\\[(])")

    /**
     * Satırdaki HER parçayı ayrı ele alır: OCR ikinci konuşmacının
     * etiketini de açık bırakıyor ("Nolan) Earth? -[Alien That's what he
     * said."). Ayrıca defterde bilinen ada yapışmış "I"yi söker.
     */
    fun acikEtiketKapat(metin: String): String {
        val onarilan = synchronized(kilit) {
            YAPISIK_ETIKET.replace(metin) { m ->
                if (m.groupValues[2].lowercase() in kesinEtiket) "[" + m.groupValues[2] else m.value
            }
        }
        val parcalar = PARCA_AYIR.split(onarilan)
        if (parcalar.size == 1) return tekEtiketKapat(onarilan)
        return parcalar.joinToString("-") { p ->
            val bas = p.takeWhile { it == ' ' }
            val son = p.takeLastWhile { it == ' ' }
            bas + tekEtiketKapat(p.trim()) + son
        }
    }

    private fun tekEtiketKapat(metin: String): String = synchronized(kilit) {
        val t = metin.trimStart()
        if (t.length < 4 || (t[0] != '[' && t[0] != '(')) metin
        else if (t.take(42).any { it == ']' || it == ')' }) metin
        else {
            val sozcukler = t.substring(1).split(' ').filter { it.isNotEmpty() }
            var n = 0
            while (n < sozcukler.size && n < 3 &&
                sozcukler[n].firstOrNull()?.isUpperCase() == true &&
                sozcukler[n].trim('\'', '’', '.', ',').lowercase() in kesinEtiket
            ) n++
            if (n == 0 || n >= sozcukler.size) metin
            else "[" + sozcukler.take(n).joinToString(" ") + "] " +
                sozcukler.drop(n).joinToString(" ")
        }
    }

    private const val ORTA_ESIK = 2
    private const val UST_SINIR = 400

    // Etiketten gelen ad KESİNDİR, geri alınmaz: "[Alien]" tartışmasız kanıt.
    // Erişim kilidi. @Synchronized yerine synchronized(...) bloğu
    // kullanılıyor: bu biçim projenin derlenen kodunda (AiTranslate,
    // CaptureService) zaten var, dolayısıyla kanıtlı. Kotlin derleyicisi
    // elimde olmadığı için kanıtsız biçim tercih edilmiyor.
    private val kilit = Any()

    // Etiketten gelen ad KESİNDİR, geri alınmaz: "[Alien]" tartışmasız kanıt.
    private val kesinEtiket = HashSet<String>()
    // Metinden ("the Venture") gelen ad, sonradan küçük harfle görülürse
    // GERİ ALINIR. OCR cümle ortasında rastgele büyük harf üretiyor:
    // "you were Worried some human book nerd" -> "Worried" ad sanılmamalı.
    private val kesinMetin = HashSet<String>()
    private val sayac = HashMap<String, Int>()
    private val kucukGorulen = HashSet<String>()

    fun temizle() = synchronized(kilit) {
        kesinEtiket.clear(); kesinMetin.clear(); sayac.clear(); kucukGorulen.clear()
    }

    fun adMi(kucuk: String): Boolean = synchronized(kilit) {
        kucuk in kesinEtiket ||
            (kucuk !in kucukGorulen &&
                (kucuk in kesinMetin || (sayac[kucuk] ?: 0) >= ORTA_ESIK))
    }

    fun durum(): String = synchronized(kilit) {
        "${kesinEtiket.size} etiket, ${kesinMetin.size} metin, " +
            "${sayac.count { it.value >= ORTA_ESIK }} tekrarlı"
    }

    private fun uygun(k: String): Boolean {
        val kucuk = k.lowercase()
        return k.length >= 3 && kucuk !in ASLA_AD && kucuk !in ETIKET_DOLGU
    }

    private fun etiketEkle(k: String) {
        if (!uygun(k)) return
        if (kesinEtiket.size < UST_SINIR) kesinEtiket += k.lowercase()
    }

    private fun metinEkle(k: String) {
        if (!uygun(k)) return
        val kucuk = k.lowercase()
        if (kucuk in kesinEtiket) return
        if (kesinMetin.size < UST_SINIR) kesinMetin += kucuk
    }

    private fun sayacEkle(k: String) {
        if (!uygun(k)) return
        val kucuk = k.lowercase()
        if (kucuk in kesinEtiket || kucuk in kesinMetin) return
        if (sayac.size >= UST_SINIR && kucuk !in sayac) return
        sayac[kucuk] = (sayac[kucuk] ?: 0) + 1
    }

    /** Küçük harfle görülen kelime, METİNDEN öğrenilmiş ad olamaz. */
    private fun kucukEkle(k: String) {
        if (k.length < 3) return
        if (kucukGorulen.size < UST_SINIR * 4) kucukGorulen += k.lowercase()
    }

    /** Satırı okur, ad adaylarını deftere işler. Çeviriden ÖNCE çağrılır. */
    fun ogren(satir: String) = synchronized(kilit) {
        if (satir.isNotBlank()) ogrenKilitli(satir)
    }

    private fun ogrenKilitli(satir: String) {
        for (m in SESLENME.findAll(satir)) etiketEkle(m.groupValues[1])

        // 1) Konuşmacı etiketleri: KESİN kanıt.
        //    Etiketin ilk sözcüğü küçük harfliyse o bir SES etiketidir
        //    ("[electrical crackling]"), ad aranmaz.
        for (m in ETIKET.findAll(satir)) {
            val ic = m.groupValues[1].trim()
            if (ic.isEmpty()) continue
            val sozcukler = ic.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
            val ilk = sozcukler.firstOrNull() ?: continue
            val ilkSade = ilk.trim('\'', '’', '.')
            // ses etiketi: "[groans]", "[the Captain]" -> "the" dolgu, sonrakine bak
            val basla = if (ilkSade.lowercase() in ETIKET_DOLGU) 1 else 0
            if (basla >= sozcukler.size) continue
            val bas = sozcukler[basla].trim('\'', '’', '.')
            if (bas.firstOrNull()?.isUpperCase() != true) continue   // ses etiketi
            for (w in sozcukler.drop(basla)) {
                val k = w.trim('\'', '’', '.', ':', ';')
                    .substringBefore('\'').substringBefore('’')
                if (k.firstOrNull()?.isUpperCase() == true && KELIME.matches(k)) etiketEkle(k)
            }
        }

        // 2) Cümle ORTASINDA büyük harf. Etiketler çıkarılıyor; etiket
        //    sonrası ilk kelime de "cümle başı" sayılıyor.
        val etiketsiz = ETIKET.replace(satir, " ").trim()
        for (cumle in etiketsiz.split(CUMLE_AYIR)) {
            val c = cumle.trim()
            if (c.isEmpty()) continue
            val sozcukler = c.split(Regex("\\s+")).filter { it.isNotBlank() }
            // ilk sözcük atlanıyor: orada büyük harf kanıt değil
            for ((i, w) in sozcukler.withIndex()) {
                if (i == 0) {
                    // İlk sözcükte BÜYÜK harf kanıt değil, ama KÜÇÜK harf
                    // kanıttır: cümle başında küçük yazılmışsa ad değildir.
                    val ilk = w.trim('"', '\'', '’', '(', ')', '[', ']', ',', '.', '!', '?', ':', ';', '…', '-')
                    if (ilk.firstOrNull()?.isLowerCase() == true) kucukEkle(ilk)
                    continue
                }
                val k = w.trim('"', '\'', '’', '(', ')', '[', ']', ',', '.', '!', '?', ':', ';', '…', '-')
                    .substringBefore('\'').substringBefore('’')
                if (k.length < 3) continue
                if (k.firstOrNull()?.isLowerCase() == true) { kucukEkle(k); continue }
                if (k.firstOrNull()?.isUpperCase() != true) continue
                if (!KELIME.matches(k)) continue
                // "the Venture" — belirtili tanımlık + büyük harf: KESİN.
                val onceki = sozcukler[i - 1].lowercase().trim(',', '.', '"', '\'')
                // "the Coalition of Planets": ardından "of" geliyorsa bu bir
                // kurum öbeğidir ve çevrilir; "the Venture" gibi çıplak ad değil.
                val sonraki = sozcukler.getOrNull(i + 1)?.lowercase()
                if (onceki == "the" && sonraki != "of") metinEkle(k) else if (onceki != "the") sayacEkle(k)
            }
        }
    }
}

object NameShield {
    class Slot(val token: String, val ad: String)
    class Kalkanli(val text: String, val slots: List<Slot>)

    /** Yer tutucular: motorun bölmeyeceği kısa, harf+rakam karışımı jetonlar. */
    private val jetonlar = (1..12).map { "Qx$it" }

    /** Üç harfli kişi adları (Art, Eve) da koruma kapsamındadır. */
    private val ADAY = Regex("\\b\\p{Lu}[\\p{L}]{2,}\\b")

    /**
     * Sözlükte olmasına rağmen cümle içinde ad olarak geçmesi sık olan ve
     * çevrilince anlamı bozan kelimeler. Dar tutuluyor.
     */
    private val HER_ZAMAN_AD = setOf("viltrumite", "viltrumites", "viltrumit")

    /**
     * 9.92 — Aynı zamanda sıradan bir kelime olan yaygın kişi adları.
     *
     * 12 Kasım 9.91 kayıtları: "Maybe Mark was right." -> "Belki de işaret
     * haklıydı."; "You'll be as tall as Mark soon." -> "Yakında işaret kadar
     * uzun olacaksın." Ad defteri bir adı cümle ortasında İKİ kez görmeden
     * öğrenmiyor; o zamana kadar çeviri motoru adı kelime sanıp çeviriyor.
     * Bu kelimeler cümle ORTASINDA büyük harfle yazılmışsa addır. Cümle
     * başında ("Mark my words.", "Hope so.") karar verilmez.
     */
    private val ORTADA_AD = setOf(
        "mark", "grace", "rose", "jack", "frank", "chase", "hunter", "faith", "dawn",
        "angel", "victor", "cliff", "lance", "miles", "sandy", "wade", "amber", "iris",
        "lily", "penny", "ruby", "violet", "daisy", "holly", "jade", "pearl", "crystal",
        "destiny", "harmony", "melody", "robin", "drew", "gene", "grant", "hope",
    )

    /** [bas] konumundaki kelime cümlenin ortasında mı (başında değil)? */
    private fun cumleOrtasi(text: String, bas: Int): Boolean {
        var i = bas - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '"' || text[i] == '\'' || text[i] == '-')) i--
        if (i < 0) return false
        return text[i] != '.' && text[i] != '!' && text[i] != '?' && text[i] != '…' &&
            text[i] != ']' && text[i] != ')' && text[i] != ':'
    }

    fun shield(text: String): Kalkanli {
        if (!Sozluk.hazir) return Kalkanli(text, emptyList())
        val slots = ArrayList<Slot>()
        // Full personal names such as "Jonathan Crane" must preserve BOTH
        // tokens. Previously the ordinary noun "crane" bypassed the shield.
        val pairStarts = ProperNameSequence1039.protectedStarts(text)
        val out = ADAY.replace(text) { m ->
            val k = m.value
            val kucuk = k.lowercase()
            // AdDefteri: SÖZLÜKTE OLAN adlar da korunuyor (Venture, Alien, Salt).
            // Isolated vocative "Mark?" / "Mark!" is a name. Do not
            // change the imperative "Mark the target" at sentence start.
            val vocativeMark = kucuk == "mark" &&
                text.trim().matches(Regex("^mark[,?!]+$", RegexOption.IGNORE_CASE))
            val adMi = !SpokenEnglish204.isSpokenGrammar(kucuk) &&
                (vocativeMark || kucuk in HER_ZAMAN_AD || AdDefteri.adMi(kucuk) ||
                m.range.first in pairStarts ||
                (kucuk in ORTADA_AD && cumleOrtasi(text, m.range.first)) ||
                !Sozluk.icinde(kucuk))
            if (!adMi || slots.size >= jetonlar.size) {
                m.value
            } else {
                // Aynı ad satırda iki kez geçerse aynı jetonu kullan.
                val varOlan = slots.firstOrNull { it.ad == k }
                if (varOlan != null) varOlan.token
                else {
                    val j = jetonlar[slots.size]
                    slots += Slot(j, k)
                    j
                }
            }
        }
        return Kalkanli(out, slots)
    }

    /**
     * Yer tutucuları adlarla değiştirir. Türkçe ek jetona yapışabilir
     * ("Qx1'u", "Qx1nin"); ek olduğu gibi korunur.
     */
    fun restore(translated: String, slots: List<Slot>): String {
        if (slots.isEmpty()) return translated
        var t = translated
        for (s in slots) {
            // "(?![0-9])" ŞART: jetonlar Qx1..Qx12. Onsuz "Qx1" geri konurken
            // "Qx12"nin içine de giriyor ve "Raymond2" gibi bozuk çıktı
            // veriyordu. Satırda 10+ ad varsa (ad listesi replikleri) sessizce
            // bozuluyordu. Kendi gözden geçirmemde bulundu.
            t = Regex("(?i)" + Regex.escape(s.token) + "(?![0-9])").replace(t) { s.ad }
        }
        return t
    }
}

/** Uygulama genelinde tek örnek. */
val OfflineFix = OfflineFixEngine()

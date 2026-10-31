package com.example.subtitles

/**
 * OCR'ın yapıştırdığı kelimeleri ayırır ve cümle ortasındaki gereksiz büyük harfleri düzeltir.
 *
 * Kelime kümesi bilinçli olarak KÜÇÜK tutulmuştur (~828 kelime). Büyük bir sözlük bölücüyü
 * kötüleştirir: "notice" -> "no tice" gibi hatalar tam olarak sözlük şiştiğinde çıkar.
 * Küme, işlev kelimeleri ve sık geçen oyun/diyalog sözcükleri üzerine kuruludur.
 *
 * Kümede yer alan her kelime bölünmeye karşı korunur. Bu yüzden "however", "island",
 * "ashore", "whatever" gibi bileşik görünümlü sözcükler listede bulunmak ZORUNDADIR;
 * yoksa "how ever", "is land", "a shore" diye ayrılırlar.
 */
object TextFix {

    private val WORDS: Set<String> = """
        a ablaze aboard about above abroad across aflame afraid after again against ago agree
        aground ahead aim air alarm alive all allow almost alone along aloud already also alter
        although altogether always am amid amiss among amount an anchor and anew anger angry ankle
        another answer any anybody anyhow anyone anything anywhere apart appear are arm army around
        arrive art as ash ashamed ashes ashore aside ask asleep astern at attack attempt await awake
        away awhile back backward bay be beach became because become becomes been before began begin
        begins begun behind being belief believe believed bell below beneath beside best better
        between beyond blood boat bone both bottle box brave bread breath breathe bridge brother
        burn but by cage came can cannot captain careful carefully cargo cell cells certain
        certainly chain chest coast coat coin come comes coming could crew crown cup danger dark day
        days dead death decent deck deep deserve deserved destroy destroyed destroys destruction did
        die died distance do dock does doing done door doors down downward dream drink during dust
        each early earth end ends enemies enemy enough escape escaped even ever every everybody
        everyone everything everywhere except fail failed fails failure far fast father fear feel
        feels felt few fight fighting fights find finds finish finished fire first flag fleet flesh
        floor fog follow followed follows fool for forest forever forget forgive forgiven fortune
        forward fought found free friend friends from full gate gather gathered get gets getting
        give given gives glass go goes going gold gone good got grass grave great ground guard had
        hand hands happen happened happens harbor hard has hate have having he head hear heard heart
        heaven heavy held hell help her here hers herself hide high highland hill him himself his
        history hold holds hole home honest honesty honor hook hope hoped horse hour hours house how
        however hunger i idea idle if ill image imagine imagined important in inch indeed injury ink
        inland inn inside instead intend interest into inward iron is island it its itself journey
        judge just keen keep keeps kept key kill killed kills kind king knew knife knot know
        knowledge known knows ladder lamp land lantern last late later lead learn leave leaves left
        less lesson let lets letter life light like likes line listen listened listening listens
        little live lives lock long look looking looks lose loses lost love made mainland make makes
        making man many map mast master matter matters may me meal mean means meant meanwhile meat
        men mercy might mind mine minute misunderstand moment money more moreover morning most
        mother mountain move moves much must my myself name near need needs net never new news next
        night no nobody none nonetheless nor not nothing notice now nowhere oar oath ocean of off
        offer offered old on once one only onto onward open opens or order other others otherwise
        our ours ourselves out outside outward over own pain paper part past path patience pay
        people perhaps pirate pistol place places plank play please pocket port powder power prepare
        prepared prison promise promised protect protected pull push put quarter queen question
        quick quiet rain ran rat rather reach reached read ready real reason remain remained
        remember remembered remembers respect respected rest return returns right rise river road
        rock room rope rum run running runs sack safe said sail sailor salt same sand save saw say
        says scar sea seat second secret see seen sees send sends sent servant set settle settled
        several shadow shall she shelf ship ships shipwright shirt shore short shot should show
        shows side sign silence silver since sir sister sit sits skill sky slave sleep slouch slow
        small smoke snow so soldier some somebody someday somehow someone something sometimes
        somewhat song soon sorrow sorry soul sound speak spirit spoke spot stair stand stands star
        stay stays steel step stick still stone stop stops storm story stranger string strong such
        suffer suffered sun suppose supposed supposes sure surrender sword table take taken takes
        tale talk talks tell tells temple tent than thank that the their theirs them themselves then
        there therefore these they thief thing things think thinks thirst this those though thought
        thread three throat through throughout throw thrown thunder tide time times tired to today
        together told tomorrow tongue tonight too took tool toward towards tower town trade trail
        treasure tree tries trouble troubled true trust truth try turn turns two under understand
        understood until up upon upward urgency us use used valley very victory village voice wait
        waits walk walks wall want wants war was watch water wave way we weapon weather week welcome
        well went were what whatever wheel when whenever where wherever which while whisky whisper
        who whoever whole whom whomever whose why wife will win wind wine winter wish with within
        without woman wonder wondered wood wool word words work works world worried worry worth
        would wound wright wrong year years yes yesterday yet you young your yours yourself
        yourselves
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    /**
     * Büyük harfi korunacak oyun terimleri. WORDS içinde de bulunabilirler (bölünmeye
     * karşı koruma için) ama cümle ortasında küçültülmezler: "the Order" tarikat adıdır
     * ve büyük harf, çevirmene özel isim olduğunu söyler.
     * AiTranslate sistem istemindeki sözlükle aynı terimleri taşımalıdır.
     */
    private val KORUNAN_TERIMLER = setOf(
        "order", "sect", "transcension", "dao", "qi", "wulin",
        "creed", "brotherhood", "templar", "assassin"
    )

    private fun bilinen(w: String): Boolean = w.lowercase() in WORDS

    /**
     * Tek bir kelimeyi ikiye böler; YALNIZCA her iki parça da bilinen kelimeyse.
     * Zaten bilinen bir kelimeye hiç dokunmaz.
     */
    private fun ikiyeBol(kelime: String): Pair<String, String>? {

        val k = kelime.lowercase()

        if (k.length < 4) return null
        if (bilinen(k)) return null

        var enIyiPuan = -1
        var enIyiKesim = -1

        for (i in 1 until k.length) {

            val sol = k.substring(0, i)
            val sag = k.substring(i)

            if (!bilinen(sol) || !bilinen(sag)) continue

            // Tek harfli parça yalnızca "i" veya "a" olabilir
            if (sol.length == 1 && sol != "i" && sol != "a") continue
            if (sag.length == 1 && sag != "i" && sag != "a") continue

            // İki harf + iki harf bölmeleri güvenilmez ("no" + "te")
            if (sol.length == 2 && sag.length == 2) continue

            // Dengeli bölme tercih edilir
            val puan = minOf(sol.length, sag.length)
            if (puan > enIyiPuan) {
                enIyiPuan = puan
                enIyiKesim = i
            }
        }

        if (enIyiKesim < 0) return null

        return kelime.substring(0, enIyiKesim) to kelime.substring(enIyiKesim)
    }

    /** Metindeki yapışık kelimeleri ayırır: "Ihad" -> "I had", "cannotfail" -> "cannot fail". */
    fun kelimeleriAyir(metin: String): String {
        return Regex("[A-Za-z]+").replace(metin) { eslesme ->
            val w = eslesme.value
            val bolme = ikiyeBol(w)
            if (bolme == null) w else "${bolme.first} ${bolme.second}"
        }
    }

    /**
     * Cümle ortasındaki, bilinen bir İngilizce kelime olan büyük harfli sözcükleri küçültür.
     * Kümede olmayanlar (Kenway, Jackdaw gibi özel isimler) dokunulmadan kalır.
     *
     * OCR ve bazı oyunlar "a decent Drink", "damned Cell for Days" gibi satırlar üretiyor;
     * çevirmen bu sözcükleri özel isim sanıp anlamı bozuyordu.
     */
    fun buyukHarfDuzelt(metin: String): String {

        val sonuc = StringBuilder(metin.length)
        var cumleBasi = true

        for (parca in Regex("[A-Za-z']+|[^A-Za-z']+").findAll(metin)) {

            var p = parca.value

            if (p.first().isLetter() || p.first() == '\'') {

                val govdesi = p.drop(1)

                if (!cumleBasi &&
                    p.first().isUpperCase() &&
                    govdesi.isNotEmpty() &&
                    govdesi == govdesi.lowercase() &&
                    bilinen(p) &&
                    p.lowercase() !in KORUNAN_TERIMLER &&
                    p != "I"
                ) {
                    p = p.first().lowercaseChar() + p.drop(1)
                }

                cumleBasi = false

            } else if (p.any { it == '.' || it == '!' || it == '?' }) {
                cumleBasi = true
            }

            sonuc.append(p)
        }

        return sonuc.toString()
    }

    /** İki geçişi sırayla uygular. Bölme önce gelmeli: "Ihad" -> "I had" -> "had". */
    fun onar(metin: String): String {
        return buyukHarfDuzelt(kelimeleriAyir(metin))
    }
}

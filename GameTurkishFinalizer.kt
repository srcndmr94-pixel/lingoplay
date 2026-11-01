package com.example.subtitles

/**
 * İngilizce oyun altyazısını temizler ve ML Kit sonucunu doğal Türkçeye son kez düzeltir.
 * Tam eşleşmeler gerçek cihaz loglarından gelir. Eşleşme yoksa ham çeviri korunur.
 */
object GameTurkishFinalizer {
    private fun key(text: String): String = text
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

    private val exact = mapOf(
        key("Come on, come on!") to "Hadi, hadi!",
        key("Move, move!") to "Hadi, hareket et!",
        key("I always been there for you.") to "Her zaman yanında oldum.",
        key("But this what I get?") to "Karşılığında gördüğüm bu mu?",
        key("Police! Don't fucking move!") to "Polis! Sakın kıpırdama lan!",
        key("Holy shit!") to "Hassiktir!",
        key("Stay down!") to "Yerde kal!",
        key("Yeah, I know.") to "Evet, biliyorum.",
        key("Come on, we need to find a way out.") to "Hadi, buradan bir çıkış yolu bulmalıyız.",
        key("I got Boobie's bricks.") to "Boobie'nin malları bende.",
        key("Fuck Boobie! We gotta get out of this.") to "Siktir et Boobie'yi! Buradan kurtulmamız lazım.",
        key("Lu, push up. Stay with me.") to "Lu, ilerle. Yanımdan ayrılma.",
        key("Lu, cover!") to "Lu, koru beni!",
        key("What the fuck is going on here?!") to "Burada ne sikim dönüyor?!",
        key("Fuck, it's him!") to "Siktir, bu o!",
        key("Can't you fucking handle a bunch of street pushers?") to "Bir avuç sokak satıcısıyla bile başa çıkamıyor musun?",
        key("Hey man, this ain't even loaded!") to "Hey dostum, bunun şarjörü bile dolu değil!",
        key("Aww, too bad!") to "Vah vah, ne kötü!",
        key("Clean this shit up!") to "Şu boku temizleyin!",
        key("It's fucking Méndez!") to "Siktir, Méndez bu!",
        key("Yep. You think he saw us?") to "Evet. Sence bizi gördü mü?",
        key("No. He couldn't have.") to "Hayır. Görmüş olamaz.",
        key("Get in here 'fore they get yo ass!") to "Kıçını yakalamadan buraya gir!",
        key("Thanks, man.") to "Sağ ol, dostum.",
        key("Every time I try to find some peace it's something else!") to "Ne zaman biraz huzur bulmaya çalışsam başka bir şey çıkıyor!",
        key("And that's my fault?") to "Bu benim suçum mu?",
        key("I'm hit!") to "Vuruldum!",
        key("Wrong way, this way.") to "Yanlış taraf. Buradan!",
        key("Fire at the Powder Keg!") to "Barut fıçısına ateş et!",
        key("Looks like a real page-turner.") to "Sürükleyici bir şeye benziyor.",
        key("Pardon my delay, I was catching up on some reading.") to "Geciktiğim için kusura bakma, biraz okumaya dalmıştım.",
        key("What can I do for you?") to "Sana nasıl yardımcı olabilirim?",
        key("So, what'll you be needing?") to "Peki, ne lazım?",
        key("Money was my only Aim.") to "Tek amacım paraydı.",
        key("Should I find comfort in that?") to "Bununla mı teselli bulmalıyım?",
        key("James tells me you treated with the Templars there.") to "James, oradaki Tapınakçılarla görüştüğünü söylüyor.",
        key("Did you see the Man they call The Sage?") to "Bilge dedikleri adamı gördün mü?",
        key("Would you recognize his Face if you saw it again?") to "Onu tekrar görsen yüzünü tanır mısın?",
        key("I must be certain.") to "Emin olmam gerek.",
        key("Not a Word.") to "Tek kelime etme."
    )

    private val dialogueStarts = listOf(
        "Come on,", "I got ", "Fuck Boobie!", "Yep.", "Lu,", "What the fuck",
        "Fuck,", "Can't you", "Hey man,", "Aww,", "Clean this", "It's fucking",
        "Police!", "Holy shit!", "Stay down!", "Move,", "Yeah,"
    )

    fun cleanSource(raw: String): String {
        var text = raw
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("\\s+"), " ")
            .trim()

        text = text
            .replace(Regex("(?i)\\blgot\\b"), "I got")
            .replace(Regex("(?i)\\bl\\s*got\\b"), "I got")
            .replace(Regex("(?i)\\bstay\\s+wth\\s+me\\b"), "Stay with me")

        if (isHudOnly(text) || text.equals("Kurulumu kontrol et", true)) return ""

        text = text.replace(
            Regex("^\\d+\\s+SHOTGUN\\s+(?=[A-Za-z])", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        val prefixEnd = dialogueStarts
            .map { text.indexOf(it, ignoreCase = true) }
            .filter { it > 0 }
            .minOrNull()
        if (prefixEnd != null) text = text.substring(prefixEnd).trim()

        // Diyaloğun sonuna yapışan bozuk SHOTGUN/HUD kuyruğunu at.
        text = text.replace(
            Regex("(?i)\\s+[A-Z0-9]{1,12}(?:\\s+[A-Z0-9]{1,12})*\\s+\\d+\\s+SH[OU]TGUN.*$"),
            ""
        ).trim()

        return text
    }

    fun polish(sourceEnglish: String, rawTurkish: String): String {
        val cleaned = cleanSource(sourceEnglish)
        if (cleaned.isBlank()) return ""
        return exact[key(cleaned)] ?: genericPolish(cleaned, rawTurkish.trim())
    }

    private fun genericPolish(source: String, raw: String): String {
        var out = raw
        if (source.contains("push up", true)) out = out.replace(Regex("(?i)şınav"), "ilerle")
        if (source.contains("cover", true)) out = out.replace(Regex("(?i)kapak"), "koru beni")
        if (source.contains("loaded", true)) out = out.replace(Regex("(?i)yüklen(?:iyor|miş|di)?"), "şarjörü dolu")
        if (source.contains("street pushers", true)) out = out.replace(Regex("(?i)sokak itici(?:ler)?"), "sokak satıcıları")
        return out
    }

    private fun isHudOnly(text: String): Boolean =
        Regex("(?i)^\\s*[A-Z0-9]*\\s*MORE\\s+LAND\\s+\\d+\\s+SH[OU]TGUN\\s*$").matches(text) ||
        Regex("(?i)^\\s*\\d+\\s+SH[OU]TGUN\\s*$").matches(text)
}

package com.example.subtitles

/**
 * Gerçek cihaz loglarından doğrulanan kaynak temizleme ve doğal Türkçe düzeltmeleri.
 * Kaynakta olmayan küfür eklemez. HUD/UI metinlerini diyalogdan ayırır.
 */
object NaturalGameTurkishCorrections {

    private fun normalize(text: String): String = text
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

    private val exactTranslations = mapOf(
        normalize("I'm hit!") to "Vuruldum!",
        normalize("Stay down!") to "Yerde kal!",
        normalize("Move, move!") to "Hadi, ilerle!",
        normalize("Wrong way, this way.") to "Yanlış taraf. Buradan!",
        normalize("Fire at the Powder Keg!") to "Barut fıçısına ateş et!",
        normalize("Police! Don't fucking move!") to "Polis! Sakın kıpırdama lan!",
        normalize("Lu, push up. Stay with me.") to "Lu, ilerle. Yanımdan ayrılma.",
        normalize("Come on, we need to find a way out.") to "Hadi, buradan bir çıkış yolu bulmalıyız.",
        normalize("I got Boobie's bricks.") to "Boobie'nin malları bende.",
        normalize("Fuck Boobie! We gotta get out of this.") to "Siktir et Boobie'yi! Buradan kurtulmamız lazım.",
        normalize("Lu, push up. Stay wth me.") to "Lu, ilerle. Yanımdan ayrılma.",
        normalize("Lu, cover!") to "Lu, koru beni!",

        normalize("Fuck, it's him!") to "Siktir, bu o!",
        normalize("It's fucking Méndez!") to "Siktir, Méndez bu!",
        normalize("What the fuck is going on here?!") to "Burada ne sikim dönüyor?!",
        normalize("What the fuck y'all doing in here?") to "Siz burada ne sikim yapıyorsunuz?",
        normalize("What the fuck you talking about?") to "Ne sikimden bahsediyorsun?",
        normalize("The fuck is that?") to "Bu ne sikim?",
        normalize("Where the fuck you at?") to "Neredesin lan?",
        normalize("Holy shit!") to "Hassiktir!",
        normalize("Ah, shit!") to "Ah, siktir!",
        normalize("Clean this shit up!") to "Şu boku temizleyin!",
        normalize("Can't you fucking handle a bunch of street pushers?") to
            "Bir avuç sokak satıcısıyla bile başa çıkamıyor musun?",
        normalize("Hey man, this ain't even loaded!") to
            "Hey dostum, bunun şarjörü bile dolu değil!",
        normalize("Get in here 'fore they get yo ass!") to "Kıçını yakalamadan buraya gir!",
        normalize("Yep. You think he saw us?") to "Evet. Sence bizi gördü mü?",
        normalize("No. He couldn't have.") to "Hayır. Görmüş olamaz.",
        normalize("Aww, too bad!") to "Vah vah, ne kötü!",
        normalize("Thanks, man.") to "Sağ ol, dostum.",
        normalize("Every time I try to find some peace it's something else!") to
            "Ne zaman biraz huzur bulmaya çalışsam başka bir şey çıkıyor!",
        normalize("And that's my fault?") to "Bu benim suçum mu?",

        normalize("Money was my only Aim.") to "Tek amacım paraydı.",
        normalize("Should I find comfort in that?") to "Bununla mı teselli bulmalıyım?",
        normalize("James tells me you treated with the Templars there.") to
            "James, oradaki Tapınakçılarla görüştüğünü söylüyor.",
        normalize("Did you see the Man they call The Sage?") to "Bilge dedikleri adamı gördün mü?",
        normalize("Would you recognize his Face if you saw it again?") to
            "Onu tekrar görsen yüzünü tanır mısın?",
        normalize("I must be certain.") to "Emin olmam gerek.",
        normalize("Not a Word.") to "Tek kelime etme.",
        normalize("We are not sorry to see him gone.") to "Gittiğine hiç üzülmüyoruz.",
        normalize("But it was you who carried out his final Betrayal. Why?") to
            "Ama son ihanetini gerçekleştiren sendin. Neden?",

        normalize("Looks like a real page-turner.") to "Sürükleyici bir şeye benziyor.",
        normalize("Pardon my delay, I was catching up on some reading.") to
            "Geciktiğim için kusura bakma, biraz okumaya dalmıştım.",
        normalize("But this what I get?") to "Karşılığında bunu mu görüyorum?",
        normalize("What can I do for you?") to "Sana nasıl yardımcı olabilirim?",
        normalize("So, what'll you be needing?") to "Peki, ne lazım?"
    )

    fun cleanSourceText(raw: String): String {
        var text = raw.trim()
        // HUD öneki: "10 SHOTGUN Yep..." -> "Yep..."
        text = text.replace(
            Regex("^\\d+\\s+SHOTGUN\\s+", RegexOption.IGNORE_CASE),
            ""
        )
        // OCR/HUD çöplüğü cümlenin önüne yapışırsa bilinen diyalog başlangıcından itibaren al.
        val dialogueStarts = listOf(
            "Fuck Boobie!", "Yep.", "Lu,", "What the fuck", "Fuck,", "Can't you",
            "Hey man", "Aww,", "Clean this", "It's fucking", "Come on"
        )
        val start = dialogueStarts
            .map { marker -> text.indexOf(marker, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull()
        if (start != null && start > 0) text = text.substring(start)
        return text.trim()
    }

    fun shouldIgnoreSource(text: String): Boolean {
        val value = normalize(text)
        if (value.isBlank()) return true
        if (value == normalize("Kurulumu kontrol et")) return true
        if (Regex("^more land\\s+\\d+\\s+shotgun$", RegexOption.IGNORE_CASE).matches(value)) return true
        if (Regex("^\\d+\\s+shotgun$", RegexOption.IGNORE_CASE).matches(value)) return true
        return false
    }

    fun polish(sourceEnglish: String, fallbackTurkish: String): String {
        val cleaned = cleanSourceText(sourceEnglish)
        if (shouldIgnoreSource(cleaned)) return ""
        return exactTranslations[normalize(cleaned)] ?: fallbackTurkish.trim()
    }
}

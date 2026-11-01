package com.example.subtitles

/**
 * ML Kit çıktısındaki sık görülen oyun-komutu/deyim hatalarını, kaynak İngilizceyi de
 * dikkate alarak lokal ve deterministik biçimde düzeltir. Çevrimiçi çeviriye uygulanmaz.
 */
object GameTurkishPostEditor {
    private data class Rule(val source: Regex, val output: String)

    private val exactRules = listOf(
        Rule(Regex("(?i)^i['’]?m hit[.!]?$"), "Vuruldum!"),
        Rule(Regex("(?i)^stay down[.!]?$"), "Yerde kal!"),
        Rule(Regex("(?i)^move[,! ]+move[.!]?$"), "Hadi, hareket et!"),
        Rule(Regex("(?i)^come on[,! ]+come on[.!]?$"), "Hadi, hadi!"),
        Rule(Regex("(?i)^wrong way[,!. ]+this way[.!]?$"), "Yanlış taraf. Buradan!"),
        Rule(Regex("(?i)^fire at the powder keg[.!]?$"), "Barut fıçısına ateş et!"),
        Rule(Regex("(?i)^damn you[.!]?$"), "Kahretsin!"),
        Rule(Regex("(?i)^holy shit[.!]?$"), "Hassiktir!"),
        Rule(Regex("(?i)^ah[,! ]+shit[.!]?$"), "Ah, kahretsin!"),
        Rule(Regex("(?i)^what can i do for you[?!.]*$"), "Sana nasıl yardımcı olabilirim?"),
        Rule(Regex("(?i)^so[, ]+what['’]?ll you be needing[?!.]*$"), "Peki, ne lazım?"),
        Rule(Regex("(?i)^a shame[, ]+['’]?til next time then[.!]?$"), "Ne yazık. Artık bir dahaki sefere."),
        Rule(Regex("(?i)^have you business (?:to contract )?with me[?!.]*$"), "Benimle bir işin mi var?"),
        Rule(Regex("(?i)^looks like a real page[- ]turner[.!]?$"), "Sürükleyici bir şeye benziyor."),
        Rule(Regex("(?i)^i always been there for you[.!]?$"), "Ben hep senin yanındaydım."),
        Rule(Regex("(?i)^but this what i get[?!.]*$"), "Karşılığında bunu mu görüyorum?"),
        Rule(Regex("(?i)^pardon my delay[, ]+i was catching up on some reading[.!]?$"), "Geciktiğim için kusura bakma, biraz okuma yapıyordum."),
        Rule(Regex("(?i)^(?:yeah[.! ]+)?looks like a real page[- ]turner[.!]?$"), "Evet... Sürükleyici bir şeye benziyor."),
        Rule(Regex("(?i)^where the fuck you at[?!.]*$"), "Neredesin lan?"),
        Rule(Regex("(?i)^what the fuck you talking about[?!.]*$"), "Ne sikimden bahsediyorsun?"),
        Rule(Regex("(?i)^the fuck is that[?!.]*$"), "Bu ne sikim?"),
        Rule(Regex("(?i)^hey man[, ]+this ain['’]?t even loaded[.!]?$"), "Hey dostum, bunun şarjörü bile dolu değil!"),
        Rule(Regex("(?i)^aww[, ]+too bad[.!]?$"), "Vah vah, ne kötü!"),
        Rule(Regex("(?i)^clean this shit up[.!]?$"), "Şu boku temizleyin!"),
        Rule(Regex("(?i)^yep[.! ]+you think he saw us[?!.]*$"), "Evet. Sence bizi gördü mü?"),
        Rule(Regex("(?i)^no[.! ]+he couldn['’]?t have[.!]?$"), "Hayır. Görmüş olamaz."),
        Rule(Regex("(?i)^yo[, ]+they got more cops coming in from down ?stairs[.!]?$"), "Hey, aşağıdan daha fazla polis geliyor."),
        Rule(Regex("(?i)^get in here ['’]?fore they get yo(?:ur)? ass[.!]?$"), "Kıçını yakalamadan buraya gir!"),
        Rule(Regex("(?i)^what the fuck y['’]?all doing in here[?!.]*$"), "Siz burada ne sikim yapıyorsunuz?"),
        Rule(Regex("(?i)^you ain['’]?t hear all that outside[?!.]*"), "Dışarıdaki onca şeyi duymadın mı? Şimdi bir dakika sakin ol."),
        Rule(Regex("(?i)^and that['’]?s my fault[?!.]*$"), "Bu benim suçum mu?"),
        Rule(Regex("(?i)^every time i try to find some peace it['’]?s something else[.!]?$"), "Ne zaman biraz huzur bulmaya çalışsam başka bir şey çıkıyor!"),
        Rule(Regex("(?i)^my ac[.! ]+don['’]?t you climb out my .*window[.!]?$"), "Klimam! Sakın penceremden çıkmaya kalkma!"),
        Rule(Regex("(?i)^you know i got .* outside yo momma house[, ]+right[?!.]*$"), "Annenin evinin dışında adamlarımdan biri olduğunu biliyorsun, değil mi?"),
        Rule(Regex("(?i)^fuck[, ]+it['’]?s him[.!]?$"), "Siktir, bu o!"),
        Rule(Regex("(?i)^can['’]?t you fucking handle a bunch of street pushers[?!.]*$"), "Bir avuç sokak satıcısıyla bile başa çıkamıyor musun?"),
        Rule(Regex("(?i)^it['’]?s fucking méndez[.!]?$"), "Siktir, bu Méndez!"),
        Rule(Regex("(?i)^oh[, ]+coño[.!]?$"), "Ah, siktir!"),
        Rule(Regex("(?i)^wh-?\s*raymond[, ]+what['’]?s going[.! ]+what the fuck going on[?!.]*$"), "Raymond, neler oluyor? Ne sikim dönüyor burada?"),
        Rule(Regex("(?i)^you are not all owed to just come in here[.!]?$"), "Öyle kafanıza göre buraya dalamazsınız."),
        Rule(Regex("(?i)^hold on["”']?[?!. ]+you got motherfuckers running up in my house[.!]?$"), "Dur bir dakika mı? Orospu çocukları evimi basıyor!"),
        Rule(Regex("(?i)^thanks[, ]+man[.!]?$"), "Sağ ol, dostum."),
        Rule(Regex("(?i)^clean this shit up[.!]?$"), "Şu boku temizleyin!"),
        Rule(Regex("(?i)^dead and buried[.! ]+after he tried to kill me[.!]?$"), "Öldü, gömüldü. Beni öldürmeye kalkıştıktan sonra."),
        Rule(Regex("(?i)^we are not sorry to see him gone[.!]?$"), "Gittiğine hiç üzülmüyoruz."),
        Rule(Regex("(?i)^but it was you who carried out his final betrayal[.! ]+why[?!.]*$"), "Ama son ihanetini gerçekleştiren sendin. Neden?"),
        Rule(Regex("(?i)^money was my only aim[.!]?$"), "Tek amacım paraydı."),
        Rule(Regex("(?i)^should i find comfort in that[?!. ]+you murdered our brothers and sisters in havana[.!]?$"), "Bununla mı teselli bulmalıyım? Havana'daki kardeşlerimizi katlettin!"),
        Rule(Regex("(?i)^he has the sense[, ]+mentor[.!]?$"), "Aklı başında, Üstat."),
        Rule(Regex("(?i)^james tells me you treated with the templars there[.!]?$"), "James, oradaki Tapınakçılarla görüştüğünü söylüyor."),
        Rule(Regex("(?i)^did you see the man they call the sage[?!.]*$"), "Bilge dedikleri adamı gördün mü?"),
        Rule(Regex("(?i)^would you recognize his face if you saw it again[?!.]*$"), "Onu tekrar görsen yüzünü tanır mısın?"),
        Rule(Regex("(?i)^if you saw it again[?!.]*$"), "Onu tekrar görsen?"),
        Rule(Regex("(?i)^i must be certain[.!]?$"), "Emin olmam gerek."),
        Rule(Regex("(?i)^not a word[.!]?$"), "Tek kelime etme."),
        Rule(Regex("(?i)^you walked me blind and backwards into this mess[, ]+kidd[.!]?$"), "Beni hiçbir şeyden habersiz bu belanın içine soktun, Kidd!")
    )

    fun polish(sourceEnglish: String, mlKitTurkish: String): String {
        val source = sourceEnglish.replace(Regex("\\s+"), " ").trim()
        exactRules.firstOrNull { it.source.matches(source) }?.let { return it.output }

        var out = mlKitTurkish.trim()
        if (out.isBlank()) return out

        // Source-aware command corrections. Only apply when source intent is unambiguous.
        if (Regex("(?i)^police[!,. ]+").containsMatchIn(source) &&
            Regex("(?i)don't\\s+(?:fucking\\s+)?move").containsMatchIn(source)) {
            return "Polis! Sakın kıpırdama lan!"
        }
        if (Regex("(?i)^let['’]?s get these workers out of here").containsMatchIn(source)) {
            return "Hadi şu işçileri buradan çıkaralım!"
        }
        if (Regex("(?i)^a little help would be nice").containsMatchIn(source) ||
            Regex("(?i)^ah[, ]+a little help would be nice").containsMatchIn(source)) {
            return "Ah, biraz yardım etseniz fena olmaz!"
        }

        // Conservative Turkish cleanup; never rewrites names or whole sentences blindly.
        out = out.replace(Regex("\\s+([,.!?;:])"), "$1")
            .replace(Regex("([,.!?;:])(?=\\S)"), "$1 ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return out
    }
}

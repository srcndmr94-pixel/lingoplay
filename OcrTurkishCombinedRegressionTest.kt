package com.example.subtitles

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTurkishCombinedRegressionTest {
    private data class Case(
        val raw: String,
        val cleaned: String,
        val machineTr: String,
        val expectedTr: String
    )

    @Test
    fun combinedOcrAndTurkishRegressionCases() {
        val cases = listOf(
            Case("Iť's one you will own.", "It's one you will own.", "Ben sahip olacağın bir kişi.", "Bu, sizin de sahip olacağınız bir şey."),
            Case("At who lesale prices.", "At wholesale prices.", "Kim Lesale fiyatları.", "Toptan satış fiyatlarıyla."),
            Case("ladies and gentleman.", "ladies and gentleman.", "Bayanlar ve beyefendi.", "Hanımlar ve beyler."),
            Case("and easy to produce.", "and easy to produce.", "ve üretmek kolay.", "ve üretimi kolay."),
            Case("The re'll be guests.", "There'll be guests.", "Konuklar olacak.", "Konuklar olacak."),
            Case("to for eign powers!", "to foreign powers!", "yabancı güçlere!", "yabancı güçlere!"),
            Case("(tense music play ingl", "(tense music playing", "[gergin müzik]", "[gergin müzik]"),
            Case("You haven't killed anyone yet.", "You haven't killed anyone yet.", "Henüz birini öldürmedin.", "Henüz kimseyi öldürmedin."),
            Case("This vendetta can't go on", "This vendetta can't go on", "Bu vendetta devam edemez", "Bu kan davası böyle devam edemez"),
            Case("The accident obviously hurt your mind.", "The accident obviously hurt your mind.", "Kaza açıkça aklını incitti.", "Kaza belli ki aklına zarar vermiş."),
            Case("You'll be hearing from my lawyer.", "You'll be hearing from my lawyer.", "Avukatımdan haber alacaksın.", "Avukatım seninle iletişime geçecek."),
            Case("And so I gather, sir.", "And so I gather, sir.", "Ve ben de topladım efendim.", "Ben de öyle anlıyorum, efendim."),
            Case("many would call in the police.", "many would call in the police.", "Birçoğu polisteki arayacaktı.", "Çoğu kişi polisi çağırırdı."),
            Case("Gordon's people can't handle her.", "Gordon's people can't handle her.", "Gordon'un halkı onunla başa çıkamıyor.", "Gordon'un adamları onunla başa çıkamaz."),
            Case("she'd cut through them like butter.", "she'd cut through them like butter.", "Onları tereyağı gibi kesti.", "Onları tereyağı gibi biçer geçerdi."),
            Case("Then it's down to you.", "Then it's down to you.", "O zaman senin için aşağı.", "O zaman iş sana düşüyor."),
            Case("However will you get there in time?", "However will you get there in time?", "Ancak zamanla oraya gidersiniz?", "Peki oraya zamanında nasıl yetişeceksin?")
        )

        cases.forEach { c ->
            val cleaned = TextFix.onar(c.raw)
            assertEquals(c.cleaned, cleaned)
            val fixedTr = TurkishTextFix.onar(cleaned, c.machineTr, 60)
            assertEquals(c.expectedTr, fixedTr)
        }
    }
}

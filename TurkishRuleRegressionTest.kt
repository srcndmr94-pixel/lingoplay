package com.example.subtitles
import org.junit.Assert.assertEquals
import org.junit.Test

class TurkishRuleRegressionTest {
    private fun check(source: String, machine: String, expected: String) =
        assertEquals(expected, TurkishTextFix.onar(source, machine, 60))

    @Test fun allKnownRules() {
        check("And so I gather, sir.", "Ve ben de topladım efendim.", "Ben de öyle anlıyorum, efendim.")
        check("many would call in the police.", "Birçoğu polisteki arayacaktı.", "Çoğu kişi polisi çağırırdı.")
        check("Gordon's people can't handle her.", "Gordon'un halkı onunla başa çıkamıyor.", "Gordon'un adamları onunla başa çıkamaz.")
        check("she'd cut through them like butter.", "Onları tereyağı gibi kesti.", "Onları tereyağı gibi biçer geçerdi.")
        check("Then it's down to you.", "O zaman senin için aşağı.", "O zaman iş sana düşüyor.")
        check("However will you get there in time?", "Ancak zamanla oraya gidersiniz?", "Peki oraya zamanında nasıl yetişeceksin?")
        check("ladies and gentleman.", "Bayanlar ve beyefendi.", "Hanımlar ve beyler.")
        check("It's one you will own.", "Ben sahip olacağın bir kişi.", "Bu, sizin de sahip olacağınız bir şey.")
        check("At wholesale prices.", "Kim Lesale fiyatları.", "Toptan satış fiyatlarıyla.")
        check("and easy to produce.", "ve üretmek kolay.", "ve üretimi kolay.")
        check("You haven't killed anyone yet.", "Henüz birini öldürmedin.", "Henüz kimseyi öldürmedin.")
        check("This vendetta can't go on", "Bu vendetta devam edemez", "Bu kan davası böyle devam edemez")
        check("The accident obviously hurt your mind.", "Kaza açıkça aklını incitti.", "Kaza belli ki aklına zarar vermiş.")
        check("You'll be hearing from my lawyer.", "Avukatımdan haber alacaksın.", "Avukatım seninle iletişime geçecek.")
    }
}

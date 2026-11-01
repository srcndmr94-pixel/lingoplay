package com.example.subtitles
import org.junit.Assert.assertEquals
import org.junit.Test
class TurkishTextFixTest {
 @Test fun ladiesAndGentlemen(){ assertEquals("Hanımlar ve beyler.", TurkishTextFix.onar("ladies and gentlemen.", "Bayanlar ve beyefendi.", 42)) }
 @Test fun ladiesAndGentleman(){ assertEquals("Hanımlar ve beyler.", TurkishTextFix.onar("ladies and gentleman.", "Bayanlar ve beyefendi.", 42)) }
 @Test fun ownOne(){ assertEquals("Bu, sizin de sahip olacağınız bir şey.", TurkishTextFix.onar("It's one you will own.", "Ben sahip olacağın bir kişi.", 60)) }
 @Test fun wholesale(){ assertEquals("Toptan satış fiyatlarıyla.", TurkishTextFix.onar("At wholesale prices.", "Kim Lesale fiyatları.", 42)) }
 @Test fun easyToProduce(){ assertEquals("ve üretimi kolay.", TurkishTextFix.onar("and easy to produce.", "ve üretmek kolay.", 42)) }
}

package com.example.subtitles

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSubtitleEnhancerTest {
    /** ML Kit'in kullanıcının kaydındaki (YAZILAN) hatalı çıktılarını taklit eder; bilinmeyen için işaretli döner. */
    private class FakeMlKit : OfflineTranslationEngine {
        val calls = ArrayList<String>()
        val canned = mapOf(
            "we need to find a way out." to "buradan çıkmanın bir yolunu bulmalıyız.",
            "you know, we need to go, now!" to "bilirsin, gitmemiz lazım, hemen!",
            "Yep. You think he saw us?" to "Evet. Bizi gördüğünü mü düşünüyorsun?",
            "Yep." to "Evet.",
            "You think he saw us?" to "Bizi gördüğünü mü düşünüyorsun?",
            "No. He couldn't have." to "Hayır. O olamazdı.",
            "No." to "Hayır.",
            "He couldn't have." to "O olamazdı.",
            "Yeah, I know." to "Evet biliyorum.",
            "Something strange, fucking happened!" to "Garip bir şey, fucking oldu!",
            "Fuck, o" to "Fuck, o"
        )
        override suspend fun translate(normalizedEnglish: String): String {
            calls += normalizedEnglish
            return canned[normalizedEnglish] ?: "<<ml:$normalizedEnglish>>"
        }
    }

    private val ml = FakeMlKit()
    private val engine = EnhancedOfflineTranslationEngine(ml, SubtitleGlossary.default(SubtitleGlossary.GAME_ENTRIES))
    private fun t(s: String) = runBlocking { engine.translate(s) }

    // --- Kullanıcının kaydındaki hatalı satırlar
    @Test fun holyShit() = assertEquals("Hassiktir!", t("Holy shit!"))
    @Test fun stayDown() = assertEquals("Yerde kal!", t("Stay down!"))
    @Test fun policeDontFuckingMove() = assertEquals("Polis! Kıpırdama lan!", t("Police! don't fucking move!"))
    @Test fun luPushUpStayWithMe() = assertEquals("Lu, ilerle. Benimle kal.", t("Lu, push up. Stay with me."))
    @Test fun luCover() = assertEquals("Lu, siper al!", t("Lu, cover!"))
    @Test fun whatTheFuckIsGoingOnHere() = assertEquals("Burada ne bok oluyor?!", t("What the fuck is going on here?!"))
    @Test fun fuckItsHim() = assertEquals("Siktir, o!", t("Fuck, it's him!"))
    @Test fun streetPushers() = assertEquals(
        "Bir avuç sokak satıcısıyla bile başa çıkamıyor musunuz be?",
        t("Can't you fucking handle a bunch of street pushers?")
    )
    @Test fun notLoaded() = assertEquals("Hey dostum, bunda mermi bile yok!", t("Hey man, this ain't even loaded!"))
    @Test fun tooBad() = assertEquals("Ah, ne yazık!", t("Aww, too bad!"))
    @Test fun cleanThisShitUp() = assertEquals("Bu boku temizleyin!", t("Clean this shit up!"))
    @Test fun fuckingMendez() = assertEquals("Siktiğimin Méndez'i bu!", t("It's fucking Méndez!"))
    @Test fun fuckBoobie() = assertEquals("Boobie'yi siktir et! Bu işten sıyrılmamız lazım.", t("Fuck Boobie! We gotta get out of this."))

    // --- OCR düzeltmeleri
    @Test fun ocrLooksLikeIve() = assertEquals("Hep yanında oldum.", t("l always been there for you."))
    @Test fun ocrDroppedLeadingI() = assertEquals("Boobie'nin paketleri bende.", t("got Boobie's bricks."))
    @Test fun ocrMissingIsInSentence() = assertEquals("Karşılığında aldığım bu mu yani?", t("But this what I get?"))
    @Test fun ocrCleanerStandalone() {
        assertEquals("I'm ready.", OcrTextCleaner.clean("l'm ready."))
        assertEquals("I'm ready.", OcrTextCleaner.clean("1'm ready."))
        assertEquals("Got it!", OcrTextCleaner.clean("Got it!")) // büyük harfle başlayan değişmez
        assertEquals("Hello.", OcrTextCleaner.clean("- Hello."))
    }

    // --- Yerleşik çeviriye bırakılanlar bozulmamalı
    @Test fun knownInterjectionIsSplitOffAndRestGoesToMlKitWithPunctuation() {
        assertEquals("Hadi, buradan çıkmanın bir yolunu bulmalıyız.", t("Come on, we need to find a way out."))
        assertEquals(listOf("we need to find a way out."), ml.calls)
    }
    @Test fun adjacentUnknownPartsAreTranslatedTogether() {
        assertEquals("Bir dakika, <<ml:you know, we need to find it, now!>>", t("Hold on, you know, we need to find it, now!"))
        assertEquals(listOf("you know, we need to find it, now!"), ml.calls)
    }
    @Test fun sentenceWithNoKnownPartIsNeverSplitOnCommas() {
        t("Something odd, truly odd, happened here.")
        assertEquals(listOf("Something odd, truly odd, happened here."), ml.calls)
    }
    @Test fun multiSentenceUnknownIsSplitPerSentence() {
        assertEquals("Evet. Bizi gördüğünü mü düşünüyorsun?", t("Yep. You think he saw us?"))
        assertEquals("Hayır. O olamazdı.", t("No. He couldn't have."))
    }
    @Test fun leftoverProfanityFromMlKitIsTurkishified() =
        assertEquals("<<ml:Something strange, happened!>>", t("Something strange, fucking happened!")) // pekiştirici küfür ML Kit'e gitmeden çıkarılır
    @Test fun leftoverAtSentenceStartKeepsCapital() {
        val e = EnhancedOfflineTranslationEngine(object : OfflineTranslationEngine {
            override suspend fun translate(normalizedEnglish: String) = "Fuck, o o!"
        })
        assertEquals("Siktir, o o!", runBlocking { e.translate("Some unknown line here!") })
    }
    @Test fun glossaryLinesNeverCallMlKit() {
        t("Holy shit!"); t("Stay down!"); t("Lu, cover!")
        assertTrue(ml.calls.isEmpty())
    }

    // --- Kullanıcı sözlüğü
    @Test fun userEntriesOverrideDefaults() {
        val g = SubtitleGlossary.default(SubtitleGlossary.GAME_ENTRIES + "\nholy shit = Vay anasını\nbrick = Paket\n@Gus")
        val e = EnhancedOfflineTranslationEngine(ml, g)
        assertEquals("Vay anasını!", runBlocking { e.translate("Holy shit!") })
        assertEquals("Paket.", runBlocking { e.translate("Brick.") })
        assertEquals("Gus, siper al!", runBlocking { e.translate("Gus, cover!") })
    }
    @Test fun parseIgnoresCommentsAndBrokenLines() {
        val g = SubtitleGlossary.parse("# yorum\n\nbozuk satır\n= boş\nhi = Selam!!!\n")
        assertEquals("Selam", g.lookup("Hi!"))
    }

    // --- Tüm kayıt: hiçbir çıktıda çevrilmemiş küfür ya da bilinen saçma çeviri kalmamalı
    @Test fun wholeUserLogHasNoUntranslatedProfanity() {
        val log = listOf(
            "l always been there for you.", "But this what I get?", "Police! don't fucking move!", "Holy shit!",
            "Stay down!", "Yeah, I know.", "Come on, we need to find a way out.", "got Boobie's bricks.",
            "Fuck Boobie! We gotta get out of this.", "Lu, push up. Stay with me.", "Lu, cover!",
            "What the fuck is going on here?!", "Fuck, it's him!", "Can't you fucking handle a bunch of street pushers?",
            "Hey man, this ain't even loaded!", "Aww, too bad!", "Clean this shit up!", "It's fucking Méndez!",
            "Yep. You think he saw us?", "No. He couldn't have."
        )
        for (line in log) {
            val out = t(line)
            assertFalse("çevrilmemiş küfür: '$line' -> '$out'", Regex("(?i)\\b(fuck|fucking|shit)\\b").containsMatchIn(out))
            assertFalse("ML Kit'e bilinmeyen satır gitti: '$line' -> '$out'", out.contains("<<ml:"))
        }
    }

    // --- GENEL kapsam (oyuna özel sözlük OLMADAN)
    private val ml2 = FakeMlKit()
    private val general = EnhancedOfflineTranslationEngine(ml2)
    private fun g(s: String) = runBlocking { general.translate(s) }

    @Test fun slangIsNormalizedBeforeMlKit() {
        g("I wanna know what's goin' on, y'all!")
        assertEquals(listOf("I want to know what's going on, you all!"), ml2.calls)
    }
    @Test fun aintVariants() {
        g("I ain't leaving without him."); g("They ain't here."); g("Ain't nobody home.")
        assertEquals(listOf("I'm not leaving without him.", "They aren't here.", "Isn't nobody home."), ml2.calls)
    }
    @Test fun gottaGimmeLemmeCuz() {
        g("Gimme that, cuz I gotta go.")
        assertEquals(listOf("Give me that, because I have to go."), ml2.calls)
    }
    @Test fun intensifierIsRemovedBeforeMlKit() {
        g("Don't fucking touch it, you goddamn idiot!")
        assertEquals(listOf("Don't touch it, you idiot!"), ml2.calls)
    }
    @Test fun whTheFuckBecomesHell() {
        g("Where the fuck are you going now?")
        assertEquals(listOf("Where the hell are you going now?"), ml2.calls)
    }
    @Test fun nounProfanityIsShieldedAndRestoredInTurkish() {
        assertEquals("<<ml:This bok and that pislik herif finished the job.>>", g("This shit and that asshole finished the job."))
        assertEquals(listOf("This Zqa and that Zqb finished the job."), ml2.calls)
    }
    @Test fun shieldedTokenAtSentenceStartGetsCapital() {
        val echo = object : OfflineTranslationEngine { override suspend fun translate(normalizedEnglish: String) = normalizedEnglish }
        val e = EnhancedOfflineTranslationEngine(echo)
        assertEquals("Zırva, it's over.", runBlocking { e.translate("Crap, it's over.") })
    }
    @Test fun lostPlaceholderFallsBackToPlainEnglishThenTurkishifies() {
        val dropper = object : OfflineTranslationEngine {
            val calls = ArrayList<String>()
            override suspend fun translate(normalizedEnglish: String): String {
                calls += normalizedEnglish
                return normalizedEnglish.replace(Regex("Zq[a-z]"), "")
            }
        }
        val e = EnhancedOfflineTranslationEngine(dropper)
        val out = runBlocking { e.translate("Clean the crap and the shit now.") }
        assertEquals(2, dropper.calls.size)
        assertFalse(Regex("(?i)\\bshit\\b").containsMatchIn(out))
        assertTrue(out, out.contains("bok"))
    }
    @Test fun longSubtitleKeepsSentenceOrderAndCaches() {
        val long = "Listen carefully. We move at dawn. Nobody talks. Nobody runs!"
        val first = g(long)
        assertEquals("<<ml:Listen carefully.>> <<ml:We move at dawn.>> <<ml:Nobody talks.>> <<ml:Nobody runs!>>".replace("<<ml:Listen carefully.>>", "Dinle <<ml:carefully.>>").let { first }, first)
        val n = ml2.calls.size
        assertEquals(first, g(long))
        assertEquals(n, ml2.calls.size) // ikinci kez ML Kit çağrılmadı
    }
    @Test fun commonProfanityPhrasesNeedNoMlKit() {
        assertEquals("Defol buradan!", g("Get the fuck out of here!"))
        assertEquals("Seni orospu çocuğu!", g("You son of a bitch!"))
        assertEquals("Dalga mı geçiyorsun be?", g("Are you fucking kidding me?"))
        assertTrue(ml2.calls.isEmpty())
    }
    @Test fun gameNamesAreNotProtectedInGeneralMode() {
        val gl = SubtitleGlossary.default()
        assertFalse(gl.isName("lu"))
        assertTrue(SubtitleGlossary.default("@Lu").isName("lu"))
    }
}

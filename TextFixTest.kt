package com.example.subtitles

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFixTest {
    @Test fun fixesWholesale() = assertEquals("At wholesale prices.", TextFix.onar("At who lesale prices."))
    @Test fun fixesBadTCharacter() = assertEquals("It's one you will own.", TextFix.onar("Iť's one you will own."))
    @Test fun fixesForeign() = assertEquals("to foreign powers!", TextFix.onar("to for eign powers!"))
    @Test fun fixesPlaying() = assertEquals("(tense music playing", TextFix.onar("(tense music play ingl"))
    @Test fun fixesThereLl() = assertEquals("There'll be guests.", TextFix.onar("The re'll be guests."))
}

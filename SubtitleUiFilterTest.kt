package com.example.subtitles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleUiFilterTest {
    @Test fun filtersControllerAndShopPromptsFromRealLogs() {
        listOf(
            "See Shop Exit",
            ") Exit See Shop",
            "A Wait.",
            "A Wat",
            "A Carry",
            "A) Carry",
            "(A)) Carry",
            "L2 Observe O World Map",
            "World Map Observe",
            "Use O to Exit the Shop",
            "R1 Heavy Strike",
            "▲ Customize"
        ).forEach { assertTrue(it, SubtitleUiFilter.shouldIgnore(it)) }
    }

    @Test fun keepsActualDialogue() {
        listOf(
            "Another time no doubt.",
            "What might you be requiring?",
            "Perhaps t'is but postponed.",
            "What's on your mind then?",
            "He never takes it off.",
            "A gold Locket you say?"
        ).forEach { assertFalse(it, SubtitleUiFilter.shouldIgnore(it)) }
    }
}

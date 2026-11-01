package com.example.subtitles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleUiFilterTest {
    @Test
    fun filtersControllerAndMenuPromptsFromRealLogs() {
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
        ).forEach { input ->
            assertTrue(input, SubtitleUiFilter.shouldIgnore(input))
        }
    }

    @Test
    fun keepsActualDialogueFromRealLogs() {
        listOf(
            "Another time no doubt.",
            "What might you be requiring?",
            "Perhaps t'is but postponed.",
            "What's on your mind then?",
            "He never takes it off.",
            "A gold Locket you say?",
            "So, what'll you be needing?",
            "What can I do for you?"
        ).forEach { input ->
            assertFalse(input, SubtitleUiFilter.shouldIgnore(input))
        }
    }
}

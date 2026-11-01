package com.example.subtitles

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicPanelIntegrationTest {
    private fun block(text: String, rect: Rect, w: Int = 1920, h: Int = 1080) = OcrTextBlock(
        id = text,
        text = text,
        bounds = rect,
        lines = text.lines().mapIndexed { i, line ->
            OcrTextLine(line, Rect(rect.left, rect.top + i * 28, rect.right, rect.top + (i + 1) * 28))
        },
        frameWidth = w,
        frameHeight = h
    )

    @Test fun leftAndRightPanelsAreDetectedWithoutHardcodedText() {
        val detector = DynamicPanelDetector()
        val result = detector.detect(listOf(
            block("Objective\nReach the harbor", Rect(40, 150, 430, 230)),
            block("Inventory\nDamage 17", Rect(1460, 180, 1870, 260))
        ))
        assertEquals(2, result.size)
        assertTrue(result.any { it.bounds.centerX() < 960 })
        assertTrue(result.any { it.bounds.centerX() > 960 })
    }

    @Test fun bottomCenterDialogueIsNotPromotedToPanel() {
        val detector = DynamicPanelDetector()
        val result = detector.detect(listOf(
            block("Where are you going?", Rect(570, 760, 1350, 830))
        ))
        assertTrue(result.isEmpty())
    }

    @Test fun panelMustRemainStableBeforeTranslationCandidateAppears() {
        val tracker = PanelTracker(stableFrames = 2)
        val candidate = DynamicPanelDetector.PanelCandidate(
            Rect(1400, 120, 1820, 260), emptyList(), "Find the target"
        )
        assertTrue(tracker.update(listOf(candidate)).stable.isEmpty())
        val second = tracker.update(listOf(candidate)).stable
        assertEquals(1, second.size)
        assertEquals("Find the target", second.single().sourceText)
    }

    @Test fun disappearingPanelIsRemovedAfterGraceFrames() {
        val tracker = PanelTracker(stableFrames = 1, removeAfterMissingFrames = 1)
        val candidate = DynamicPanelDetector.PanelCandidate(
            Rect(70, 110, 440, 240), emptyList(), "Mission text"
        )
        val id = tracker.update(listOf(candidate)).stable.single().id
        assertTrue(tracker.update(emptyList()).removedIds.isEmpty())
        assertEquals(listOf(id), tracker.update(emptyList()).removedIds)
    }

    @Test fun controllerSymbolsAreSeparatedAndRestored() {
        val playstation = ControllerPromptParser.parse("▲ Customize")
        assertEquals("▲", playstation.prefix)
        assertEquals("Customize", playstation.label)
        assertEquals("▲ Özelleştir", ControllerPromptParser.restore(playstation.prefix, "Özelleştir"))

        val shoulder = ControllerPromptParser.parse("R1 Heavy Strike")
        assertEquals("R1", shoulder.prefix)
        assertEquals("Heavy Strike", shoulder.label)
    }

    @Test fun translationCacheUsesNormalizedSourceText() {
        val cache = PanelTranslationCache()
        cache.put("Find   the target", "Hedefi bul")
        assertEquals("Hedefi bul", cache.get("find the target"))
    }
}

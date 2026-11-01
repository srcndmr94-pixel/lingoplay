package com.example.subtitles
import org.junit.Assert.*
import org.junit.Test

class FragmentIntegrationRegressionTest {
    @Test fun commaContinuationMerges() {
        val b = SubtitleFragmentBuffer()
        assertTrue(b.offer("example of Triad technology,") is SubtitleFragmentBuffer.Result.AwaitNext)
        assertEquals(
            SubtitleFragmentBuffer.Result.Ready("example of Triad technology, but the real miracle is the power source."),
            b.offer("but the real miracle is the power source.")
        )
    }

    @Test fun finishedSentenceAndLowercaseNextRemainSeparate() {
        val b = SubtitleFragmentBuffer()
        assertEquals(SubtitleFragmentBuffer.Result.Ready("but the real miracle is the power source."), b.offer("but the real miracle is the power source."))
        assertEquals(SubtitleFragmentBuffer.Result.Ready("and easy to produce."), b.offer("and easy to produce."))
    }

    @Test fun titleLikeNextLineDoesNotMerge() {
        val b = SubtitleFragmentBuffer()
        b.offer("of the impending")
        assertEquals(SubtitleFragmentBuffer.Result.Ready("of the impending"), b.offer("Dr. Sutton."))
        assertEquals("Dr. Sutton.", b.pollDeferred())
    }

    @Test fun soundCaptionDoesNotMergeWithPendingDialogue() {
        val b = SubtitleFragmentBuffer()
        b.offer("to the")
        assertEquals(SubtitleFragmentBuffer.Result.Ready("to the"), b.offer("[dramatic music playing"))
        assertEquals("[dramatic music playing", b.pollDeferred())
    }
}

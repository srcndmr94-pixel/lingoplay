package com.example.subtitles
import org.junit.Assert.*
import org.junit.Test
class SubtitleFragmentBufferTest{
 @Test fun validContinuationMerges(){val b=SubtitleFragmentBuffer();assertTrue(b.offer("of the impending") is SubtitleFragmentBuffer.Result.AwaitNext);assertEquals(SubtitleFragmentBuffer.Result.Ready("of the impending attack on the facility."),b.offer("attack on the facility."))}
 @Test fun independentSentenceIsDeferred(){val b=SubtitleFragmentBuffer();b.offer("of the impending");assertEquals(SubtitleFragmentBuffer.Result.Ready("of the impending"),b.offer("She will attack it then."));assertEquals("She will attack it then.",b.pollDeferred())}
 @Test fun queueIsBounded(){val b=SubtitleFragmentBuffer();b.offer("of the impending");for(i in 0..30)b.offer("Sentence $i.");assertTrue(b.deferredCount()<=8)}
}

package com.example.subtitles
import java.util.concurrent.*
import org.junit.Assert.*
import org.junit.Test
class SubtitleFragmentBufferConcurrencyTest{
 @Test fun concurrentOffersDoNotCorrupt(){val b=SubtitleFragmentBuffer();val ex=Executors.newFixedThreadPool(8);val start=CountDownLatch(1);val done=CountDownLatch(100);val errors=ConcurrentLinkedQueue<Throwable>();repeat(100){i->ex.execute{try{start.await();b.offer(if(i%2==0)"of the impending" else "attack on the facility.")}catch(t:Throwable){errors.add(t)}finally{done.countDown()}}};start.countDown();assertTrue(done.await(5,TimeUnit.SECONDS));ex.shutdownNow();assertTrue(errors.isEmpty());assertTrue(b.deferredCount()<=8)}
 @Test fun concurrentPollReturnsAtMostOnce(){val b=SubtitleFragmentBuffer();b.offer("of the impending");b.offer("Dr. Sutton.");b.offer("However will you get there in time?");val r=ConcurrentLinkedQueue<String>();val ex=Executors.newFixedThreadPool(8);val done=CountDownLatch(8);repeat(8){ex.execute{try{b.pollDeferred()?.let(r::add)}finally{done.countDown()}}};assertTrue(done.await(5,TimeUnit.SECONDS));ex.shutdownNow();assertEquals(r.size,r.toSet().size)}
}

package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ToneEngineTest {

    private val sampleRate = ToneSynthesizer.SAMPLE_RATE

    /** What the sink was handed, in order. */
    private fun phrase(noteMs: Int = 40, gapMs: Int = 0, lastNoteMs: Int = noteMs) =
        Phrase(Melodies.SOLVE_SCALE, noteMs, gapMs, lastNoteMs)

    @Test
    fun aPhraseReachesTheSinkAsOneBuffer() {
        val sink = RecordingSink()
        val engine = ToneEngine(sink, sampleRate, directExecutor)

        engine.play(phrase(noteMs = 40, gapMs = 10))

        assertEquals(1, sink.played.size)
        val expected = ToneSynthesizer.samplesFor(50, sampleRate) * (Melodies.SOLVE_SCALE.size - 1) +
            ToneSynthesizer.samplesFor(40, sampleRate)
        assertEquals(expected, sink.sampleCounts.single())
    }

    @Test
    fun thePhraseIsRenderedOnceAndReused() {
        val sink = RecordingSink()
        val engine = ToneEngine(sink, sampleRate, directExecutor)
        val same = phrase()

        engine.play(same)
        val first = sink.played.single()
        engine.play(same)

        assertEquals(2, sink.played.size)
        assertTrue(
            "the same phrase should be handed over from the cache, not rendered again",
            first === sink.played[1]
        )
    }

    @Test
    fun everyPhraseIsPlayed() {
        val sink = RecordingSink()
        val engine = ToneEngine(sink, sampleRate, directExecutor)

        repeat(3) { engine.play(Phrase(listOf(Pitch.C4), noteMs = 20)) }

        assertEquals(3, sink.played.size)
        assertEquals(
            List(3) { ToneSynthesizer.samplesFor(20, sampleRate) },
            sink.sampleCounts
        )
    }

    @Test(timeout = 10_000)
    fun playDoesNotBlockTheCaller() {
        // a sink that takes 400 ms per phrase, on the engine's own worker
        val sink = RecordingSink(delayMs = 400)
        val engine = ToneEngine(sink, sampleRate)

        val start = System.nanoTime()
        engine.play(phrase(noteMs = 200, gapMs = 80, lastNoteMs = 1_200))
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(
            "play() blocked for $elapsedMs ms; audio must never hold up the caller",
            elapsedMs < 100
        )
        engine.shutdown()
    }

    @Test(timeout = 10_000)
    fun aSecondPhraseDoesNotWaitForTheFirstToStopRinging() {
        val bothStarted = CountDownLatch(2)
        val slowSink = object : AudioSink {
            override fun play(samples: ShortArray, sampleRate: Int) {
                bothStarted.countDown()
                Thread.sleep(300) // as long as a note rings
            }
        }
        val engine = ToneEngine(slowSink, sampleRate)

        engine.play(Phrase(listOf(Pitch.C4), noteMs = 50))
        engine.play(Phrase(listOf(Pitch.D4), noteMs = 50))

        assertTrue(
            "the second phrase had to wait for the first one to finish ringing",
            bothStarted.await(250, TimeUnit.MILLISECONDS)
        )
        engine.shutdown()
    }

    @Test
    fun aFailingAudioDeviceIsSwallowed() {
        val exploding = object : AudioSink {
            override fun play(samples: ShortArray, sampleRate: Int) = error("no audio device")
        }
        val engine = ToneEngine(exploding, sampleRate, directExecutor)

        engine.play(Phrase(Melodies.KEYPAD_NOTES, noteMs = 10))

        engine.shutdown() // nothing thrown, nothing left behind
    }

    @Test
    fun shutdownReleasesTheSink() {
        val sink = RecordingSink()
        ToneEngine(sink, sampleRate, directExecutor).shutdown()
        assertTrue(sink.released)
    }
}

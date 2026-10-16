package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneEngineTest {

    private val sampleRate = ToneSynthesizer.SAMPLE_RATE

    @Test
    fun aPhraseReachesTheSinkAsOneBuffer() {
        val sink = RecordingSink()
        val engine = ToneEngine(sink, sampleRate, directExecutor)

        engine.play(Melodies.SOLVE_SCALE, noteMs = 40, gapMs = 10)

        assertEquals(1, sink.played.size)
        assertEquals(
            ToneSynthesizer.samplesFor(50, sampleRate) * Melodies.SOLVE_SCALE.size,
            sink.sampleCounts.single()
        )
    }

    @Test
    fun everyPhraseIsPlayed() {
        val sink = RecordingSink()
        val engine = ToneEngine(sink, sampleRate, directExecutor)

        repeat(3) { engine.play(listOf(Pitch.C4), noteMs = 20, gapMs = 0) }

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
        engine.play(Melodies.SOLVE_SCALE, noteMs = 200, gapMs = 80)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(
            "play() blocked for $elapsedMs ms; audio must never hold up the caller",
            elapsedMs < 100
        )
        engine.shutdown()
    }

    @Test
    fun aFailingAudioDeviceIsSwallowed() {
        val exploding = object : AudioSink {
            override fun play(samples: ShortArray, sampleRate: Int) = error("no audio device")
        }
        val engine = ToneEngine(exploding, sampleRate, directExecutor)

        engine.play(Melodies.KEYPAD_NOTES, noteMs = 10, gapMs = 0)

        engine.shutdown() // nothing thrown, nothing left behind
    }

    @Test
    fun shutdownReleasesTheSink() {
        val sink = RecordingSink()
        ToneEngine(sink, sampleRate, directExecutor).shutdown()
        assertTrue(sink.released)
    }
}

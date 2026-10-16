package com.a24solver.wakusei.sound

import java.util.concurrent.Executor

/**
 * A sink that remembers what it was asked to play.
 *
 * Shared by the sound tests: it lets a test see exactly which buffers reached the audio device,
 * both directly ([ToneEngineTest]) and through the whole user interface (`KeypadSoundTest`).
 */
internal class RecordingSink(private val delayMs: Long = 0) : AudioSink {

    val played = mutableListOf<ShortArray>()
    var released = false
        private set

    val sampleCounts: List<Int> get() = played.map { it.size }

    /** Which of [candidates] the [index]-th phrase starts with, by comparing actual samples. */
    fun matches(buffer: ShortArray, candidate: ShortArray): Boolean = buffer.contentEquals(candidate)

    override fun play(samples: ShortArray, sampleRate: Int) {
        if (delayMs > 0) Thread.sleep(delayMs)
        played += samples
    }

    override fun release() {
        released = true
    }
}

/** Runs everything on the calling thread, so a test sees playback the moment `play` returns. */
internal val directExecutor = Executor { it.run() }

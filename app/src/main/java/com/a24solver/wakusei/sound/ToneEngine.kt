package com.a24solver.wakusei.sound

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Plays a phrase of notes on background workers.
 *
 * [play] never blocks the caller and never waits for the sound: rendering happens on a worker (from
 * [PhraseCache], so it is usually a lookup) and the sink starts a voice straight away. Nothing is
 * queued behind a note that is still ringing — that is the sink's business, and it overlaps notes
 * rather than cutting them off.
 *
 * Failures are swallowed on purpose: a device without a usable audio output must not be able to
 * break the puzzle.
 */
class ToneEngine(
    private val sink: AudioSink,
    private val sampleRate: Int = ToneSynthesizer.SAMPLE_RATE,
    worker: Executor? = null,
    private val cache: PhraseCache = PhraseCache(sampleRate = sampleRate)
) {

    private val executor: Executor = worker ?: ThreadPoolExecutor(
        WORKERS,
        WORKERS,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_BACKLOG),
        { runnable ->
            Thread(runnable, "keypad-sound").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        },
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    /** Renders (or looks up) `phrase` and starts it. Returns immediately. */
    fun play(phrase: Phrase) {
        executor.execute {
            runCatching { sink.play(cache.samples(phrase), sampleRate) }
        }
    }

    /** Convenience for the common "one note" and "a scale with a long final note" cases. */
    fun play(pitches: List<Pitch>, noteMs: Int, gapMs: Int, lastNoteMs: Int = noteMs): Unit =
        play(Phrase(pitches.toList(), noteMs, gapMs, lastNoteMs))

    fun shutdown() {
        (executor as? ExecutorService)?.shutdownNow()
        runCatching { sink.release() }
    }

    private companion object {
        /** How many phrases may wait for a worker before the oldest is dropped. */
        const val MAX_BACKLOG = 4

        /** Two workers, so one phrase can be rendering while another starts. */
        const val WORKERS = 2
    }
}

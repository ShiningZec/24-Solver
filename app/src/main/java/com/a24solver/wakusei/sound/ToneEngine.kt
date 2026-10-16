package com.a24solver.wakusei.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Where rendered PCM ends up.
 *
 * The engine only ever talks to this interface, so tests can watch playback — and count notes —
 * without an audio device, and without the engine knowing anything about it.
 *
 * [play] returns as soon as the note has *started*; it is not expected to wait for it to finish.
 */
interface AudioSink {

    /** Starts [samples] (mono, 16-bit) at [sampleRate]. Does not block until it has finished. */
    fun play(samples: ShortArray, sampleRate: Int)

    /** Frees whatever the sink holds. Called once, when the engine is shut down. */
    fun release() {}
}

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

/**
 * The real sink: a fixed set of [AudioTrack]s, one per voice, so notes ring into each other.
 *
 * Each voice is a static track — the whole note is written up front and the track just plays it,
 * which is the cheapest way to start a note *now*: no streaming buffer sits between the key press
 * and the sound. Starting a note never stops another one; a voice is only reused once its note would
 * have finished, or — when all [voiceCount] voices are ringing — the voice that is closest to
 * falling silent, because losing the oldest tail is much less noticeable than losing the note just
 * played.
 *
 * Sounds go to the *media* stream ([AudioAttributes.USAGE_MEDIA]), the stream a musical toy belongs
 * on, so the volume keys and the user's media volume control it.
 *
 * Note that overlapping notes are mixed by the system, not here: with several voices ringing at once
 * the sum can momentarily reach full scale. Keeping the per-note peak where it is and capping the
 * voices is the trade this sink makes for a keypad that never delays a note — see the README.
 */
class PolyphonicAudioSink(val voiceCount: Int = DEFAULT_VOICE_COUNT) : AudioSink {

    private val lock = Any()
    private val pool = VoicePool(voiceCount)
    private val voices = arrayOfNulls<AudioTrack>(voiceCount)

    override fun play(samples: ShortArray, sampleRate: Int) {
        synchronized(lock) {
            val now = SystemClock.uptimeMillis()
            val durationMs = samples.size * 1000L / sampleRate
            val voice = pool.acquire(now, durationMs)

            val track = trackFor(voice, samples.size, sampleRate)
            // a static track has to be stopped before its buffer may be rewritten
            runCatching { track.stop() }
            track.write(samples, 0, samples.size)
            track.play()
        }
    }

    /** How many voices are ringing right now; the tests and diagnostics use it. */
    fun soundingVoices(): Int = synchronized(lock) { pool.sounding(SystemClock.uptimeMillis()) }

    override fun release() {
        synchronized(lock) {
            voices.forEach { track ->
                track ?: return@forEach
                runCatching { track.stop() }
                runCatching { track.release() }
            }
            voices.fill(null)
            pool.reset()
        }
    }

    /**
     * The voice's track, built on first use and rebuilt only if a longer note arrives than it has
     * room for. The buffer is sized to the note, so the first tap of a new length pays for one track
     * — a few milliseconds, once.
     */
    private fun trackFor(voice: Int, sampleCount: Int, sampleRate: Int): AudioTrack {
        val required = sampleCount * Short.SIZE_BYTES
        val existing = voices[voice]
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED &&
            existing.bufferSizeInFrames * Short.SIZE_BYTES >= required
        ) {
            return existing
        }
        existing?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        return buildTrack(required, sampleRate).also { voices[voice] = it }
    }

    private fun buildTrack(bufferSizeBytes: Int, sampleRate: Int): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bufferSizeBytes)
            .build()

    companion object {
        /**
         * Six voices: enough for a scale to ring over itself and for a quick run of key presses,
         * without holding more audio buffers than a phone needs for a keypad.
         */
        const val DEFAULT_VOICE_COUNT = 6
    }
}

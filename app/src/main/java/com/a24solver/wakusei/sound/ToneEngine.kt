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
 */
interface AudioSink {

    /** Plays [samples] (mono, 16-bit) at [sampleRate]. Blocks until the sound has finished. */
    fun play(samples: ShortArray, sampleRate: Int)

    /** Frees whatever the sink holds. Called once, when the engine is shut down. */
    fun release() {}
}

/**
 * Plays a phrase of notes on a background worker.
 *
 * [play] never blocks the caller: it renders nothing on the calling thread and hands the work to a
 * single worker, which is what keeps a key press from ever waiting on audio. The worker queue is
 * deliberately short and keeps the *newest* phrases, so hammering the keypad plays the latest notes
 * instead of falling further and further behind.
 *
 * Failures are swallowed on purpose: a device without a usable audio output must not be able to
 * break the puzzle.
 */
class ToneEngine(
    private val sink: AudioSink,
    private val sampleRate: Int = ToneSynthesizer.SAMPLE_RATE,
    worker: Executor? = null
) {

    private val executor: Executor = worker ?: ThreadPoolExecutor(
        1,
        1,
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

    /** Renders and plays `pitches` with `gapMs` of silence after each note. Returns immediately. */
    fun play(pitches: List<Pitch>, noteMs: Int, gapMs: Int) {
        val phrase = pitches.toList()
        executor.execute {
            runCatching { sink.play(ToneSynthesizer.sequence(phrase, noteMs, gapMs, sampleRate), sampleRate) }
        }
    }

    fun shutdown() {
        (executor as? ExecutorService)?.shutdownNow()
        runCatching { sink.release() }
    }

    private companion object {
        /** How many phrases may wait for the worker before the oldest is dropped. */
        const val MAX_BACKLOG = 4
    }
}

/**
 * The real sink: one short-lived [AudioTrack] per phrase, in static mode, released once the sound
 * has rung out.
 *
 * Static mode is the right fit for a note of a few hundred milliseconds — the whole buffer is
 * written up front and the track just plays it. Sounds go to the *media* stream
 * ([AudioAttributes.USAGE_MEDIA]), the stream a musical toy belongs on, so the volume keys and the
 * user's media volume control it.
 */
class AudioTrackSink : AudioSink {

    private var playing: AudioTrack? = null

    override fun play(samples: ShortArray, sampleRate: Int) {
        stop()
        val track = AudioTrack.Builder()
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
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .build()

        track.write(samples, 0, samples.size)
        playing = track
        track.play()
        SystemClock.sleep(samples.size * 1000L / sampleRate + RING_OUT_MARGIN_MS)
        stop()
    }

    override fun release() = stop()

    private fun stop() {
        playing?.let { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        playing = null
    }

    private companion object {
        /** A little slack after the buffer, so the tail is not cut off. */
        const val RING_OUT_MARGIN_MS = 20L
    }
}

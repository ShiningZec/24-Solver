package com.a24solver.wakusei.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock

/**
 * One voice's output: a buffer of PCM that can be started, stopped and thrown away.
 *
 * It exists so [PolyphonicAudioSink]'s voice handling — the part that decides which audio buffer a
 * note lands in — can be tested without an audio device. [AudioTrack] itself is the only
 * implementation the app uses.
 */
internal interface ToneTrack {

    /**
     * How many bytes of audio the track will play.
     *
     * This is the whole buffer, not the last write, which is why the sink rebuilds a track whose
     * size does not match the note it is about to play.
     */
    val bufferSizeInBytes: Int

    fun write(samples: ShortArray)

    fun play()

    fun stop()

    fun release()
}

/** [ToneTrack] backed by a static [AudioTrack]: the whole note is written up front. */
internal class AudioTrackToneTrack(private val track: AudioTrack) : ToneTrack {

    override val bufferSizeInBytes: Int get() = track.bufferSizeInFrames * Short.SIZE_BYTES

    override fun write(samples: ShortArray) {
        track.write(samples, 0, samples.size)
    }

    override fun play() = track.play()

    override fun stop() {
        runCatching { track.stop() }
    }

    override fun release() {
        runCatching { track.release() }
    }

    companion object {
        /** Builds a mono 16-bit track whose buffer holds exactly [bufferSizeBytes]. */
        fun create(bufferSizeBytes: Int, sampleRate: Int): ToneTrack {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // the media stream: a musical toy belongs there, and the volume keys control it
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
            return AudioTrackToneTrack(track)
        }
    }
}

/**
 * The real sink: a fixed set of voices, each with its own audio track, so notes ring into each other
 * instead of cutting one another off.
 *
 * Starting a note never stops another voice; a voice is only reused once its note would have
 * finished, or — when all [voiceCount] voices are ringing — the voice closest to falling silent,
 * because losing the oldest tail is much less noticeable than losing the note just played.
 *
 * ### Why a voice's track is rebuilt when the note length changes
 *
 * A static [AudioTrack] plays *its buffer*, so a voice that last played a three-second scale still
 * holds that scale after a shorter note has been written into the front of it. Reusing such a track
 * for a 750 ms keypad tone leaves the rest of the old scale in the buffer, and it plays: the tone is
 * followed by a ghost of the scale that came before it. The sink therefore reuses a voice's track
 * only when its buffer is *exactly* the size of the note about to be written; otherwise the track is
 * thrown away and a new one built. Tracking the size this way costs one track construction whenever
 * a keypad tone follows a scale (a few milliseconds, on a background worker) and removes the whole
 * class of leftovers.
 */
internal class PolyphonicAudioSink(
    val voiceCount: Int = DEFAULT_VOICE_COUNT,
    private val createTrack: (bufferSizeBytes: Int, sampleRate: Int) -> ToneTrack =
        AudioTrackToneTrack::create,
    private val nowMs: () -> Long = { SystemClock.uptimeMillis() }
) : AudioSink {

    private val lock = Any()
    private val pool = VoicePool(voiceCount)
    private val voices = arrayOfNulls<ToneTrack>(voiceCount)

    override fun play(samples: ShortArray, sampleRate: Int) {
        synchronized(lock) {
            val durationMs = samples.size * 1000L / sampleRate
            val voice = pool.acquire(nowMs(), durationMs)

            val track = trackFor(voice, samples.size, sampleRate)
            track.stop() // a static track has to be stopped before its buffer may be rewritten
            track.write(samples)
            track.play()
        }
    }

    /** How many voices are ringing right now; diagnostics and tests use it. */
    fun soundingVoices(): Int = synchronized(lock) { pool.sounding(nowMs()) }

    override fun release() {
        synchronized(lock) {
            voices.forEach { track ->
                track ?: return@forEach
                track.stop()
                track.release()
            }
            voices.fill(null)
            pool.reset()
        }
    }

    private fun trackFor(voice: Int, sampleCount: Int, sampleRate: Int): ToneTrack {
        val requiredBytes = sampleCount * Short.SIZE_BYTES
        val existing = voices[voice]
        if (existing != null && existing.bufferSizeInBytes == requiredBytes) return existing

        existing?.let {
            it.stop()
            it.release()
        }
        return createTrack(requiredBytes, sampleRate).also { voices[voice] = it }
    }

    companion object {
        /**
         * Six voices: enough for a phrase to ring over itself and for a quick run of key presses,
         * without holding more audio buffers than a phone needs for a keypad.
         */
        const val DEFAULT_VOICE_COUNT = 6
    }
}

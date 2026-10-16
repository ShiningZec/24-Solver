package com.a24solver.wakusei.sound

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates the keypad's notes as raw PCM.
 *
 * The timbre is a struck-bar instrument (xylophone/marimba family): a fundamental plus two much
 * quieter upper modes — real bars are tuned so the second mode is roughly two octaves up and the
 * third about three octaves up — with an instant attack and an exponential decay, the higher modes
 * dying away first. Everything is computed from sine waves, so no sample data and no licence is
 * involved anywhere in the app.
 *
 * Pure and deterministic: no Android types, no I/O, no randomness. That is what lets the tests pin
 * the waveform down (length, peak, decay, measured frequency) on a bare JVM.
 */
object ToneSynthesizer {

    const val SAMPLE_RATE = 44_100

    /** Peak amplitude of a single note, leaving plenty of headroom below full scale. */
    private const val PEAK = 0.55

    /** Relative strength of the three bar modes. */
    private val MODE_AMPLITUDES = doubleArrayOf(1.0, 0.28, 0.09)

    /** How much faster each higher mode decays than the fundamental. */
    private val MODE_DECAY_FACTORS = doubleArrayOf(1.0, 1.9, 2.7)

    /** Frequency multiplier of each mode relative to the fundamental. */
    private val MODE_RATIOS = doubleArrayOf(1.0, 4.0, 9.9)

    private const val ATTACK_SECONDS = 0.004
    private const val RELEASE_SECONDS = 0.012

    fun samplesFor(durationMs: Int, sampleRate: Int = SAMPLE_RATE): Int =
        durationMs * sampleRate / 1000

    /**
     * One struck note: [durationMs] of audio at [sampleRate], normalised so it never clips.
     */
    fun note(pitch: Pitch, durationMs: Int, sampleRate: Int = SAMPLE_RATE): ShortArray {
        require(durationMs > 0) { "A note must last longer than nothing" }
        val count = samplesFor(durationMs, sampleRate)
        val out = ShortArray(count)
        val duration = count.toDouble() / sampleRate
        // hoisted: Pitch.frequencyHz is computed, and this loop runs once per sample per mode
        val fundamental = pitch.frequencyHz
        // the fundamental's amplitude falls by e^-1 over a third of the note
        val decayTau = duration / 3.0
        val amplitudeSum = MODE_AMPLITUDES.sum()
        val peak = PEAK * Short.MAX_VALUE / amplitudeSum

        for (index in 0 until count) {
            val seconds = index.toDouble() / sampleRate
            var sample = 0.0
            for (mode in MODE_RATIOS.indices) {
                val modeTau = decayTau / MODE_DECAY_FACTORS[mode]
                sample += MODE_AMPLITUDES[mode] *
                    exp(-seconds / modeTau) *
                    sin(2.0 * PI * fundamental * MODE_RATIOS[mode] * seconds)
            }
            out[index] = (sample * peak * envelope(seconds, duration)).toInt().toShort()
        }
        return out
    }

    /** A [durationMs] stretch of silence. */
    fun silence(durationMs: Int, sampleRate: Int = SAMPLE_RATE): ShortArray =
        ShortArray(samplesFor(durationMs, sampleRate))

    /**
     * [pitches] played one after another, each `noteMs` long with `gapMs` of silence behind it,
     * except the last one, which lasts [lastNoteMs] — a longer final note lets a scale ring out
     * instead of stopping dead, and reads as a full stop. There is no silence after the last note:
     * the voice ends when the sound does.
     */
    fun phrase(
        pitches: List<Pitch>,
        noteMs: Int,
        gapMs: Int,
        lastNoteMs: Int = noteMs,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        require(pitches.isNotEmpty()) { "A phrase needs at least one note" }
        require(noteMs > 0 && gapMs >= 0 && lastNoteMs > 0) {
            "A phrase needs positive note lengths"
        }
        val step = samplesFor(noteMs + gapMs, sampleRate)
        val last = samplesFor(lastNoteMs, sampleRate)
        val out = ShortArray(step * (pitches.size - 1) + last)
        pitches.forEachIndexed { index, pitch ->
            val durationMs = if (index == pitches.lastIndex) lastNoteMs else noteMs
            note(pitch, durationMs, sampleRate).copyInto(out, index * step)
        }
        return out
    }

    /** Fades the first few milliseconds in and the last few out, so notes never click. */
    private fun envelope(seconds: Double, duration: Double): Double = when {
        seconds < ATTACK_SECONDS -> seconds / ATTACK_SECONDS
        duration - seconds < RELEASE_SECONDS -> (duration - seconds) / RELEASE_SECONDS
        else -> 1.0
    }
}

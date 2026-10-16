package com.a24solver.wakusei.sound

import kotlin.math.tanh

/**
 * Keeps a sum of notes inside what a 16-bit sample can hold, without touching anything quiet.
 *
 * A phrase whose notes overlap adds up, and adding up is how you get distortion: five notes at once
 * reach five times the loudness of one. Compressing the *sum* is the honest way out — below [KNEE]
 * the curve is the identity, so a single note (or a quiet passage) comes through exactly as it was
 * synthesised and nothing sounds squashed; above it the curve bends smoothly to a ceiling of full
 * scale, so a dense moment is squeezed rather than clipped, which is roughly what a real instrument
 * does to itself.
 *
 * The identity below the knee is what keeps a lone keypad tone bit-for-bit what it would have been.
 */
internal object SoftLimiter {

    /** Below this (in units of full scale) nothing is changed at all. */
    const val KNEE = 0.7

    /** [KNEE] in 16-bit sample units: the largest sum that passes through untouched. */
    const val KNEE_SAMPLE = (KNEE * Short.MAX_VALUE).toInt()

    /**
     * Maps [value] (which may be far outside ±1) into ±1.
     *
     * Note the ceiling is approached, not reached: a sum of 100 comes back as 1.0 minus an
     * infinitesimal, so `value * Short.MAX_VALUE` still fits in a `Short`.
     */
    fun limit(value: Double): Double = when {
        value > KNEE -> KNEE + (1.0 - KNEE) * tanh((value - KNEE) / (1.0 - KNEE))
        value < -KNEE -> -KNEE - (1.0 - KNEE) * tanh((-value - KNEE) / (1.0 - KNEE))
        else -> value
    }

    /**
     * The same curve for a sum of 16-bit samples.
     *
     * Anything inside the knee is returned **bit for bit**: going through ±1 and back would round
     * some samples by one, and a lone keypad tone has to stay exactly the note that was synthesised.
     */
    fun limitSample(sum: Int): Short = when {
        sum > KNEE_SAMPLE || sum < -KNEE_SAMPLE ->
            (limit(sum / Short.MAX_VALUE.toDouble()) * Short.MAX_VALUE).toInt().toShort()
        else -> sum.toShort()
    }
}

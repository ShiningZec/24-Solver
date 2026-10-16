package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ToneSynthesizerTest {

    private val sampleRate = 44_100

    @Test
    fun aNoteHasTheRequestedLength() {
        assertEquals(sampleRate / 10, ToneSynthesizer.samplesFor(100, sampleRate))
        assertEquals(
            100 * sampleRate / 1000,
            ToneSynthesizer.note(Pitch.A4, 100, sampleRate).size
        )
    }

    @Test
    fun aNoteIsAudibleAndNeverClips() {
        val samples = ToneSynthesizer.note(Pitch.A4, 300, sampleRate)
        val peak = samples.maxOf { abs(it.toInt()) }

        assertTrue("the note is silent", peak > Short.MAX_VALUE / 4)
        assertTrue("the note clips at $peak", peak <= Short.MAX_VALUE)
        assertTrue(samples.any { it != 0.toShort() })
    }

    @Test
    fun aNoteIsStruckAndThenDecays() {
        val samples = ToneSynthesizer.note(Pitch.C4, 400, sampleRate)
        val head = rms(samples, 0, samples.size / 10)
        val tail = rms(samples, samples.size * 9 / 10, samples.size)

        assertTrue("head rms $head should be well above tail rms $tail", head > tail * 4)
    }

    @Test
    fun aLongNoteDecaysToNearlyNothingBeforeItEnds() {
        // this is what lets notes ring into each other without a click: by the end of a 750 ms note
        // the amplitude is a few percent of the strike, and the last few milliseconds are faded out
        val samples = ToneSynthesizer.note(Pitch.C4, 750, sampleRate)
        val peak = samples.maxOf { abs(it.toInt()) }
        val tailPeak = samples.copyOfRange(samples.size - 2_000, samples.size)
            .maxOf { abs(it.toInt()) }

        assertTrue(
            "the tail still peaks at $tailPeak against $peak — the note would end audibly",
            tailPeak < peak / 15
        )
    }

    @Test
    fun theAttackStartsFromSilence() {
        val samples = ToneSynthesizer.note(Pitch.C4, 200, sampleRate)
        val firstWindow = rms(samples, 0, 40)

        // no click: the very first samples are near zero, then the strike builds up
        assertTrue("first samples jump out of nowhere", abs(samples[0].toInt()) < 200)
        assertTrue(firstWindow < rms(samples, 200, 400))
    }

    @Test
    fun theMeasuredFrequencyMatchesThePitch() {
        for (pitch in listOf(Pitch.C4, Pitch.G4, Pitch.C5)) {
            val samples = ToneSynthesizer.note(pitch, 200, sampleRate)
            val measured = estimateFrequency(samples, sampleRate)
            val error = abs(measured - pitch.frequencyHz) / pitch.frequencyHz
            assertTrue(
                "${pitch.noteName}: measured %.1f Hz, expected %.1f Hz".format(measured, pitch.frequencyHz),
                error < 0.02
            )
        }
    }

    /**
     * The fundamental period, by normalised autocorrelation over the first 100 ms.
     *
     * Counting zero crossings is not good enough here: the bar's upper modes add crossings of their
     * own, so the composite waveform would read high. Autocorrelation finds the period the whole
     * waveform repeats at, which is the fundamental.
     */
    private fun estimateFrequency(samples: ShortArray, sampleRate: Int): Double {
        val window = minOf(samples.size, sampleRate / 10)
        val minLag = sampleRate / 1200
        val maxLag = minOf(sampleRate / 150, window / 2)

        var bestLag = minLag
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var product = 0.0
            var energyA = 0.0
            var energyB = 0.0
            for (index in 0 until window - lag) {
                val a = samples[index].toDouble()
                val b = samples[index + lag].toDouble()
                product += a * b
                energyA += a * a
                energyB += b * b
            }
            val score = product / sqrt(energyA * energyB)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        return sampleRate.toDouble() / bestLag
    }

    @Test
    fun differentPitchesSoundDifferent() {
        val low = ToneSynthesizer.note(Pitch.C4, 100, sampleRate)
        val high = ToneSynthesizer.note(Pitch.C5, 100, sampleRate)
        assertNotEquals(low.toList(), high.toList())
    }

    @Test
    fun silenceIsSilent() {
        val silence = ToneSynthesizer.silence(50, sampleRate)
        assertEquals(sampleRate * 50 / 1000, silence.size)
        assertTrue(silence.all { it == 0.toShort() })
    }

    @Test
    fun aPhraseStartsItsNotesOneOnsetApart() {
        val noteMs = 50
        val onsetMs = 120
        val pitches = listOf(Pitch.C4, Pitch.E4, Pitch.G4)

        val phrase = ToneSynthesizer.phrase(pitches, noteMs, onsetMs, sampleRate = sampleRate)

        val onset = ToneSynthesizer.samplesFor(onsetMs, sampleRate)
        assertEquals(
            "the phrase ends when the last note does",
            onset * (pitches.size - 1) + ToneSynthesizer.samplesFor(noteMs, sampleRate),
            phrase.size
        )
        // nothing overlaps yet in the first onset, so it is the first note exactly
        assertTrue(
            "the first note should start the phrase unchanged",
            phrase.copyOfRange(0, ToneSynthesizer.samplesFor(noteMs, sampleRate))
                .contentEquals(ToneSynthesizer.note(pitches.first(), noteMs, sampleRate))
        )
    }

    @Test
    fun overlappingNotesAreMixedAndStayInsideTheSampleRange() {
        val pitches = Melodies.SOLVE_SCALE
        val phrase = ToneSynthesizer.phrase(
            pitches,
            noteMs = 450,
            onsetMs = 120,
            lastNoteMs = 1_200,
            sampleRate = sampleRate
        )

        val peak = phrase.maxOf { abs(it.toInt()) }
        assertTrue("the mix clipped at $peak", peak <= Short.MAX_VALUE)
        assertTrue("the mix went silent", peak > Short.MAX_VALUE / 2)

        // where notes overlap the output is the sum, not one note: the region between the first and
        // second attacks has to be louder than the tail of the first note alone
        val onset = ToneSynthesizer.samplesFor(120, sampleRate)
        val firstNote = ToneSynthesizer.note(pitches.first(), 450, sampleRate)
        val mixedWindow = phrase.copyOfRange(onset + 100, onset + 400)
        val aloneWindow = firstNote.copyOfRange(onset + 100, onset + 400)
        assertTrue(
            "the second note's attack is missing from the mix",
            mixedWindow.maxOf { abs(it.toInt()) } > aloneWindow.maxOf { abs(it.toInt()) }
        )
    }

    @Test
    fun aPhraseCanEndOnALongerNote() {
        val pitches = listOf(Pitch.C4, Pitch.E4, Pitch.G4)
        val lastNoteMs = 400
        val onsetMs = 60

        val phrase = ToneSynthesizer.phrase(pitches, noteMs = 50, onsetMs = onsetMs, lastNoteMs = lastNoteMs, sampleRate = sampleRate)

        val onset = ToneSynthesizer.samplesFor(onsetMs, sampleRate)
        assertEquals(onset * 2 + ToneSynthesizer.samplesFor(lastNoteMs, sampleRate), phrase.size)
        // the tail belongs to the last note, which is still ringing on its own by then
        val tailStart = phrase.size - 200
        assertEquals(
            "the long final note should be ringing at the end",
            true,
            phrase.copyOfRange(tailStart, phrase.size).any { it != 0.toShort() }
        )
    }

    @Test
    fun aSingleNotePhraseIsExactlyThatNote() {
        // the keypad tone must not be changed by any of the mixing machinery
        val phrase = ToneSynthesizer.phrase(listOf(Pitch.A4), noteMs = 300, onsetMs = 300, sampleRate = sampleRate)

        assertTrue(
            "a one-note phrase should be bit-for-bit the note itself",
            phrase.contentEquals(ToneSynthesizer.note(Pitch.A4, 300, sampleRate))
        )
    }

    @Test
    fun aPhraseNeedsNotesAndPositiveLengths() {
        assertThrows(IllegalArgumentException::class.java) {
            ToneSynthesizer.phrase(emptyList(), 100, 10, sampleRate = sampleRate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneSynthesizer.phrase(listOf(Pitch.C4), noteMs = 0, onsetMs = 0, sampleRate = sampleRate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneSynthesizer.phrase(listOf(Pitch.C4, Pitch.D4), noteMs = 100, onsetMs = 0, sampleRate = sampleRate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneSynthesizer.note(Pitch.C4, 0, sampleRate)
        }
    }

    private fun rms(samples: ShortArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (index in from until to) sum += samples[index].toDouble() * samples[index]
        return sqrt(sum / (to - from))
    }
}

package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * The pitch table: a complete chromatic scale from A♭3 to F#5, tuned in equal temperament.
 *
 * It is spelled out as MIDI numbers and the frequencies are derived, so what needs pinning down is
 * that the table is actually *complete* (no missing semitone, which would be invisible in the code
 * but audible the moment someone picks that note) and that the arithmetic is right.
 */
class PitchTest {

    @Test
    fun theTableCoversEverySemitoneFromAFlat3ToFSharp5() {
        val pitches = Pitch.entries

        assertEquals(23, pitches.size)
        assertEquals(56, pitches.first().midiNumber) // Ab3
        assertEquals(78, pitches.last().midiNumber)  // F#5
        assertEquals(
            "every MIDI number in the range must appear exactly once, in order",
            (56..78).toList(),
            pitches.map { it.midiNumber }
        )
        assertEquals("two notes share a name", pitches.size, pitches.map { it.noteName }.toSet().size)
        assertEquals(
            "two notes share a MIDI number",
            pitches.size,
            pitches.map { it.midiNumber }.toSet().size
        )
    }

    @Test
    fun theNotesAscendInPitch() {
        Pitch.entries.zipWithNext { lower, higher ->
            assertTrue(
                "${lower.noteName} (${lower.frequencyHz} Hz) should be below " +
                    "${higher.noteName} (${higher.frequencyHz} Hz)",
                lower.frequencyHz < higher.frequencyHz
            )
        }
    }

    @Test
    fun everyStepIsOneSemitone() {
        val semitoneRatio = 2.0.pow(1.0 / 12.0)

        Pitch.entries.zipWithNext { lower, higher ->
            val ratio = higher.frequencyHz / lower.frequencyHz
            assertEquals(
                "${lower.noteName} -> ${higher.noteName} is not a semitone",
                semitoneRatio,
                ratio,
                1e-12
            )
        }
    }

    @Test
    fun theFrequenciesMatchEqualTemperament() {
        // reference values from the standard table (A4 = 440 Hz), written out rather than derived
        val expected = mapOf(
            Pitch.A_FLAT3 to 207.6523,
            Pitch.A3 to 220.0000,
            Pitch.C4 to 261.6256,
            Pitch.E_FLAT4 to 311.1270,
            Pitch.A4 to 440.0000,
            Pitch.B_FLAT4 to 466.1638,
            Pitch.C5 to 523.2511,
            Pitch.E5 to 659.2551,
            Pitch.F_SHARP5 to 739.9888
        )

        assertEquals(440.0, Pitch.A4.frequencyHz, 1e-9)
        for ((pitch, reference) in expected) {
            assertEquals(
                "${pitch.noteName} should be $reference Hz",
                reference,
                pitch.frequencyHz,
                0.0001
            )
        }
    }

    @Test
    fun anOctaveIsExactlyDouble() {
        assertEquals(Pitch.C5.frequencyHz, Pitch.C4.frequencyHz * 2, 1e-9)
        assertEquals(Pitch.E5.frequencyHz, Pitch.E4.frequencyHz * 2, 1e-9)
        // the same pitch class under two spellings, an octave apart
        assertEquals(Pitch.G_SHARP4.frequencyHz, Pitch.A_FLAT3.frequencyHz * 2, 1e-9)
    }

    @Test
    fun theNotesTheKeypadUsesAreAllInTheTable() {
        val available = Pitch.entries.toSet()

        for (pitch in Melodies.KEYPAD_NOTES + Melodies.SOLVE_SCALE + Melodies.RESET_SCALE) {
            assertTrue("${pitch.noteName} is not part of the table", pitch in available)
        }
        assertTrue(
            "the lowest keypad note should be well inside the table",
            Melodies.KEYPAD_NOTES.minOf { it.frequencyHz } > Pitch.entries.first().frequencyHz
        )
    }

    @Test
    fun theSpellingsReadLikeNoteNames() {
        val pattern = Regex("^[A-G](#|b)?[0-9]$")
        for (pitch in Pitch.entries) {
            assertTrue(
                "${pitch.noteName} does not look like a note name",
                pattern.matches(pitch.noteName)
            )
        }
        // the octave number has to follow the pitch: C4 is above B3, the octave rolls over at C
        assertEquals("B3", Pitch.B3.noteName)
        assertEquals("C4", Pitch.C4.noteName)
        assertTrue(Pitch.B3.frequencyHz < Pitch.C4.frequencyHz)
        assertEquals("Ab3", Pitch.A_FLAT3.noteName)
        assertEquals("F#5", Pitch.F_SHARP5.noteName)
    }

    @Test
    fun theTableIsNotAccidentallyCentsOut() {
        // a semitone is ~5.9% in frequency; a typo in a hard-coded decimal would show up here
        val cents = 1200 * kotlin.math.log2(Pitch.F_SHARP5.frequencyHz / Pitch.A_FLAT3.frequencyHz)
        assertEquals("Ab3 to F#5 is 22 semitones", 2200.0, cents, 1e-6)
        assertTrue(abs(Pitch.A4.frequencyHz - 440.0) < 0.005)
    }
}

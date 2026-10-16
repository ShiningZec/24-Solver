package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The note material the app plays.
 *
 * These are the scales the app currently ships: they were tuned by hand out of [Pitch], so this
 * pins what is actually played rather than a specification — changing [Melodies] means changing
 * these expectations too, which is the point.
 */
class MelodiesTest {

    @Test
    fun theKeypadDrawsFromItsCurrentSet() {
        assertEquals(
            listOf("B3", "D4", "Eb4", "F4", "G4", "Ab4", "C5"),
            Melodies.KEYPAD_NOTES.map { it.noteName }
        )
    }

    @Test
    fun aRoundOpensOnTheLeadingNote() {
        assertEquals(Pitch.C4, Melodies.KEYPAD_LEADING_NOTE)
        assertTrue(
            "the leading note must not also be shuffled, or it could come round twice",
            Melodies.KEYPAD_LEADING_NOTE !in Melodies.KEYPAD_NOTES
        )
    }

    @Test
    fun solvePlaysItsCurrentScale() {
        assertEquals(
            listOf("C4", "Eb4", "F#4", "G4", "Bb4"),
            Melodies.SOLVE_SCALE.map { it.noteName }
        )
    }

    @Test
    fun theResetGesturePlaysItsCurrentArpeggio() {
        assertEquals(
            listOf("G4", "D4", "B3"),
            Melodies.RESET_SCALE.map { it.noteName }
        )
    }

    @Test
    fun thePitchesAreEqualTempered() {
        // A4 is the reference; an octave up is exactly twice the frequency
        assertEquals(440.0, Pitch.A4.frequencyHz, 0.01)
        assertEquals(Pitch.C4.frequencyHz * 2, Pitch.C5.frequencyHz, 0.01)
    }
}

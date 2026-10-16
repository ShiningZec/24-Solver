package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Test

/** The note material, pinned to what the specification asks for. */
class MelodiesTest {

    @Test
    fun theKeypadDrawsFromEightNotes() {
        // the specification's "C, D, E, sharp F, G, A, flat B, high C", in the fourth/fifth octave
        assertEquals(
            listOf("C4", "D4", "E4", "F#4", "G4", "A4", "Bb4", "C5"),
            Melodies.KEYPAD_NOTES.map { it.noteName }
        )
    }

    @Test
    fun solvePlaysTheSixNoteBluesScale() {
        // "C, flat E, sharp F, G, flat B, B"
        assertEquals(
            listOf("C4", "Eb4", "F#4", "G4", "Bb4", "B4"),
            Melodies.SOLVE_SCALE.map { it.noteName }
        )
    }

    @Test
    fun theResetGesturePlaysTheMajorThirdArpeggio() {
        // "E, sharp G, high C"
        assertEquals(
            listOf("E4", "G#4", "C5"),
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

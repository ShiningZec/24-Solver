package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Test

/** The note material, pinned to what the specification asks for. */
class MelodiesTest {

    @Test
    fun theKeypadDrawsFromEightNotes() {
        assertEquals(
            listOf("C", "D", "E", "F#", "G", "A", "Bb", "C5"),
            Melodies.KEYPAD_NOTES.map { it.noteName }
        )
    }

    @Test
    fun solvePlaysTheSixNoteBluesScale() {
        assertEquals(
            listOf("C", "Eb", "F#", "G", "Bb", "B"),
            Melodies.SOLVE_SCALE.map { it.noteName }
        )
    }

    @Test
    fun theResetGesturePlaysTheMajorThirdArpeggio() {
        assertEquals(
            listOf("E", "G#", "C5"),
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

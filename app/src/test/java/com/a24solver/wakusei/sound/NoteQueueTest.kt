package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NoteQueueTest {

    @Test
    fun everyNoteIsUsedOnceBeforeAnyRepeats() {
        val queue = NoteQueue(Melodies.KEYPAD_NOTES, Random(1))

        val firstRound = List(Melodies.KEYPAD_NOTES.size) { queue.next() }
        assertEquals(
            "a round must contain each note exactly once",
            Melodies.KEYPAD_NOTES.toSet(),
            firstRound.toSet()
        )
        assertEquals(Melodies.KEYPAD_NOTES.size, firstRound.size)

        val secondRound = List(Melodies.KEYPAD_NOTES.size) { queue.next() }
        assertEquals(Melodies.KEYPAD_NOTES.toSet(), secondRound.toSet())
    }

    @Test
    fun theOrderIsShuffledAndRerolled() {
        val queue = NoteQueue(Melodies.KEYPAD_NOTES, Random(7))
        val rounds = List(4) { List(Melodies.KEYPAD_NOTES.size) { queue.next() } }

        assertTrue(
            "the shuffled order should not be the specified order every round",
            rounds.any { it != Melodies.KEYPAD_NOTES }
        )
        assertNotEquals("two rounds in a row were identical", rounds[0], rounds[1])
    }

    @Test
    fun aQueueOwnsExactlyTheNotesItWasGiven() {
        val queue = NoteQueue(listOf(Pitch.C4, Pitch.G4), Random(3))
        val drawn = List(6) { queue.next() }
        assertTrue(drawn.all { it == Pitch.C4 || it == Pitch.G4 })
        assertEquals(setOf(Pitch.C4, Pitch.G4), drawn.take(2).toSet())
    }

    @Test
    fun resetStartsAFreshBag() {
        val queue = NoteQueue(Melodies.KEYPAD_NOTES, Random(5))
        queue.next()
        queue.reset()
        val round = List(Melodies.KEYPAD_NOTES.size) { queue.next() }
        assertEquals(Melodies.KEYPAD_NOTES.toSet(), round.toSet())
    }

    @Test
    fun anEmptyQueueIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { NoteQueue(emptyList()) }
    }
}

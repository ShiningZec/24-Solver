package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NoteQueueTest {

    private val bag = listOf(Pitch.D4, Pitch.E4, Pitch.F4)

    @Test
    fun everyNoteIsUsedOnceBeforeAnyRepeats() {
        val queue = NoteQueue(bag, Random(1))

        val firstRound = List(bag.size) { queue.next() }
        assertEquals(
            "a round must contain each note exactly once",
            bag.toSet(),
            firstRound.toSet()
        )

        val secondRound = List(bag.size) { queue.next() }
        assertEquals(bag.toSet(), secondRound.toSet())
    }

    @Test
    fun theOrderIsShuffledAndRerolled() {
        val notes = listOf(Pitch.C4, Pitch.D4, Pitch.E4, Pitch.F4, Pitch.G4, Pitch.A4)
        val queue = NoteQueue(notes, Random(7))
        val rounds = List(4) { List(notes.size) { queue.next() } }

        assertTrue(
            "the shuffled order should not be the given order every round",
            rounds.any { it != notes }
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
    fun aLeadingNoteOpensEveryRoundWithoutRepeatingInsideIt() {
        val rest = listOf(Pitch.B3, Pitch.D4, Pitch.F4, Pitch.C5)
        val queue = NoteQueue(rest, Random(11), leadingNote = Pitch.C4)

        repeat(3) {
            val round = List(rest.size + 1) { queue.next() }
            assertEquals("a round must open on the leading note", Pitch.C4, round.first())
            assertEquals(
                "the rest of the round is every other note, exactly once",
                rest.toSet(),
                round.drop(1).toSet()
            )
            assertEquals("nothing may repeat inside a round", round.size, round.toSet().size)
        }
    }

    @Test
    fun aLeadingNoteAlreadyInTheBagIsNotShuffledTwice() {
        // the trap this parameter exists to avoid: a leading note that is also one of the notes
        val notes = listOf(Pitch.C4, Pitch.D4, Pitch.E4)
        val queue = NoteQueue(notes, Random(5), leadingNote = Pitch.C4)

        val round = List(notes.size) { queue.next() }

        assertEquals(Pitch.C4, round.first())
        assertEquals("C4 must not come round twice in one round", 1, round.count { it == Pitch.C4 })
        assertEquals(notes.toSet(), round.toSet())
    }

    @Test
    fun resetStartsAFreshBag() {
        val queue = NoteQueue(bag, Random(5))
        queue.next()
        queue.reset()
        val round = List(bag.size) { queue.next() }
        assertEquals(bag.toSet(), round.toSet())
    }

    @Test
    fun anEmptyQueueIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { NoteQueue(emptyList()) }
    }
}

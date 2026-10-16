package com.a24solver.wakusei.sound

import kotlin.random.Random

/**
 * The note material of the keypad, spelled out in one place so the sounds can be read off the
 * specification and checked by tests.
 */
object Melodies {

    /**
     * The eight notes the keypad draws from, in the order they are listed: C, D, E, F#, G, A, Bb,
     * high C.
     */
    val KEYPAD_NOTES: List<Pitch> = listOf(
        Pitch.C4,
        Pitch.D4,
        Pitch.E4,
        Pitch.F_SHARP4,
        Pitch.G4,
        Pitch.A4,
        Pitch.B_FLAT4,
        Pitch.C5
    )

    /** SOLVE: the six-note blues scale C, Eb, F#, G, Bb, B. */
    val SOLVE_SCALE: List<Pitch> = listOf(
        Pitch.C4,
        Pitch.E_FLAT4,
        Pitch.F_SHARP4,
        Pitch.G4,
        Pitch.B_FLAT4,
        Pitch.B4
    )

    /** A long press on CLEAR or DEL: the E major triad E, G#, high C. */
    val RESET_SCALE: List<Pitch> = listOf(
        Pitch.E4,
        Pitch.G_SHARP4,
        Pitch.C5
    )
}

/**
 * A shuffled bag of notes.
 *
 * [next] hands out every note of [notes] exactly once before any of them can repeat, in a random
 * order that is redrawn each time the bag runs out — so a run of key presses moves through all
 * eight notes instead of repeating the same one, and the sequence is different every round.
 *
 * Thread-safe: key presses arrive on the main thread, but nothing here assumes that.
 */
class NoteQueue(
    private val notes: List<Pitch>,
    private val random: Random = Random.Default
) {
    init {
        require(notes.isNotEmpty()) { "A note queue needs at least one note" }
    }

    private val bag = ArrayDeque<Pitch>()

    @Synchronized
    fun next(): Pitch {
        if (bag.isEmpty()) bag.addAll(notes.shuffled(random))
        return bag.removeFirst()
    }

    /** Draws and discards notes so the next [next] starts a fresh, fully shuffled bag. */
    @Synchronized
    fun reset() {
        bag.clear()
    }
}

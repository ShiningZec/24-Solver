package com.a24solver.wakusei.sound

import kotlin.random.Random

/**
 * The note material of the keypad, spelled out in one place so the sounds can be read off the
 * specification and checked by tests.
 *
 * Every entry is a [Pitch], and the full chromatic range on offer is `Pitch.entries` (A♭3 up to
 * F#5) — so swapping a scale, or making a key play a single different note, means naming different
 * constants here and nothing else.
 */
object Melodies {

    /**
     * The notes the keypad draws from, as they are currently tuned: B, D, Eb, F, G, Ab, high C.
     *
     * Pick whatever you like out of `Pitch.entries`; nothing else has to change. (The commented-out
     * entries are the ones the earlier eight-note set used.)
     */
    val KEYPAD_NOTES: List<Pitch> = listOf(
//        Pitch.C4,
        Pitch.B3,
        Pitch.D4,
        Pitch.E_FLAT4,
        Pitch.F4,
        Pitch.G4,
        Pitch.A_FLAT4,
//        Pitch.B_FLAT4,
        Pitch.C5
    )

    /**
     * The note a shuffled round always opens on, or null to start on a random note.
     *
     * It is not part of [KEYPAD_NOTES]: a round is this note followed by a shuffle of the rest, so
     * it can never come round twice in one round.
     */
    val KEYPAD_LEADING_NOTE: Pitch? = Pitch.C4

    /** SOLVE: the five-note scale C, Eb, F#, G, Bb, with its last note held. */
    val SOLVE_SCALE: List<Pitch> = listOf(
        Pitch.C4,
        Pitch.E_FLAT4,
        Pitch.F_SHARP4,
        Pitch.G4,
        Pitch.B_FLAT4
    )

    /** A long press on CLEAR or DEL: the three-note arpeggio G, D, B, with its last note held. */
    val RESET_SCALE: List<Pitch> = listOf(
        Pitch.G4,
        Pitch.D4,
        Pitch.B3
    )
}

/**
 * A shuffled bag of notes.
 *
 * [next] hands out every note of [notes] exactly once before any of them can repeat, in a random
 * order that is redrawn each time the bag runs out — so a run of key presses moves through the whole
 * set instead of repeating the same note, and the sequence is different every round.
 *
 * With a [leadingNote], each round is that note followed by a shuffle of the remaining ones: the
 * round always opens on the same note without that note being able to appear twice in it. Passing
 * null (the default) shuffles every note, including any that happens to be the app's usual opener.
 *
 * Thread-safe: key presses arrive on the main thread, but nothing here assumes that.
 */
class NoteQueue(
    private val notes: List<Pitch>,
    private val random: Random = Random.Default,
    private val leadingNote: Pitch? = null
) {
    init {
        require(notes.isNotEmpty()) { "A note queue needs at least one note" }
    }

    private val bag = ArrayDeque<Pitch>()

    @Synchronized
    fun next(): Pitch {
        if (bag.isEmpty()) return startNewRound()
        return bag.removeFirst()
    }

    /**
     * Refills the bag with a fresh shuffle and hands back the note that opens the round. The
     * leading note is kept out of the shuffle so it cannot come round a second time.
     */
    private fun startNewRound(): Pitch {
        val shuffled = notes.filter { it != leadingNote }.shuffled(random)
        if (leadingNote == null) {
            bag.addAll(shuffled)
            return bag.removeFirst()
        }
        bag.addAll(shuffled)
        return leadingNote
    }

    /** Draws and discards notes so the next [next] starts a fresh, fully shuffled bag. */
    @Synchronized
    fun reset() {
        bag.clear()
    }
}

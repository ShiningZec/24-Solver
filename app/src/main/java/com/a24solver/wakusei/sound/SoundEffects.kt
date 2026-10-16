package com.a24solver.wakusei.sound

import androidx.annotation.VisibleForTesting

/**
 * The app's single source of keypad sound.
 *
 * Everything the interface plays goes through here, which keeps the decision of *what* each control
 * sounds like in the interface code (see `FirstFragment.playSound`) and the note material in
 * [Melodies].
 *
 * The default engine synthesises PCM and hands it to a [PolyphonicAudioSink]; tests swap in an
 * engine with a recording sink and a direct executor, so a test can click a key and see exactly what
 * was handed to the audio device.
 */
object SoundEffects {

    /**
     * How long a single keypad tone rings for.
     *
     * Long enough that the note is still sounding when the next key is struck: notes overlap and
     * ring out the way they would on a real bar instrument, instead of being cut off by the next
     * keypress.
     */
    const val KEY_NOTE_MS = 750

    /** Note length and spacing of the two arpeggios (SOLVE, and a long press on CLEAR/DEL). */
    const val SCALE_NOTE_MS = 450
    const val SCALE_GAP_MS = 80

    /**
     * The final note of a scale is held much longer: it is the note the phrase is *about*, and
     * letting it ring gives the scale an ending rather than a stop.
     */
    const val SCALE_LAST_NOTE_MS = 1200

    private val keypadNotes = NoteQueue(
        notes = Melodies.KEYPAD_NOTES,
        leadingNote = Melodies.KEYPAD_LEADING_NOTE
    )

    @Volatile
    private var engine: ToneEngine = ToneEngine(PolyphonicAudioSink())

    /** One note from the shuffled bag: the fifteen keypad keys all share this queue. */
    fun playKeypadTone() {
        engine.play(listOf(keypadNotes.next()), KEY_NOTE_MS, gapMs = 0)
    }

    /** SOLVE: the scale, with its last note held. */
    fun playSolveScale() {
        engine.play(Melodies.SOLVE_SCALE, SCALE_NOTE_MS, SCALE_GAP_MS, SCALE_LAST_NOTE_MS)
    }

    /** A long press on CLEAR or DEL: the arpeggio, with its last note held. */
    fun playResetScale() {
        engine.play(Melodies.RESET_SCALE, SCALE_NOTE_MS, SCALE_GAP_MS, SCALE_LAST_NOTE_MS)
    }

    // ------------------------------------------------------------------- tests

    @VisibleForTesting
    internal fun useEngine(replacement: ToneEngine) {
        engine = replacement
    }

    @VisibleForTesting
    internal fun restoreDefaultEngine() {
        engine = ToneEngine(PolyphonicAudioSink())
    }

    @VisibleForTesting
    internal fun resetNoteQueue() {
        keypadNotes.reset()
    }
}

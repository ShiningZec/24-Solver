package com.a24solver.wakusei.sound

import androidx.annotation.VisibleForTesting

/**
 * The app's single source of keypad sound.
 *
 * Everything the interface plays goes through here, which keeps the decision of *what* each control
 * sounds like in the interface code (see `FirstFragment.playSound`) and the note material in
 * [Melodies].
 *
 * The default engine synthesises PCM and writes it to an [AudioTrackSink]; tests swap in an engine
 * with a recording sink and a direct executor, so a test can click a key and see exactly what was
 * handed to the audio device.
 */
object SoundEffects {

    /** How long a single keypad tone rings for. */
    const val KEY_NOTE_MS = 450

    /** Note length and spacing of the two arpeggios (SOLVE, and a long press on CLEAR/DEL). */
    const val SCALE_NOTE_MS = 200
    const val SCALE_GAP_MS = 80

    private val keypadNotes = NoteQueue(Melodies.KEYPAD_NOTES)

    @Volatile
    private var engine: ToneEngine = ToneEngine(AudioTrackSink())

    /** One note from the shuffled bag: the fifteen keypad keys all share this queue. */
    fun playKeypadTone() {
        engine.play(listOf(keypadNotes.next()), KEY_NOTE_MS, gapMs = 0)
    }

    /** SOLVE: the six-note blues scale. */
    fun playSolveScale() {
        engine.play(Melodies.SOLVE_SCALE, SCALE_NOTE_MS, SCALE_GAP_MS)
    }

    /** A long press on CLEAR or DEL: the three-note E major arpeggio. */
    fun playResetScale() {
        engine.play(Melodies.RESET_SCALE, SCALE_NOTE_MS, SCALE_GAP_MS)
    }

    // ------------------------------------------------------------------- tests

    @VisibleForTesting
    internal fun useEngine(replacement: ToneEngine) {
        engine = replacement
    }

    @VisibleForTesting
    internal fun restoreDefaultEngine() {
        engine = ToneEngine(AudioTrackSink())
    }

    @VisibleForTesting
    internal fun resetNoteQueue() {
        keypadNotes.reset()
    }
}

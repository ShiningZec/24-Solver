package com.a24solver.wakusei.sound

import kotlin.math.pow

/**
 * Every semitone from A♭3 to F#5, in ascending order — a complete chromatic scale to pick notes
 * from.
 *
 * [Melodies] names the eight keypad notes and the two arpeggios out of this table. To use a
 * different scale or a single note, name the constants you want there; nothing else has to change.
 *
 * Adding a note here is one line: a Kotlin name, the spelling to show, and its MIDI number. The
 * frequencies are *not* written out — [frequencyHz] derives them in equal temperament from that MIDI
 * number, so a new note cannot end up a few cents out because of a typo in a decimal, and it is
 * obvious at a glance that the table has no gaps (`56, 57, 58, …`).
 *
 * **Do not delete notes that nothing currently refers to.** The table is a complete, gap-free
 * chromatic scale on purpose: it is the palette [Melodies] picks from, and the whole point is that
 * swapping a scale — or making one key play a different single note — is a matter of naming another
 * constant. The note you want is already here and already tuned, whether or not anything uses it
 * today. An unused note costs one line and nothing at runtime; deleting it costs whoever comes next
 * the job of working its frequency out again. `PitchTest` enforces the completeness, so removing one
 * fails the build instead of passing unnoticed.
 *
 * Spellings follow the ones already used across the app: sharps for F#/G#/C#, flats for Bb/Eb. That
 * is why the bottom note is `Ab3` while the note an octave above it is `G_SHARP4` — they are the same
 * pitch class under two names.
 */
enum class Pitch(val noteName: String, val midiNumber: Int) {
    A_FLAT3("Ab3", 56),
    A3("A3", 57),
    B_FLAT3("Bb3", 58),
    B3("B3", 59),
    C4("C4", 60),
    C_SHARP4("C#4", 61),
    D4("D4", 62),
    E_FLAT4("Eb4", 63),
    E4("E4", 64),
    F4("F4", 65),
    F_SHARP4("F#4", 66),
    G4("G4", 67),
    G_SHARP4("G#4", 68),
    A_FLAT4("Ab4", 68),
    A4("A4", 69),
    B_FLAT4("Bb4", 70),
    B4("B4", 71),
    C5("C5", 72),
    C_SHARP5("C#5", 73),
    D5("D5", 74),
    E_FLAT5("Eb5", 75),
    E5("E5", 76),
    F5("F5", 77),
    F_SHARP5("F#5", 78);

    /**
     * Equal temperament with A4 = 440 Hz: 207.65 Hz at A♭3 up to 739.99 Hz at F#5.
     *
     * Computed rather than stored, so it is always consistent with [midiNumber]. It costs a `pow`,
     * so anything that uses it in a loop hoists it first (see `ToneSynthesizer`).
     */
    val frequencyHz: Double
        get() = A4_FREQUENCY_HZ * 2.0.pow((midiNumber - A4_MIDI_NUMBER) / 12.0)

    private companion object {
        const val A4_FREQUENCY_HZ = 440.0
        const val A4_MIDI_NUMBER = 69
    }
}

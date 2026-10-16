package com.a24solver.wakusei.sound

/**
 * A phrase of notes: what to play, how long each note lasts, and how much silence sits between them.
 *
 * The last note gets its own length ([lastNoteMs]) so a scale can end on a long, ringing note; a
 * single keypad tone is simply a phrase of one note, where the two lengths are the same.
 */
data class Phrase(
    val pitches: List<Pitch>,
    val noteMs: Int,
    val gapMs: Int = 0,
    val lastNoteMs: Int = noteMs
) {
    init {
        require(pitches.isNotEmpty()) { "A phrase needs at least one note" }
    }
}

/**
 * Memoises rendered phrases.
 *
 * Synthesis is a few milliseconds of maths per note, which is nothing until a burst of key presses
 * arrives: without this, fifteen quick taps would queue roughly fifty milliseconds of rendering and
 * the last notes would be heard late — exactly what the keypad must not do. The app only ever plays
 * a handful of distinct phrases (eight single notes and two scales), so after the first tap every
 * later one is a hash lookup.
 *
 * Bounded and least-recently-used, so a caller that invents a new phrase per note cannot grow it
 * without limit. Thread-safe: the engine renders on more than one worker.
 */
class PhraseCache(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val sampleRate: Int = ToneSynthesizer.SAMPLE_RATE,
    private val render: (Phrase) -> ShortArray = { phrase ->
        ToneSynthesizer.phrase(
            phrase.pitches,
            phrase.noteMs,
            phrase.gapMs,
            phrase.lastNoteMs,
            sampleRate
        )
    }
) {

    private val lock = Any()
    private val entries = LinkedHashMap<Phrase, ShortArray>()

    /** The samples for [phrase], rendering them only the first time it is asked for. */
    fun samples(phrase: Phrase): ShortArray = synchronized(lock) {
        entries[phrase]?.let { cached ->
            // re-insert to mark it as the most recently used
            entries.remove(phrase)
            entries[phrase] = cached
            return cached
        }
        val rendered = render(phrase)
        entries[phrase] = rendered
        while (entries.size > capacity) {
            entries.remove(entries.keys.first())
        }
        rendered
    }

    /** How many phrases are held; the tests use it to see the cache doing its job. */
    internal val size: Int get() = synchronized(lock) { entries.size }

    private companion object {
        const val DEFAULT_CAPACITY = 32
    }
}

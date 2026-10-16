package com.a24solver.wakusei.sound

/**
 * Where rendered PCM ends up.
 *
 * The engine only ever talks to this interface, so tests can watch playback — and count notes —
 * without an audio device, and without the engine knowing anything about it.
 *
 * [play] returns as soon as the note has *started*; it is not expected to wait for it to finish.
 */
interface AudioSink {

    /** Starts [samples] (mono, 16-bit) at [sampleRate]. Does not block until it has finished. */
    fun play(samples: ShortArray, sampleRate: Int)

    /** Frees whatever the sink holds. Called once, when the engine is shut down. */
    fun release() {}
}

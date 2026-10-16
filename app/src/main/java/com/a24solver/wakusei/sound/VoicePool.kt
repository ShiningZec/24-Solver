package com.a24solver.wakusei.sound

/**
 * Decides which of a fixed set of voices a new note should use, so that notes overlap instead of
 * cutting each other off.
 *
 * A voice is "sounding" until the note given to it would have finished. [acquire] hands back a free
 * voice whenever there is one; when every voice is busy it takes the one that will fall silent
 * soonest — the oldest tail — because dropping the oldest note is far less noticeable than dropping
 * the one the user just played.
 *
 * No Android types and no threads of its own: the policy is plain arithmetic, which is what makes it
 * testable on a bare JVM.
 */
internal class VoicePool(val voiceCount: Int) {

    init {
        require(voiceCount > 0) { "A voice pool needs at least one voice" }
    }

    private val busyUntilMs = LongArray(voiceCount) { NEVER_USED }

    /** Reserves a voice for a note starting at [nowMs] and lasting [durationMs]. */
    fun acquire(nowMs: Long, durationMs: Long): Int {
        require(durationMs > 0) { "A note must last longer than nothing" }

        for (voice in 0 until voiceCount) {
            if (busyUntilMs[voice] <= nowMs) {
                busyUntilMs[voice] = nowMs + durationMs
                return voice
            }
        }

        var oldest = 0
        for (voice in 1 until voiceCount) {
            if (busyUntilMs[voice] < busyUntilMs[oldest]) oldest = voice
        }
        busyUntilMs[oldest] = nowMs + durationMs
        return oldest
    }

    /** How many voices are still ringing at [nowMs]. */
    fun sounding(nowMs: Long): Int = busyUntilMs.count { it > nowMs }

    fun free(nowMs: Long): Int = voiceCount - sounding(nowMs)

    /** Forgets every reservation, e.g. when the audio device is released. */
    fun reset() {
        busyUntilMs.fill(NEVER_USED)
    }

    private companion object {
        const val NEVER_USED = Long.MIN_VALUE
    }
}

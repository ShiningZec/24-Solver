package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice allocation: which of the fixed set of voices a new note takes.
 *
 * This is the part that makes notes ring *into* each other instead of cutting each other off, so
 * what matters is that a free voice is preferred, that nothing is stolen while a voice is free, and
 * that the note which gets dropped when they are all busy is the oldest tail.
 */
class VoicePoolTest {

    @Test
    fun aFreshNoteTakesAFreeVoice() {
        val pool = VoicePool(voiceCount = 3)

        assertEquals(0, pool.acquire(nowMs = 0, durationMs = 500))
        assertEquals(1, pool.acquire(nowMs = 0, durationMs = 500))
        assertEquals(2, pool.acquire(nowMs = 0, durationMs = 500))
    }

    @Test
    fun nothingIsStolenWhileAVoiceIsStillFree() {
        val pool = VoicePool(voiceCount = 3)
        pool.acquire(0, durationMs = 1_000)

        val second = pool.acquire(nowMs = 10, durationMs = 100)
        val third = pool.acquire(nowMs = 20, durationMs = 100)

        assertNotEquals("a sounding voice was reused although others were free", 0, second)
        assertNotEquals(0, third)
        assertEquals("the first note should still be ringing", 3, pool.sounding(nowMs = 30))
    }

    @Test
    fun aVoiceIsReusedOnceItsNoteWouldHaveFinished() {
        val pool = VoicePool(voiceCount = 2)
        pool.acquire(0, durationMs = 100)
        pool.acquire(0, durationMs = 100)

        assertEquals("past the end of both notes, either voice may be reused", 0, pool.acquire(150, 100))
        assertEquals(1, pool.sounding(nowMs = 160))
    }

    @Test
    fun theOldestTailIsTheOneThatGoesWhenEveryVoiceIsBusy() {
        val pool = VoicePool(voiceCount = 3)
        pool.acquire(nowMs = 0, durationMs = 1_000)  // voice 0, ends at 1000
        pool.acquire(nowMs = 0, durationMs = 300)   // voice 1, ends at 300
        pool.acquire(nowMs = 0, durationMs = 600)   // voice 2, ends at 600

        val stolen = pool.acquire(nowMs = 100, durationMs = 500)

        assertEquals("the voice closest to falling silent should be reused", 1, stolen)
        assertEquals("the other two keep ringing", 3, pool.sounding(nowMs = 100))
    }

    @Test
    fun aLongRingingNoteIsNotStolenWhileAShorterOneIsAvailable() {
        val pool = VoicePool(voiceCount = 2)
        pool.acquire(nowMs = 0, durationMs = 10_000) // voice 0: a long note, still ringing
        pool.acquire(nowMs = 0, durationMs = 100)   // voice 1: nearly finished

        val next = pool.acquire(nowMs = 0, durationMs = 10)

        assertEquals(
            "the long note must survive; the nearly-finished one is the right voice to reuse",
            1,
            next
        )
        assertEquals("both voices are still counted as sounding", 2, pool.sounding(nowMs = 0))
        assertEquals("the long note still rings after the short one is gone", 1, pool.sounding(nowMs = 200))
    }

    @Test
    fun theCountsFollowTheClock() {
        val pool = VoicePool(voiceCount = 4)
        pool.acquire(0, durationMs = 100)
        pool.acquire(0, durationMs = 100)

        assertEquals(2, pool.sounding(0))
        assertEquals(2, pool.free(0))
        assertEquals(0, pool.sounding(100))
        assertEquals(4, pool.free(101))
    }

    @Test
    fun resettingFreesEverything() {
        val pool = VoicePool(voiceCount = 2)
        pool.acquire(0, durationMs = 10_000)

        pool.reset()

        assertEquals(0, pool.sounding(nowMs = 1))
    }

    @Test
    fun aPoolNeedsVoices() {
        assertThrows(IllegalArgumentException::class.java) { VoicePool(0) }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePool(1).acquire(nowMs = 0, durationMs = 0)
        }
    }

    @Test
    fun theDefaultPoolIsTheSixVoicesTheAppShips() {
        assertEquals(6, PolyphonicAudioSink.DEFAULT_VOICE_COUNT)
        assertTrue(VoicePool(6).free(nowMs = 0) == 6)
    }
}

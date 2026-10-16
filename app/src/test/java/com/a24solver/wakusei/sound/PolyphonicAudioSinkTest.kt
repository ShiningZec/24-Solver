package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A [ToneTrack] that remembers what it was asked to do. */
private class FakeTrack(override val bufferSizeInBytes: Int) : ToneTrack {

    var written: ShortArray? = null
        private set
    var playCount = 0
        private set
    var stopCount = 0
        private set
    var released = false
        private set

    override fun write(samples: ShortArray) {
        written = samples.copyOf()
    }

    override fun play() {
        playCount++
    }

    override fun stop() {
        stopCount++
    }

    override fun release() {
        released = true
    }
}

private class FakeTrackFactory : (Int, Int) -> ToneTrack {
    val created = mutableListOf<FakeTrack>()
    val sampleRates = mutableListOf<Int>()

    override fun invoke(bufferSizeBytes: Int, sampleRate: Int): ToneTrack {
        sampleRates += sampleRate
        return FakeTrack(bufferSizeBytes).also { created += it }
    }

    val latest: FakeTrack get() = created.last()
}

/**
 * The voice handling of the audio sink, which is where the "a keypad tone replayed the previous
 * scale" bug lived: a static track plays its whole buffer, so reusing a long track for a short note
 * lets the leftovers play after it.
 */
class PolyphonicAudioSinkTest {

    private val sampleRate = 44_100
    private val factory = FakeTrackFactory()
    private var now = 0L

    private fun sink(voiceCount: Int = 3) =
        PolyphonicAudioSink(voiceCount, factory) { now }

    private fun note(ms: Int) = ShortArray(ToneSynthesizer.samplesFor(ms, sampleRate)) { 1 }

    // ------------------------------------------------------- the leftover bug

    @Test
    fun aShorterNoteNeverPlaysInTheTrackOfALongerOne() {
        val sink = sink(voiceCount = 1)
        val scale = note(3_000)
        val tone = note(750)

        sink.play(scale, sampleRate)
        val scaleTrack = factory.latest
        now += 3_500 // the scale has finished, so its voice is free again
        sink.play(tone, sampleRate)

        assertNotSame(
            "the short note was written into the buffer that still held the scale",
            scaleTrack,
            factory.latest
        )
        assertEquals(
            "the new track must be sized for the note, not for the phrase before it",
            tone.size * Short.SIZE_BYTES,
            factory.latest.bufferSizeInBytes
        )
        assertEquals(tone.size * Short.SIZE_BYTES, factory.latest.written!!.size * Short.SIZE_BYTES)
        assertTrue("the long track should have been released", scaleTrack.released)
    }

    @Test
    fun aLongerNoteIsNotWrittenIntoAShorterTrackEither() {
        val sink = sink(voiceCount = 1)
        val tone = note(750)
        val scale = note(3_000)

        sink.play(tone, sampleRate)
        now += 1_000
        sink.play(scale, sampleRate)

        assertEquals(scale.size * Short.SIZE_BYTES, factory.latest.bufferSizeInBytes)
    }

    @Test
    fun notesOfTheSameLengthReuseTheirVoiceTrack() {
        val sink = sink(voiceCount = 1)

        sink.play(note(750), sampleRate)
        val first = factory.latest
        now += 1_000
        sink.play(note(750), sampleRate)

        assertSame("an equal-length note should not pay for a new track", first, factory.latest)
        assertEquals("one track for two notes of the same length", 1, factory.created.size)
    }

    // ----------------------------------------------------------- the voices

    @Test
    fun aNoteIsWrittenInFullAndStarted() {
        val sink = sink(voiceCount = 1)
        val tone = note(750)

        sink.play(tone, sampleRate)

        val track = factory.latest
        assertEquals(tone.toList(), track.written!!.toList())
        assertEquals(1, track.playCount)
        assertEquals(sampleRate, factory.sampleRates.single())
    }

    @Test
    fun aSecondNoteDoesNotStopTheFirstOne() {
        val sink = sink(voiceCount = 2)

        sink.play(note(750), sampleRate)
        val first = factory.created.single()
        val stopsAfterFirstNote = first.stopCount

        sink.play(note(750), sampleRate)

        assertEquals("the second note should have taken a second voice", 2, factory.created.size)
        assertEquals(
            "the first note must not be stopped (its own reset aside)",
            stopsAfterFirstNote,
            first.stopCount
        )
        assertEquals("both notes are ringing", 2, sink.soundingVoices())
    }

    @Test
    fun everyVoiceGetsItsOwnTrack() {
        val sink = sink(voiceCount = 3)

        sink.play(note(750), sampleRate)
        sink.play(note(750), sampleRate)
        sink.play(note(750), sampleRate)

        assertEquals(3, factory.created.size)
        assertTrue("each voice should have been started", factory.created.all { it.playCount == 1 })
    }

    @Test
    fun theOldestVoiceIsReusedWhenTheyAreAllBusy() {
        val sink = sink(voiceCount = 2)
        sink.play(note(100), sampleRate) // voice 0, the one that finishes first
        val oldest = factory.created[0]
        sink.play(note(500), sampleRate) // voice 1, still ringing
        val other = factory.created[1]
        val stopsOnOldest = oldest.stopCount

        now += 150 // both are still sounding
        sink.play(note(100), sampleRate)

        assertEquals("the stolen voice's track already fits, so nothing new is built", 2, factory.created.size)
        assertEquals("the nearly-finished voice should be the one reused", 2, oldest.playCount)
        assertTrue(
            "and it should have been stopped to make way for the new note",
            oldest.stopCount > stopsOnOldest
        )
        assertEquals("the other note keeps ringing", 1, other.playCount)
    }

    @Test
    fun releaseStopsAndFreesEveryTrack() {
        val sink = sink(voiceCount = 2)
        sink.play(note(750), sampleRate)
        sink.play(note(750), sampleRate)

        sink.release()

        assertTrue("every track should be released", factory.created.all { it.released })
        assertEquals("and none should still be sounding", 0, sink.soundingVoices())
    }

    @Test
    fun playingAfterReleaseStartsCleanly() {
        val sink = sink(voiceCount = 1)
        sink.play(note(750), sampleRate)
        sink.release()

        sink.play(note(750), sampleRate)

        assertEquals("a fresh track is needed after the old one was freed", 2, factory.created.size)
        assertEquals(1, factory.latest.playCount)
    }
}

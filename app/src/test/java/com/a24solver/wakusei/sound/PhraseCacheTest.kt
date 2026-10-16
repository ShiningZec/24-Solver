package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phrase cache.
 *
 * It exists so a key press never waits for a few milliseconds of synthesis: the app plays the same
 * handful of phrases over and over, so rendering each one once is the difference between "instant"
 * and "the last notes of a quick run arrive late".
 */
class PhraseCacheTest {

    private val phrase = Phrase(listOf(Pitch.C4), noteMs = 40)
    private val other = Phrase(listOf(Pitch.G4), noteMs = 40)

    /** Counts how often something actually had to be rendered. */
    private class CountingRenderer : (Phrase) -> ShortArray {
        var calls = 0
        override fun invoke(phrase: Phrase): ShortArray {
            calls++
            return ShortArray(phrase.noteMs)
        }
    }

    @Test
    fun theSamePhraseIsRenderedOnce() {
        val renderer = CountingRenderer()
        val cache = PhraseCache(render = renderer)

        val first = cache.samples(phrase)
        val second = cache.samples(phrase)

        assertSame("the second request should not re-render", first, second)
        assertEquals(1, renderer.calls)
    }

    @Test
    fun differentPhrasesAreRenderedSeparately() {
        val renderer = CountingRenderer()
        val cache = PhraseCache(render = renderer)

        assertNotSame(cache.samples(phrase), cache.samples(other))
        assertEquals(2, renderer.calls)
        assertNotSame(cache.samples(Phrase(listOf(Pitch.C4), noteMs = 41)), cache.samples(phrase))
        assertEquals(3, renderer.calls)
    }

    @Test
    fun aPhraseIsOnlyTheSameIfEveryPartMatches() {
        val base = Phrase(listOf(Pitch.C4, Pitch.E4), noteMs = 100, gapMs = 20, lastNoteMs = 300)

        assertEquals(base, Phrase(listOf(Pitch.C4, Pitch.E4), 100, 20, 300))
        assertTrue(base != base.copy(noteMs = 101))
        assertTrue(base != base.copy(gapMs = 21))
        assertTrue(base != base.copy(lastNoteMs = 301))
        assertTrue(base != base.copy(pitches = listOf(Pitch.C4, Pitch.F4)))
    }

    @Test
    fun theCacheStaysBounded() {
        val renderer = CountingRenderer()
        val cache = PhraseCache(capacity = 3, render = renderer)

        repeat(10) { index ->
            cache.samples(Phrase(listOf(Pitch.C4), noteMs = index + 1))
        }

        assertEquals("the cache grew past its capacity", 3, cache.size)
        // the oldest entries were dropped, so asking for one of them renders again
        val before = renderer.calls
        cache.samples(Phrase(listOf(Pitch.C4), noteMs = 1))
        assertEquals(before + 1, renderer.calls)
        // while a recent one is still there
        val recent = Phrase(listOf(Pitch.C4), noteMs = 10)
        assertEquals(before + 1, renderer.calls)
        cache.samples(recent)
        assertEquals(before + 1, renderer.calls)
    }

    @Test
    fun theRealRendererProducesAUsablePhrase() {
        val cache = PhraseCache()

        val samples = cache.samples(Phrase(Melodies.SOLVE_SCALE, noteMs = 40, gapMs = 5, lastNoteMs = 80))

        val expected = ToneSynthesizer.samplesFor(45, ToneSynthesizer.SAMPLE_RATE) *
            (Melodies.SOLVE_SCALE.size - 1) +
            ToneSynthesizer.samplesFor(80, ToneSynthesizer.SAMPLE_RATE)
        assertEquals(expected, samples.size)
        assertTrue(samples.any { it != 0.toShort() })
    }

    @Test
    fun aPhraseNeedsAtLeastOneNote() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            Phrase(emptyList(), noteMs = 100)
        }
    }
}

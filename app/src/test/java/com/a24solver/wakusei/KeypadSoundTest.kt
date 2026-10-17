package com.a24solver.wakusei

import android.os.Looper
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.a24solver.wakusei.settings.Settings
import com.a24solver.wakusei.sound.Melodies
import com.a24solver.wakusei.sound.Pitch
import com.a24solver.wakusei.sound.RecordingSink
import com.a24solver.wakusei.sound.SoundEffects
import com.a24solver.wakusei.sound.ToneEngine
import com.a24solver.wakusei.sound.ToneSynthesizer
import com.a24solver.wakusei.sound.directExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * What the user interface actually plays.
 *
 * The engine is replaced with one that writes to a [RecordingSink] on the calling thread, so every
 * click is observable the moment it happens — and because the recorded buffers are compared against
 * freshly synthesised notes, the test can tell *which* pitch was played, not just that something
 * was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeypadSoundTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var navController: NavController
    private lateinit var sink: RecordingSink
    private lateinit var root: View

    /** The fifteen keys of the input screen that make a sound. */
    private val toneKeyIds = listOf(
        R.id.button0, R.id.button1, R.id.button2, R.id.button3, R.id.button4,
        R.id.button5, R.id.button6, R.id.button7, R.id.button8, R.id.button9,
        R.id.button_sign, R.id.button_next, R.id.button_clear, R.id.button_del, R.id.button_target
    )

    /** The notes one round of the shuffled bag is made of: the leading note, then the rest. */
    private val roundNotes: List<Pitch> =
        listOfNotNull(Melodies.KEYPAD_LEADING_NOTE) + Melodies.KEYPAD_NOTES

    @Before
    fun setUp() {
        sink = RecordingSink()
        SoundEffects.useEngine(ToneEngine(sink, ToneSynthesizer.SAMPLE_RATE, directExecutor))
        SoundEffects.resetNoteQueue()
        settings().soundEnabled = true
        markHelpSeen(RuntimeEnvironment.getApplication())

        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val fm = controller.get().supportFragmentManager
        fm.executePendingTransactions()
        val navHost = fm.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHost.navController
        navHost.childFragmentManager.executePendingTransactions()
        root = (navHost.childFragmentManager.fragments.first() as FirstFragment).requireView()
    }

    @After
    fun tearDown() {
        SoundEffects.restoreDefaultEngine()
    }

    // ----------------------------------------------------------------- helpers

    private fun settings() = Settings(RuntimeEnvironment.getApplication())

    private fun click(id: Int) {
        root.findViewById<View>(id).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun longClick(id: Int) {
        root.findViewById<View>(id).performLongClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun noteSamples(pitch: Pitch, durationMs: Int) =
        ToneSynthesizer.note(pitch, durationMs, ToneSynthesizer.SAMPLE_RATE)

    /** The pitch a recorded keypad tone was made of, or null when nothing matches. */
    private fun pitchOf(buffer: ShortArray): Pitch? = roundNotes.firstOrNull {
        buffer.contentEquals(noteSamples(it, SoundEffects.KEY_NOTE_MS))
    }

    /**
     * The expected sample count of one scale: notes one onset apart, the last one longer.
     *
     * The notes overlap (the onset is far shorter than a note), so the phrase length is the onset
     * times the gaps between notes plus the final note — not a row of note-plus-silence steps.
     */
    private fun scaleLength(pitches: List<Pitch>): Int =
        ToneSynthesizer.samplesFor(SoundEffects.SCALE_ONSET_MS, ToneSynthesizer.SAMPLE_RATE) *
            (pitches.size - 1) +
            ToneSynthesizer.samplesFor(SoundEffects.SCALE_LAST_NOTE_MS, ToneSynthesizer.SAMPLE_RATE)

    /**
     * Whether the phrase has a note attack at [index].
     *
     * Later notes are mixed with the ones still ringing, so they cannot be compared sample by
     * sample; what can be checked is that the sound *starts* there — the level right after the onset
     * is above the level right before it.
     */
    private fun hasAttackAt(phrase: ShortArray, index: Int): Boolean {
        val onset = ToneSynthesizer.samplesFor(SoundEffects.SCALE_ONSET_MS, ToneSynthesizer.SAMPLE_RATE)
        val at = index * onset
        if (at + 400 >= phrase.size) return true // the long final note: its onset is the tail
        // before the first note there is silence, not an empty window to divide by
        val before = if (at >= 200) rms(phrase, at - 200, at) else 0.0
        val after = rms(phrase, at + 20, at + 220)
        return after > before
    }

    /**
     * Whether the phrase opens on [pitch]: only the first onset window can be compared exactly,
     * because the second note starts right after it and the two are mixed from then on.
     */
    private fun startsWith(phrase: ShortArray, pitch: Pitch): Boolean {
        val window = ToneSynthesizer.samplesFor(SoundEffects.SCALE_ONSET_MS, ToneSynthesizer.SAMPLE_RATE)
        val expected = noteSamples(pitch, SoundEffects.SCALE_NOTE_MS)
        return phrase.copyOfRange(0, window).contentEquals(expected.copyOfRange(0, window))
    }

    private fun rms(samples: ShortArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (index in from until to) sum += samples[index].toDouble() * samples[index]
        return kotlin.math.sqrt(sum / (to - from))
    }

    private fun awaitResultScreen(timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (navController.currentDestination?.id == R.id.SecondFragment) return
            Thread.sleep(20)
        }
        assertEquals(R.id.SecondFragment, navController.currentDestination?.id)
    }

    private fun awaitView(id: Int, timeoutMs: Long = 5_000): View {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            controller.get().supportFragmentManager.executePendingTransactions()
            controller.get().findViewById<View>(id)?.let { return it }
            Thread.sleep(20)
        }
        return requireNotNull(controller.get().findViewById(id)) { "view $id never appeared" }
    }

    // ------------------------------------------------------------------- tests

    @Test
    fun everyKeypadToneKeyPlaysOneNote() {
        for (id in toneKeyIds) {
            val before = sink.played.size
            click(id)
            assertEquals("key $id should play exactly one note", before + 1, sink.played.size)
            assertEquals(
                "key $id played the wrong length",
                ToneSynthesizer.samplesFor(SoundEffects.KEY_NOTE_MS, ToneSynthesizer.SAMPLE_RATE),
                sink.sampleCounts.last()
            )
        }
    }

    @Test
    fun aRoundUsesEveryNoteBeforeRepeatingOne() {
        // one full round of the shuffled bag, straight through the keypad
        toneKeyIds.take(roundNotes.size).forEach { click(it) }

        val playedPitches = sink.played.map { requireNotNull(pitchOf(it)) { "unrecognised note" } }
        assertEquals(
            "one round must use each note of the set exactly once",
            roundNotes.toSet(),
            playedPitches.toSet()
        )
        assertEquals(roundNotes.size, playedPitches.size)
    }

    @Test
    fun aRoundOpensOnTheLeadingNote() {
        click(toneKeyIds.first())

        assertEquals(Pitch.C4, pitchOf(sink.played.single()))
    }

    @Test
    fun theKeypadNotesSoundAtTheirOwnPitch() {
        // press the whole round, and check each note is one of the set exactly as synthesised
        toneKeyIds.take(roundNotes.size).forEach { click(it) }
        assertTrue(sink.played.all { pitchOf(it) != null })
    }

    @Test
    fun solvePlaysItsScaleNotesOneOnsetApartWithALongFinalNote() {
        click(R.id.button_solve)

        val phrase = sink.played.single()
        assertEquals(scaleLength(Melodies.SOLVE_SCALE), phrase.size)
        assertTrue(
            "SOLVE should open on ${Melodies.SOLVE_SCALE.first().noteName}",
            startsWith(phrase, Melodies.SOLVE_SCALE.first())
        )
        Melodies.SOLVE_SCALE.indices.forEach { index ->
            assertTrue("SOLVE should have a note attack at position $index", hasAttackAt(phrase, index))
        }
    }

    @Test
    fun aLongPressOnClearAndDelPlaysItsArpeggio() {
        for (id in listOf(R.id.button_clear, R.id.button_del)) {
            sink.played.clear()
            longClick(id)

            val phrase = sink.played.single()
            assertEquals("long press on $id", scaleLength(Melodies.RESET_SCALE), phrase.size)
            assertTrue(
                "the arpeggio should start on ${Melodies.RESET_SCALE.first().noteName}",
                startsWith(phrase, Melodies.RESET_SCALE.first())
            )
            Melodies.RESET_SCALE.indices.forEach { index ->
                assertTrue(
                    "the arpeggio should have a note attack at position $index",
                    hasAttackAt(phrase, index)
                )
            }
        }
    }

    @Test
    fun aLongPressOnClearPlaysTheArpeggioInsteadOfATone() {
        click(R.id.button1)
        val afterTone = sink.played.size

        longClick(R.id.button_clear)

        assertEquals("a long press is one phrase, not a tone plus a phrase", afterTone + 1, sink.played.size)
        assertEquals(scaleLength(Melodies.RESET_SCALE), sink.sampleCounts.last())
    }

    @Test
    fun aNoteIsNeverCutShortByTheNextOne() {
        // the requirement this exists for: press three keys and all three notes are handed over in
        // full — nothing is truncated to make room for the next one
        click(R.id.button1)
        click(R.id.button2)
        click(R.id.button3)

        assertEquals(3, sink.played.size)
        val expectedLength = ToneSynthesizer.samplesFor(
            SoundEffects.KEY_NOTE_MS,
            ToneSynthesizer.SAMPLE_RATE
        )
        assertTrue(
            "every note should be complete, were ${sink.sampleCounts}",
            sink.sampleCounts.all { it == expectedLength }
        )
    }

    @Test
    fun theSecondScreenStaysSilent() {
        click(R.id.button5) // 5 alone reaches nothing, so this lands on the empty result screen
        click(R.id.button_solve)
        awaitResultScreen()

        val before = sink.played.size
        controller.get().findViewById<View>(R.id.button_back).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("BACK must not make a sound", before, sink.played.size)
    }

    @Test
    fun theSolutionSetSwitchStaysSilent() {
        for (id in listOf(R.id.button1, R.id.button2, R.id.button3, R.id.button4)) {
            click(id)
            click(R.id.button_next)
        }
        click(R.id.button_solve)
        awaitResultScreen()

        val before = sink.played.size
        awaitView(R.id.button_show_essential).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the solution-set switch must not make a sound", before, sink.played.size)
    }

    @Test
    fun turningTheSoundOffSilencesTheKeypad() {
        settings().soundEnabled = false

        toneKeyIds.forEach { click(it) }
        click(R.id.button_solve)
        longClick(R.id.button_clear)
        longClick(R.id.button_del)

        assertEquals("nothing may play while the setting is off", 0, sink.played.size)
    }

    @Test
    fun theSoundCanBeTurnedBackOn() {
        settings().soundEnabled = false
        click(R.id.button1)
        assertEquals(0, sink.played.size)

        settings().soundEnabled = true
        click(R.id.button1)

        assertEquals(1, sink.played.size)
    }
}

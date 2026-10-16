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

    @Before
    fun setUp() {
        sink = RecordingSink()
        SoundEffects.useEngine(ToneEngine(sink, ToneSynthesizer.SAMPLE_RATE, directExecutor))
        SoundEffects.resetNoteQueue()
        settings().soundEnabled = true

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
    private fun pitchOf(buffer: ShortArray): Pitch? = Melodies.KEYPAD_NOTES.firstOrNull {
        buffer.contentEquals(noteSamples(it, SoundEffects.KEY_NOTE_MS))
    }

    /** The solve runs on a background dispatcher, so the result screen arrives a little later. */
    private fun awaitResultScreen(timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (navController.currentDestination?.id == R.id.SecondFragment) return
            Thread.sleep(20)
        }
        assertEquals(R.id.SecondFragment, navController.currentDestination?.id)
    }

    /** Waits for a view of the (asynchronously reached) result screen to exist. */
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
    fun theFifteenKeysDrawEveryNoteBeforeRepeatingOne() {
        // one full round of the shuffled bag, straight through the keypad
        toneKeyIds.forEach { click(it) }

        val playedPitches = sink.played.map { requireNotNull(pitchOf(it)) { "unrecognised note" } }
        assertEquals(toneKeyIds.size, playedPitches.size)
        assertEquals(
            "the first eight tones must use each note exactly once",
            Melodies.KEYPAD_NOTES.toSet(),
            playedPitches.take(Melodies.KEYPAD_NOTES.size).toSet()
        )
        // eight notes in the bag, fifteen presses: seven notes come round a second time
        assertEquals(Melodies.KEYPAD_NOTES.size + 7, playedPitches.size)
    }

    @Test
    fun solvePlaysTheSixNoteScale() {
        click(R.id.button_solve)

        val phrase = sink.played.single()
        val noteLength = ToneSynthesizer.samplesFor(
            SoundEffects.SCALE_NOTE_MS + SoundEffects.SCALE_GAP_MS,
            ToneSynthesizer.SAMPLE_RATE
        )
        assertEquals(noteLength * Melodies.SOLVE_SCALE.size, phrase.size)
        Melodies.SOLVE_SCALE.forEachIndexed { index, pitch ->
            val slice = phrase.copyOfRange(
                index * noteLength,
                index * noteLength + ToneSynthesizer.samplesFor(SoundEffects.SCALE_NOTE_MS, ToneSynthesizer.SAMPLE_RATE)
            )
            assertTrue(
                "SOLVE note $index should be ${pitch.noteName}",
                slice.contentEquals(noteSamples(pitch, SoundEffects.SCALE_NOTE_MS))
            )
        }
    }

    @Test
    fun aLongPressOnClearAndDelPlaysTheResetArpeggio() {
        for (id in listOf(R.id.button_clear, R.id.button_del)) {
            sink.played.clear()
            longClick(id)

            val phrase = sink.played.single()
            val noteLength = ToneSynthesizer.samplesFor(
                SoundEffects.SCALE_NOTE_MS + SoundEffects.SCALE_GAP_MS,
                ToneSynthesizer.SAMPLE_RATE
            )
            assertEquals("long press on $id", noteLength * Melodies.RESET_SCALE.size, phrase.size)
            assertTrue(
                "the arpeggio should start on ${Melodies.RESET_SCALE.first().noteName}",
                phrase.copyOfRange(
                    0,
                    ToneSynthesizer.samplesFor(SoundEffects.SCALE_NOTE_MS, ToneSynthesizer.SAMPLE_RATE)
                ).contentEquals(noteSamples(Melodies.RESET_SCALE.first(), SoundEffects.SCALE_NOTE_MS))
            )
        }
    }

    @Test
    fun aLongPressOnClearPlaysTheArpeggioInsteadOfATone() {
        click(R.id.button1)
        val afterTone = sink.played.size

        longClick(R.id.button_clear)

        assertEquals("a long press is one phrase, not a tone plus a phrase", afterTone + 1, sink.played.size)
        assertEquals(
            ToneSynthesizer.samplesFor(
                (SoundEffects.SCALE_NOTE_MS + SoundEffects.SCALE_GAP_MS) * Melodies.RESET_SCALE.size,
                ToneSynthesizer.SAMPLE_RATE
            ),
            sink.sampleCounts.last()
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

package com.a24solver.wakusei

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Robolectric tests for the input screen: key wiring, input editing, the validation performed
 * before the solver is called, and the background SOLVE call (whose result is still only the
 * "SOLVE COMPLETE" placeholder).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstFragmentTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var root: View

    private val digitIds = mapOf(
        0 to R.id.button0,
        1 to R.id.button1,
        2 to R.id.button2,
        3 to R.id.button3,
        4 to R.id.button4,
        5 to R.id.button5,
        6 to R.id.button6,
        7 to R.id.button7,
        8 to R.id.button8,
        9 to R.id.button9
    )

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val fm = controller.get().supportFragmentManager
        fm.executePendingTransactions()

        // the nav graph's start destination, or (defensively) a fragment hosted by hand
        val navHost = fm.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val first = navHost?.childFragmentManager?.let { child ->
            child.executePendingTransactions()
            child.fragments.firstOrNull() as? FirstFragment
        } ?: FirstFragment().also {
            fm.beginTransaction().replace(R.id.nav_host_fragment_content_main, it).commitNow()
        }

        root = first.requireView()
    }

    // ------------------------------------------------------------- helpers

    private fun text(id: Int): String = root.findViewById<TextView>(id).text.toString()

    private fun click(id: Int) {
        root.findViewById<Button>(id).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun type(vararg digits: Int) = digits.forEach {
        click(digitIds.getValue(it))
    }

    /** Types [value] after clearing whatever is in the buffer. */
    private fun retype(value: Int) {
        click(R.id.button_clear)
        type(*value.toString().map { it - '0' }.toIntArray())
    }

    private fun latestToast(): String = ShadowToast.getTextOfLatestToast() ?: ""

    /** Idles the main looper until the label shows [expected] (the solve call is async). */
    private fun awaitText(id: Int, expected: String, timeoutMs: Long = 20_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (text(id) == expected) return true
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        return text(id) == expected
    }

    // --------------------------------------------------------------- tests

    @Test
    fun everyDigitKeyTypesItsOwnDigit() {
        for (digit in 0..9) {
            val id = digitIds.getValue(digit)
            // the key shows the digit it must type ...
            assertEquals(digit.toString(), root.findViewById<Button>(id).text.toString())
            // ... and typing it lands in the input buffer (cleared first)
            click(R.id.button_del)
            click(id)
            assertEquals("INPUT: $digit", text(R.id.text_current_input))
        }
    }

    @Test
    fun backspaceAndClearEditTheBuffer() {
        type(1, 2, 3)
        assertEquals("INPUT: 123", text(R.id.text_current_input))

        click(R.id.button_del)
        assertEquals("INPUT: 12", text(R.id.text_current_input))

        click(R.id.button_clear)
        assertEquals("INPUT: _", text(R.id.text_current_input))
    }

    @Test
    fun clearWithEmptyBufferRemovesTheLastCommittedNumber() {
        type(7); click(R.id.button_next)
        type(8); click(R.id.button_next)
        assertEquals("NUMBERS: 7, 8", text(R.id.text_numbers))

        click(R.id.button_clear) // empty buffer -> undo the last number
        assertEquals("NUMBERS: 7", text(R.id.text_numbers))
        assertTrue(latestToast().contains("Removed last number: 8"))
    }

    @Test
    fun numbersAreCommittedByNextAndCappedAtFive() {
        for (n in listOf(1, 2, 3, 4, 5)) {
            type(n)
            click(R.id.button_next)
        }
        assertEquals("NUMBERS: 1, 2, 3, 4, 5", text(R.id.text_numbers))

        type(9)
        click(R.id.button_next) // rejected: already 5 candidates
        assertEquals("NUMBERS: 1, 2, 3, 4, 5", text(R.id.text_numbers))
        assertEquals("INPUT: 9", text(R.id.text_current_input)) // kept, so it can be edited
        assertTrue(latestToast().contains("At most 5"))
    }

    @Test
    fun candidateAbove256IsRejectedWhileTyping() {
        retype(300)
        click(R.id.button_next)
        assertEquals("NUMBERS: (none)", text(R.id.text_numbers))
        assertTrue(latestToast().contains("±256"))
    }

    @Test
    fun targetModeEditsTheTargetAndEnforcesItsRange() {
        click(R.id.button_target)
        assertEquals("TARGET INPUT: 24", text(R.id.text_current_input)) // pre-filled for editing

        click(R.id.button_clear)
        type(1, 6, 3, 8, 4)
        click(R.id.button_next)
        assertEquals("TARGET: 16384", text(R.id.text_target))
        assertEquals("INPUT: _", text(R.id.text_current_input)) // back to number mode

        click(R.id.button_target)
        click(R.id.button_clear)
        type(1, 6, 3, 8, 5) // 16385 -> out of range
        click(R.id.button_next)
        assertEquals("TARGET: 16384", text(R.id.text_target)) // unchanged
        assertEquals("TARGET INPUT: 16385", text(R.id.text_current_input))
        assertTrue(latestToast().contains("±16384"))

        click(R.id.button_target) // pressing TARGET again cancels the edit
        assertEquals("INPUT: _", text(R.id.text_current_input))
    }

    @Test
    fun solveWithoutNumbersIsRejectedBeforeCallingTheSolver() {
        click(R.id.button_solve)
        assertTrue(latestToast().contains("Add at least one number"))
        assertEquals("TARGET: 24", text(R.id.text_target))
        assertTrue(root.findViewById<Button>(R.id.button_solve).isEnabled)
    }

    @Test
    fun solveCommitsPendingInputAndShowsThePlaceholder() {
        type(5) // never confirmed with NEXT
        click(R.id.button_solve)
        assertEquals("NUMBERS: 5", text(R.id.text_numbers))
        assertTrue(awaitText(R.id.text_target, "SOLVE COMPLETE"))
        assertTrue(root.findViewById<Button>(R.id.button_solve).isEnabled)
    }

    @Test
    fun solveRunsInBackgroundAndEndsInThePlaceholderLabel() {
        for (n in listOf(1, 2, 3, 4)) {
            type(n)
            click(R.id.button_next)
        }
        assertEquals("NUMBERS: 1, 2, 3, 4", text(R.id.text_numbers))

        click(R.id.button_solve)
        // the call itself is off the main thread: label + disabled button are the observable state
        assertEquals("SOLVING…", text(R.id.text_target))
        assertFalse(root.findViewById<Button>(R.id.button_solve).isEnabled)

        assertTrue(awaitText(R.id.text_target, "SOLVE COMPLETE"))
        assertTrue(root.findViewById<Button>(R.id.button_solve).isEnabled)
    }

    @Test
    fun solverRejectionIsReportedAndTheLabelIsRestored() {
        // 256 passes the UI rule (|x| <= 256) but the solver itself requires |x| < 256
        retype(256)
        click(R.id.button_next)
        assertEquals("NUMBERS: 256", text(R.id.text_numbers))

        click(R.id.button_solve)
        assertTrue(awaitText(R.id.text_target, "TARGET: 24"))
        assertTrue(latestToast().contains("Solver rejected the input"))
        assertTrue(root.findViewById<Button>(R.id.button_solve).isEnabled)
    }
}

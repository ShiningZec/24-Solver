package com.a24solver.wakusei

import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
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
 * Robolectric tests for the input screen: keypad layout, sizing and colours, key wiring, sign
 * toggle, the TARGET latch (empty on entry, written back on exit), the scope aware CLEAR tap vs.
 * its long-press reset, DEL's long-press undo (with the haptic ticks), validation, and the
 * SOLVE → result screen → BACK round trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstFragmentTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var root: View
    private lateinit var navController: NavController

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
        bindFragments()
    }

    // ------------------------------------------------------------- helpers

    /** Re-reads the fragment tree, e.g. after the activity was recreated. */
    private fun bindFragments() {
        val fm = controller.get().supportFragmentManager
        fm.executePendingTransactions()

        val navHost = requireNotNull(
            fm.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        ) { "NavHostFragment not found" }
        navController = navHost.navController

        val child = navHost.childFragmentManager
        child.executePendingTransactions()
        root = (child.fragments.first() as FirstFragment).requireView()
    }

    private fun text(id: Int): String = root.findViewById<TextView>(id).text.toString()

    private fun activityText(id: Int): String =
        controller.get().findViewById<TextView>(id).text.toString()

    private fun click(id: Int) {
        root.findViewById<View>(id).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Performs the view's long click, i.e. what a ≥500 ms press ends up calling. */
    private fun longClick(id: Int) {
        root.findViewById<View>(id).performLongClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun type(vararg digits: Int) = digits.forEach {
        click(digitIds.getValue(it))
    }

    private fun latestToast(): String = ShadowToast.getTextOfLatestToast() ?: ""

    private fun toastCount(): Int = ShadowToast.shownToastCount()

    /**
     * The tick the app requested itself. The keys carry android:hapticFeedbackEnabled="false" (to
     * mute the platform's generic long-press buzz), so the app asks the window's view — that is
     * the shadow to inspect.
     */
    private fun lastAppHaptic(): Int = shadowOf(root.rootView).lastHapticFeedbackPerformed()

    private fun awaitDestination(destinationId: Int, timeoutMs: Long = 20_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (navController.currentDestination?.id == destinationId) return true
            Thread.sleep(20)
        }
        return navController.currentDestination?.id == destinationId
    }

    private fun targetLatch(): MaterialButton = root.findViewById(R.id.button_target)

    private fun rowOf(id: Int): List<Int> {
        val row = root.findViewById<View>(id).parent as ViewGroup
        return (0 until row.childCount).map { row.getChildAt(it).id }
    }

    private fun key(id: Int): View = root.findViewById(id)

    // ----------------------------------------------------- layout / colours

    @Test
    fun keypadRowsAreLaidOutAsSpecified() {
        // digit rows
        assertEquals(listOf(R.id.button1, R.id.button2, R.id.button3), rowOf(R.id.button1))
        assertEquals(listOf(R.id.button4, R.id.button5, R.id.button6), rowOf(R.id.button4))
        assertEquals(listOf(R.id.button7, R.id.button8, R.id.button9), rowOf(R.id.button7))
        // 4th row: [+/-] [0] [NEXT]
        assertEquals(listOf(R.id.button_sign, R.id.button0, R.id.button_next), rowOf(R.id.button_sign))
        // 5th row: [CLEAR] [DEL] [TARGET]
        assertEquals(listOf(R.id.button_clear, R.id.button_del, R.id.button_target), rowOf(R.id.button_clear))
    }

    @Test
    fun keypadBandIsEightyPercentOfTheOldSpace() {
        val keypad = key(R.id.keypad)
        val spacer = key(R.id.keypad_spacer)

        assertEquals(4f, (keypad.layoutParams as android.widget.LinearLayout.LayoutParams).weight, 0.01f)
        assertEquals(1f, (spacer.layoutParams as android.widget.LinearLayout.LayoutParams).weight, 0.01f)

        val band = keypad.height + spacer.height
        if (band > 0) { // a real layout pass happened
            assertEquals("keypad band", 0.8f, keypad.height.toFloat() / band, 0.02f)
        }
    }

    @Test
    fun keysHaveSideGapsABiggerLabelAndSolveIsTaller() {
        val metrics = controller.get().resources.displayMetrics
        val twentySp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, metrics)
        val keys = listOf(
            R.id.button1, R.id.button_sign, R.id.button_next, R.id.button_clear,
            R.id.button_del, R.id.button_target
        )
        for (id in keys) {
            val button = root.findViewById<Button>(id)
            val params = button.layoutParams as ViewGroup.MarginLayoutParams
            assertTrue("key $id needs a left gap", params.marginStart > 0)
            assertTrue("key $id needs a right gap", params.marginEnd > 0)
            assertEquals("label size of key $id", twentySp, button.textSize, 0.5f)
        }

        val solve = root.findViewById<Button>(R.id.button_solve)
        assertEquals("SOLVE height", 56f, solve.layoutParams.height / metrics.density, 0.5f)
    }

    @Test
    fun specialKeysUseTheRoseBackground() {
        val rose = controller.get().getColor(R.color.rose)
        val roseChecked = controller.get().getColor(R.color.rose_checked)
        val specialKeys = listOf(
            R.id.button_sign, R.id.button_next, R.id.button_clear, R.id.button_del, R.id.button_target
        )
        for (id in specialKeys) {
            val tint = ViewCompat.getBackgroundTintList(key(id))
            // note: an empty state set reads as "enabled = false", i.e. the disabled entry
            assertEquals(
                "background tint of $id",
                rose,
                tint?.getColorForState(intArrayOf(android.R.attr.state_enabled), rose)
            )
        }
        // the TARGET latch keeps a distinguishable checked colour
        val targetTint = ViewCompat.getBackgroundTintList(key(R.id.button_target))
        assertEquals(
            "checked tint of the TARGET latch",
            roseChecked,
            targetTint?.getColorForState(
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked), rose
            )
        )
    }

    @Test
    fun topBarIsShorterThanTheDefaultActionBar() {
        val toolbar = controller.get().findViewById<View>(R.id.toolbar)
        val appBar = toolbar.parent as ViewGroup
        val density = controller.get().resources.displayMetrics.density

        assertEquals(40f, appBar.layoutParams.height / density, 0.5f) // was ?attr/actionBarSize
        assertEquals(0, toolbar.minimumHeight)
    }

    // --------------------------------------------------------------- tests

    @Test
    fun everyDigitKeyTypesItsOwnDigit() {
        for (digit in 0..9) {
            val id = digitIds.getValue(digit)
            // the key shows the digit it must type ...
            assertEquals(digit.toString(), root.findViewById<Button>(id).text.toString())
            // ... and typing it lands in the buffer
            click(R.id.button_del)
            click(id)
            assertEquals("INPUT: $digit", text(R.id.text_current_input))
        }
    }

    @Test
    fun backspaceEditsTheBuffer() {
        type(1, 2, 3)
        assertEquals("INPUT: 123", text(R.id.text_current_input))

        click(R.id.button_del)
        assertEquals("INPUT: 12", text(R.id.text_current_input))
    }

    @Test
    fun signKeyNegatesTheCandidateBeingTyped() {
        click(R.id.button_sign)
        type(7)
        assertEquals("INPUT: -7", text(R.id.text_current_input))

        click(R.id.button_sign) // and back to positive
        assertEquals("INPUT: 7", text(R.id.text_current_input))

        click(R.id.button_sign)
        click(R.id.button_next)
        assertEquals("NUMBERS: -7", text(R.id.text_numbers))
    }

    @Test
    fun signKeyNegatesTheTarget() {
        click(R.id.button_target)
        type(2, 4)
        click(R.id.button_sign)
        assertEquals("TARGET INPUT: -24", text(R.id.text_current_input))

        click(R.id.button_next)
        assertEquals("TARGET: -24", text(R.id.text_target))
    }

    @Test
    fun targetIsALatchAndKeepsBothBuffers() {
        assertFalse(targetLatch().isChecked)

        type(7) // candidate buffer
        assertEquals("INPUT: 7", text(R.id.text_current_input))

        click(R.id.button_target)
        assertTrue(targetLatch().isChecked) // stays on after the click
        assertEquals("TARGET INPUT: _", text(R.id.text_current_input)) // entered empty
        assertEquals("TARGET: 24", text(R.id.text_target))

        type(9)
        assertEquals("TARGET INPUT: 9", text(R.id.text_current_input))
        click(R.id.button_del)
        assertTrue(targetLatch().isChecked) // still on after another key press
        assertEquals("TARGET INPUT: _", text(R.id.text_current_input))

        click(R.id.button_target)
        assertFalse(targetLatch().isChecked) // and stays off again
        assertEquals("TARGET: 24", text(R.id.text_target)) // nothing was typed -> unchanged
        assertEquals("INPUT: 7", text(R.id.text_current_input)) // candidate buffer survived
    }

    @Test
    fun targetScopeStartsEmptyAndWritesBackOnExit() {
        click(R.id.button_target)
        assertEquals("TARGET INPUT: _", text(R.id.text_current_input)) // never pre-filled
        assertEquals("TARGET: 24", text(R.id.text_target))

        type(6, 0)
        click(R.id.button_target) // leaving the scope writes the typed value back

        assertFalse(targetLatch().isChecked)
        assertEquals("TARGET: 60", text(R.id.text_target))
        assertEquals("INPUT: _", text(R.id.text_current_input))
    }

    @Test
    fun leavingTargetScopeWithEmptyInputKeepsTheTarget() {
        click(R.id.button_target)
        click(R.id.button_target)

        assertFalse(targetLatch().isChecked)
        assertEquals("TARGET: 24", text(R.id.text_target))
        assertEquals(0, toastCount()) // nothing to report
    }

    @Test
    fun leavingTargetScopeWithAnInvalidValueKeepsTheLatchOn() {
        click(R.id.button_target)
        type(1, 6, 3, 8, 5) // out of range
        click(R.id.button_target)

        assertTrue(targetLatch().isChecked) // still editing: the typed value is not lost
        assertEquals("TARGET INPUT: 16385", text(R.id.text_current_input))
        assertEquals("TARGET: 24", text(R.id.text_target))
        assertTrue(latestToast().contains("±16384"))

        // CLEAR (target scope) fixes it, then the latch can be switched off
        click(R.id.button_clear)
        click(R.id.button_target)
        assertFalse(targetLatch().isChecked)
        assertEquals("TARGET: 24", text(R.id.text_target))
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
    fun candidateBoundIsStrictlyLessThan256() {
        type(2, 5, 6)
        click(R.id.button_next) // 256 -> rejected
        assertEquals("NUMBERS: (none)", text(R.id.text_numbers))
        assertTrue(latestToast().contains("|x| < 256"))

        click(R.id.button_del)
        click(R.id.button_next) // 25 -> fine
        assertEquals("NUMBERS: 25", text(R.id.text_numbers))

        type(2, 5, 5)
        click(R.id.button_next) // 255 -> the largest allowed value
        assertEquals("NUMBERS: 25, 255", text(R.id.text_numbers))
    }

    @Test
    fun clearTapOnlyClearsItsScopeAndStaysSilent() {
        type(1)
        click(R.id.button_next)
        type(2)
        click(R.id.button_next)
        assertEquals("NUMBERS: 1, 2", text(R.id.text_numbers))

        type(3) // uncommitted buffer
        click(R.id.button_clear)

        assertEquals("NUMBERS: (none)", text(R.id.text_numbers))
        assertEquals("INPUT: _", text(R.id.text_current_input))
        assertEquals("TARGET: 24", text(R.id.text_target)) // target untouched
        assertEquals("a CLEAR tap says nothing", 0, toastCount())
    }

    @Test
    fun clearTapInTargetScopeOnlyResetsTheTargetSilently() {
        click(R.id.button_target)
        click(R.id.button_clear) // target scope: back to the default
        assertEquals("TARGET: 24", text(R.id.text_target))
        assertEquals("TARGET INPUT: _", text(R.id.text_current_input))

        type(6, 0)
        click(R.id.button_next)
        assertEquals("TARGET: 60", text(R.id.text_target))

        click(R.id.button_target) // back to candidates
        type(1)
        click(R.id.button_next)
        assertEquals("NUMBERS: 1", text(R.id.text_numbers))

        click(R.id.button_target) // target scope again
        click(R.id.button_clear)

        assertEquals("TARGET: 24", text(R.id.text_target))
        assertEquals("NUMBERS: 1", text(R.id.text_numbers)) // candidates untouched
        assertEquals("a CLEAR tap says nothing", 0, toastCount())
    }

    @Test
    fun clearLongPressResetsEverythingTicksAndReports() {
        type(1)
        click(R.id.button_next)
        click(R.id.button_target)
        click(R.id.button_clear)
        type(6, 0)
        click(R.id.button_next)
        assertEquals("TARGET: 60", text(R.id.text_target))

        longClick(R.id.button_clear) // the deliberate "start over" gesture

        assertEquals("NUMBERS: (none)", text(R.id.text_numbers))
        assertEquals("TARGET: 24", text(R.id.text_target))
        assertEquals("a long press ticks", HapticFeedbackConstants.KEYBOARD_TAP, lastAppHaptic())
        assertFalse("the platform's own long-press buzz is muted", key(R.id.button_clear).isHapticFeedbackEnabled)
        assertEquals("All Numbers Set to Default", latestToast())
    }

    @Test
    fun clearTapDoesNotTick() {
        type(1)
        click(R.id.button_next)

        click(R.id.button_clear)

        assertEquals(-1, lastAppHaptic()) // no feedback for a plain tap
    }

    @Test
    fun deleteLongPressDropsTheLastCandidateAndTicks() {
        for (n in listOf(1, 2, 3)) {
            type(n)
            click(R.id.button_next)
        }
        assertEquals("NUMBERS: 1, 2, 3", text(R.id.text_numbers))

        longClick(R.id.button_del)

        assertEquals("NUMBERS: 1, 2", text(R.id.text_numbers))
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, lastAppHaptic())
        assertFalse("the platform's own long-press buzz is muted", key(R.id.button_del).isHapticFeedbackEnabled)
    }

    @Test
    fun deleteLongPressWithNoCandidatesStaysQuiet() {
        longClick(R.id.button_del)

        assertEquals("NUMBERS: (none)", text(R.id.text_numbers))
        assertEquals("nothing was removed, so nothing ticks", -1, lastAppHaptic())
        assertEquals(0, toastCount())
    }

    @Test
    fun targetRangeIsEnforced() {
        click(R.id.button_target)
        type(1, 6, 3, 8, 4)
        assertEquals("TARGET INPUT: 16384", text(R.id.text_current_input))
        click(R.id.button_next)
        assertEquals("TARGET: 16384", text(R.id.text_target)) // the bound itself is allowed

        type(1, 6, 3, 8, 5) // 16385 -> out of range (the buffer is empty after the commit above)
        click(R.id.button_next)
        assertEquals("TARGET: 16384", text(R.id.text_target)) // unchanged
        assertEquals("TARGET INPUT: 16385", text(R.id.text_current_input)) // kept for editing
        assertTrue(latestToast().contains("±16384"))
    }

    @Test
    fun solveWithoutNumbersIsRejectedBeforeCallingTheSolver() {
        click(R.id.button_solve)
        assertTrue(latestToast().contains("Add at least one number"))
        assertEquals("TARGET: 24", text(R.id.text_target))
        assertTrue(root.findViewById<Button>(R.id.button_solve).isEnabled)
        assertEquals(R.id.FirstFragment, navController.currentDestination?.id)
    }

    @Test
    fun solveCommitsPendingInputAndShowsTheResultScreen() {
        type(5) // never confirmed with NEXT
        click(R.id.button_solve)

        assertEquals("NUMBERS: 5", text(R.id.text_numbers))
        assertTrue(awaitDestination(R.id.SecondFragment))
        assertTrue(activityText(R.id.text_result_header).contains("0 answers for target 24"))
        assertEquals(
            controller.get().getString(R.string.result_empty),
            activityText(R.id.text_answers)
        )
    }

    @Test
    fun solveRunsInBackgroundThenShowsAndReturnsFromTheResultScreen() {
        for (n in listOf(1, 2, 3, 4)) {
            type(n)
            click(R.id.button_next)
        }
        assertEquals("NUMBERS: 1, 2, 3, 4", text(R.id.text_numbers))

        click(R.id.button_solve)
        // the call itself is off the main thread: label + disabled button are the observable state
        assertEquals("SOLVING…", text(R.id.text_target))
        assertFalse(root.findViewById<Button>(R.id.button_solve).isEnabled)

        // ... and the answers land on the second screen
        assertTrue(awaitDestination(R.id.SecondFragment))
        assertTrue(activityText(R.id.text_result_header).contains("242 answers for target 24"))

        val answers = activityText(R.id.text_answers)
        assertEquals(242, answers.split("\n").size)
        assertTrue(answers.contains("(1 + 2 + 3) * 4"))

        // BACK returns to the input screen
        controller.get().findViewById<Button>(R.id.button_back).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(awaitDestination(R.id.FirstFragment))
        assertEquals("TARGET: 24", activityText(R.id.text_target))
    }

    @Test
    fun resultScreenSwitchesBetweenEveryExpressionAndTheEssentialOnes() {
        for (n in listOf(1, 2, 3, 4)) {
            type(n)
            click(R.id.button_next)
        }
        click(R.id.button_solve)
        assertTrue(awaitDestination(R.id.SecondFragment))

        val activity = controller.get()
        val toggle = activity.findViewById<MaterialButtonToggleGroup>(R.id.toggle_solution_set)
        assertEquals(View.VISIBLE, toggle.visibility)
        assertEquals(
            "All (242)",
            activity.findViewById<MaterialButton>(R.id.button_show_all).text.toString()
        )
        assertEquals(
            "Essential (28)",
            activity.findViewById<MaterialButton>(R.id.button_show_essential).text.toString()
        )

        // the "essential" switch drops the expressions that only differ by commutativity /
        // associativity of + and *
        activity.findViewById<MaterialButton>(R.id.button_show_essential).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            activityText(R.id.text_result_header)
                .contains("28 essentially different answers for target 24")
        )
        assertEquals(28, activityText(R.id.text_answers).split("\n").size)

        activity.findViewById<MaterialButton>(R.id.button_show_all).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(242, activityText(R.id.text_answers).split("\n").size)
        assertTrue(activityText(R.id.text_result_header).contains("242 answers for target 24"))
    }

    @Test
    fun theSolutionSetSwitchIsHiddenWhenThereIsNothingToSwitchBetween() {
        type(5) // 5 alone cannot reach 24
        click(R.id.button_solve)
        assertTrue(awaitDestination(R.id.SecondFragment))

        val toggle = controller.get().findViewById<View>(R.id.toggle_solution_set)
        assertEquals(View.GONE, toggle.visibility)
    }

    @Test
    fun theSolutionSetChoiceSurvivesAConfigurationChange() {
        for (n in listOf(1, 2, 3, 4)) {
            type(n)
            click(R.id.button_next)
        }
        click(R.id.button_solve)
        assertTrue(awaitDestination(R.id.SecondFragment))

        controller.get().findViewById<MaterialButton>(R.id.button_show_essential).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(28, activityText(R.id.text_answers).split("\n").size)

        controller.recreate() // the choice lives in the ViewModel, so it has to come back
        controller.get().supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(activityText(R.id.text_result_header).contains("essentially different"))
        assertEquals(28, activityText(R.id.text_answers).split("\n").size)
        assertTrue(
            controller.get().findViewById<MaterialButton>(R.id.button_show_essential).isChecked
        )
    }

    @Test
    fun theInputSurvivesAConfigurationChange() {
        type(1)
        click(R.id.button_next)
        type(9)
        click(R.id.button_next)
        type(7) // typed but not committed

        click(R.id.button_target)
        type(6, 0) // typed into the target buffer, not committed either

        controller.recreate() // rotation
        bindFragments()

        assertEquals("NUMBERS: 1, 9", text(R.id.text_numbers))
        assertEquals("TARGET: 24", text(R.id.text_target)) // still uncommitted
        assertEquals("TARGET INPUT: 60", text(R.id.text_current_input))
        assertTrue(targetLatch().isChecked) // the latch is restored as well

        click(R.id.button_target) // leaving the scope commits the restored buffer
        assertEquals("TARGET: 60", text(R.id.text_target))
        assertEquals("INPUT: 7", text(R.id.text_current_input)) // the other buffer survived too
    }
}

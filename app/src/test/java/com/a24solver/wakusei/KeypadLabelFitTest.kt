package com.a24solver.wakusei

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.navigation.fragment.NavHostFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Keypad labels must fit inside their key.
 *
 * The keys are a third of the screen wide (~100dp) and the Material button style spends 24dp of that
 * on padding at each end, which left ~53dp for the label — not enough for "TARGET" (~82dp),
 * "CLEAR" (~68dp) or "クリア" (~60dp), so those wrapped onto two lines. The style now takes the
 * padding down to 4dp per side, forbids wrapping and lets the label shrink when a large system font
 * scale would not fit.
 *
 * Robolectric cannot measure text with real font metrics, so the fit check below uses conservative
 * per-character estimates; what it *can* check exactly — and what this pins down — is the layout
 * attributes that make the fit possible, plus the fact that even at the smallest auto-shrink size the
 * longest label still fits (so a label can never be clipped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeypadLabelFitTest {

    private lateinit var controller: ActivityController<MainActivity>

    /**
     * The keys that carry a word rather than a single digit. SOLVE is deliberately not here: it is a
     * full-width button with room to spare, and it keeps the Material padding.
     */
    private val labelKeyIds = listOf(
        R.id.button_sign, R.id.button_next, R.id.button_clear, R.id.button_del, R.id.button_target
    )

    @Before
    fun setUp() {
        markHelpSeen(RuntimeEnvironment.getApplication())
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.get().supportFragmentManager.executePendingTransactions()
    }

    private fun key(id: Int): Button = controller.get().findViewById(id)

    private fun density(): Float = controller.get().resources.displayMetrics.density

    // ------------------------------------------------------------- attributes

    @Test
    fun thePaddingLeavesRoomForAWord() {
        val expected = (KEYPAD_PADDING_DP * density()).toInt()

        for (id in labelKeyIds) {
            val button = key(id)
            assertEquals("start padding of ${button.text}", expected, button.paddingStart)
            assertEquals("end padding of ${button.text}", expected, button.paddingEnd)
            // what the text layout actually uses: the start/end padding is resolved into these
            assertEquals("left padding of ${button.text}", expected, button.paddingLeft)
            assertEquals("right padding of ${button.text}", expected, button.paddingRight)
            assertTrue(
                "the Material default padding is back: ${button.text} has only " +
                    "${button.width - button.paddingLeft - button.paddingRight}px for its label",
                button.width - button.paddingLeft - button.paddingRight > button.width / 2
            )
        }
    }

    @Test
    fun aLabelIsNeverAllowedToWrap() {
        for (id in labelKeyIds) {
            assertEquals("${key(id).text} must stay on one line", 1, key(id).maxLines)
            assertTrue("${key(id).text} would be ellipsized", key(id).ellipsize == null)
        }
    }

    @Test
    fun aLabelShrinksInsteadOfBeingClipped() {
        val minimum = (MIN_TEXT_SP_SIZE * density()).toInt()
        val maximum = (MAX_TEXT_SP_SIZE * density()).toInt()

        for (id in labelKeyIds) {
            val button = key(id)
            assertEquals(
                "${button.text} should shrink automatically",
                TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM,
                TextViewCompat.getAutoSizeTextType(button)
            )
            assertEquals(
                "${button.text} minimum size",
                minimum,
                TextViewCompat.getAutoSizeMinTextSize(button)
            )
            assertEquals(
                "${button.text} maximum size",
                maximum,
                TextViewCompat.getAutoSizeMaxTextSize(button)
            )
        }
    }

    // ------------------------------------------------------------------- fit

    @Test
    fun everyLabelFitsEvenAtTheSmallestSizeItMayShrinkTo() {
        assertLabelsFit()
    }

    @Test
    @Config(qualifiers = "zh")
    fun everyChineseLabelFits() {
        assertLabelsFit()
    }

    @Test
    @Config(qualifiers = "ja")
    fun everyJapaneseLabelFits() {
        assertLabelsFit()
    }

    @Test
    @Config(qualifiers = "en-rGB")
    fun everyEnglishLabelFits() {
        assertLabelsFit()
    }

    private fun assertLabelsFit() {
        for (id in labelKeyIds) {
            val button = key(id)
            val label = button.text.toString()
            val availableDp = (button.width - button.paddingLeft - button.paddingRight) / density()
            val neededDp = estimatedWidthDp(label, MIN_TEXT_SP_SIZE)

            assertTrue(
                "'$label' needs about %.0fdp at ${MIN_TEXT_SP_SIZE}sp but the tile only offers " +
                    "%.0fdp, so it could be clipped".format(neededDp, availableDp),
                neededDp <= availableDp
            )
        }
    }

    /**
     * A deliberately pessimistic width estimate: 0.75 em for Latin letters and digits (the widest
     * capitals in Roboto are under 0.8 em) and a full em for CJK, kana and the full-width plus/minus
     * the Chinese and Japanese labels use.
     */
    private fun estimatedWidthDp(label: String, textSizeSp: Double): Double =
        label.sumOf { character ->
            if (isFullWidth(character)) textSizeSp else textSizeSp * LATIN_EM
        }

    private fun isFullWidth(character: Char): Boolean =
        character.code in 0x1100..0x115F || // Hangul Jamo
            character.code in 0x2E80..0xA4CF || // CJK radicals .. Yi
            character.code in 0xAC00..0xD7A3 || // Hangul syllables
            character.code in 0xF900..0xFAFF || // CJK compatibility ideographs
            character.code in 0xFE30..0xFE6F || // CJK compatibility forms
            character.code in 0xFF00..0xFF60 || // full-width forms
            character.code in 0xFFE0..0xFFE6

    private companion object {
        /** What the keypad style sets: a narrow tile, not a wide dialog button. */
        const val KEYPAD_PADDING_DP = 4

        /** The smallest size a keypad label may shrink to (see styles.xml). */
        const val MIN_TEXT_SP_SIZE = 12.0
        const val MAX_TEXT_SP_SIZE = 20.0

        /** Pessimistic average advance width of a Latin glyph, in em. */
        const val LATIN_EM = 0.75
    }
}

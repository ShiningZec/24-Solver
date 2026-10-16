package com.a24solver.wakusei

import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.a24solver.wakusei.databinding.FragmentFirstBinding
import com.a24solver.wakusei.solver.SolveOutcome
import com.a24solver.wakusei.solver.Solver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Input screen.
 *
 * Digits fill the buffer of the active scope: the candidate numbers, or the target while the
 * TARGET latch is on. NEXT commits the buffer, SOLVE validates the input, runs
 * [Solver.solve] on a background dispatcher and then switches to the result screen.
 *
 * CLEAR is scope aware — with the latch off it clears the candidate numbers, with the latch on
 * it resets the target to [DEFAULT_TARGET]; a long press resets both. DEL's long press drops the
 * last candidate number. Both long presses tick weakly, through the system's own feedback.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val resultViewModel: SolveResultViewModel by activityViewModels()

    /** What the target label currently shows. */
    private enum class TargetLabel { TARGET, SOLVING }

    private val numbers = mutableListOf<Int>()

    /** Buffer of the candidate numbers (may start with '-'). */
    private var numberInput = ""

    /** Buffer of the target value (may start with '-'). */
    private var targetInput = DEFAULT_TARGET.toString()

    private var target = DEFAULT_TARGET

    /** The TARGET latch: true while the keypad edits the target instead of the candidates. */
    private var targetEditing = false

    private var targetLabel = TargetLabel.TARGET
    private var solveJob: Job? = null

    /** The buffer the digit keys currently edit. */
    private val buffer: String
        get() = if (targetEditing) targetInput else numberInput

    private fun setBuffer(value: String) {
        if (targetEditing) targetInput = value else numberInput = value
    }

    // --------------------------------------------------------------- input

    private fun onDigitPressed(digit: Int) {
        val current = buffer
        if (current.count { it.isDigit() } >= MAX_DIGITS) return
        val next = when {
            current.isEmpty() -> digit.toString()
            current == "-" -> "-$digit"
            current == "0" -> digit.toString()   // keep the buffer free of leading zeros
            current == "-0" -> "-$digit"
            else -> current + digit
        }
        setBuffer(next)
        onInputChanged()
    }

    /** `+/-`: negates the value being typed, or starts a negative one. */
    private fun onSignPressed() {
        val current = buffer
        setBuffer(
            when {
                current.isEmpty() -> "-"
                current == "-" -> ""
                current.startsWith("-") -> current.substring(1)
                else -> "-$current"
            }
        )
        onInputChanged()
    }

    private fun onDeletePressed() {
        if (buffer.isNotEmpty()) setBuffer(buffer.dropLast(1))
        onInputChanged()
    }

    /**
     * CLEAR tap, scope aware and silent:
     *  - TARGET latch off → clears the candidate numbers only
     *  - TARGET latch on  → resets the target to [DEFAULT_TARGET] only
     */
    private fun onClearPressed() {
        if (targetEditing) resetTarget() else clearCandidates()
        onInputChanged()
    }

    /**
     * CLEAR long press ([LONG_PRESS_MS]): the deliberate "start over" gesture — candidates and
     * target both go back to their defaults, and it is the only CLEAR variant that reports back.
     * It replaced the earlier double tap, which was too easy to trigger by accident.
     */
    private fun onClearLongPressed() {
        clearCandidates()
        resetTarget()
        hapticFeedback(binding.buttonClear)
        toast(getString(R.string.msg_cleared_all))
        onInputChanged()
    }

    /**
     * DEL long press ([LONG_PRESS_MS]): drops the last committed candidate number. The tick is
     * emitted only when a number was actually removed, so the key stays quiet (and harmless) when
     * the list is already empty.
     */
    private fun onDeleteLongPressed() {
        if (numbers.isEmpty()) return
        numbers.removeAt(numbers.lastIndex)
        hapticFeedback(binding.buttonDel)
        onInputChanged()
    }

    /**
     * Weak, Android-managed key feedback for the long-press gestures.
     *
     * [HapticFeedbackConstants.KEYBOARD_TAP] goes through [View.performHapticFeedback]: no
     * permission (no Vibrator access) and the system silences it whenever the user turns touch
     * feedback off (`Settings.System.HAPTIC_FEEDBACK_ENABLED`), so the phone's settings stay in
     * charge instead of the app.
     *
     * The tick is requested on the *window's* view because the keys themselves declare
     * `android:hapticFeedbackEnabled="false"` (see fragment_first.xml): that switch exists only to
     * mute the platform's generic long-press buzz, which would otherwise fire on every long press
     * — including a DEL press that had nothing to delete. This way a tick means exactly what it
     * says: the requested action really happened, and it is still the system's own feedback.
     */
    private fun hapticFeedback(view: View) {
        view.rootView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun onNextPressed() {
        if (targetEditing) commitTarget() else commitNumber()
    }

    /** Guards against the programmatic isChecked update below re-entering this listener. */
    private var restoringTargetLatch = false

    /**
     * The TARGET latch: once switched on it stays on until it is switched off again.
     *
     * Switching it on never pre-fills the input — the label keeps showing the target in use and
     * the user types a new value from scratch ("TARGET INPUT: _"). Switching it off writes what
     * was typed back to the target, unless the input is empty or a lone "-", which carry no
     * value. An out-of-range input is rejected and the latch stays on, so nothing the user typed
     * is thrown away silently.
     */
    private fun onTargetToggled(active: Boolean) {
        if (restoringTargetLatch) return

        if (active) {
            targetEditing = true
            targetInput = ""
            refreshDisplay()
            return
        }

        if (targetInput.isNotEmpty() && targetInput != "-" && !commitTarget()) {
            restoringTargetLatch = true
            binding.buttonTarget.isChecked = true // keep editing: the typed value was rejected
            restoringTargetLatch = false
            return
        }

        if (targetInput == "-") targetInput = ""
        targetEditing = false
        refreshDisplay()
    }

    private fun onInputChanged() {
        targetLabel = TargetLabel.TARGET
        refreshDisplay()
    }

    // ------------------------------------------------------------- commits

    private fun clearCandidates() {
        numbers.clear()
        numberInput = ""
    }

    private fun resetTarget() {
        target = DEFAULT_TARGET
        targetInput = "" // the label carries the value; the buffer is only what is being typed
    }

    private fun commitNumber(): Boolean {
        val raw = numberInput
        if (raw.isEmpty() || raw == "-") {
            toast(getString(R.string.err_empty_input))
            return false
        }
        val value = raw.toIntOrNull()
        if (value == null || abs(value) >= MAX_ABS_NUMBER) {
            toast(getString(R.string.err_number_range, MAX_ABS_NUMBER))
            return false
        }
        if (numbers.size >= MAX_NUMBERS) {
            toast(
                resources.getQuantityString(
                    R.plurals.err_max_numbers, MAX_NUMBERS, MAX_NUMBERS
                )
            )
            return false
        }
        numbers.add(value)
        numberInput = ""
        onInputChanged()
        return true
    }

    private fun commitTarget(): Boolean {
        val raw = targetInput
        if (raw.isEmpty() || raw == "-") {
            toast(getString(R.string.err_empty_input))
            return false
        }
        val value = raw.toIntOrNull()
        if (value == null || abs(value) > MAX_ABS_TARGET) {
            toast(getString(R.string.err_target_range, MAX_ABS_TARGET))
            return false
        }
        target = value
        targetInput = "" // committed: the label carries the value from here on
        onInputChanged()
        return true
    }

    // --------------------------------------------------------------- solve

    private fun onSolvePressed() {
        if (solveJob?.isActive == true) return

        // Whatever is still being typed counts as input: commit it first, so the value being
        // validated is exactly what the user sees on screen.
        val pending = if (targetEditing) targetInput else numberInput
        if (pending.isNotEmpty() && pending != "-") {
            val committed = if (targetEditing) commitTarget() else commitNumber()
            if (!committed) return
        }

        validate()?.let { message ->
            toast(message)
            return
        }

        val candidates: List<Int> = numbers.toList()
        val targetSnapshot = target

        targetLabel = TargetLabel.SOLVING
        refreshDisplay()
        binding.buttonSolve.isEnabled = false

        solveJob = viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching { Solver.solve(targetSnapshot, candidates) }
            }

            val view = _binding ?: return@launch
            view.buttonSolve.isEnabled = true

            outcome
                .onSuccess { solutions ->
                    Log.d(
                        TAG,
                        "Solver.solve(target=$targetSnapshot, numbers=$candidates) " +
                            "-> ${solutions.allSolutions.size} answer(s), " +
                            "${solutions.essentialSolutions.size} essentially different"
                    )
                    targetLabel = TargetLabel.TARGET
                    refreshDisplay()
                    showAnswers(solutions)
                }
                .onFailure { error ->
                    targetLabel = TargetLabel.TARGET
                    refreshDisplay()
                    Log.w(TAG, "the solver rejected the input", error)
                    toast(
                        getString(
                            R.string.err_solver,
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
        }
    }

    /** Hands the answers to the shared view model and switches to the result screen. */
    private fun showAnswers(solutions: SolveOutcome) {
        resultViewModel.setResult(solutions)
        if (!isAdded) return
        runCatching { findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment) }
            .onFailure { Log.w(TAG, "could not open the result screen", it) }
    }

    /**
     * Input validation performed before [Solver.solve] is called: at least one and at most
     * [MAX_NUMBERS] candidates, `abs(candidate) < MAX_ABS_NUMBER` (strict) and
     * `abs(target) <= MAX_ABS_TARGET`. Returns an error message, or null when input is valid.
     */
    private fun validate(): String? {
        if (numbers.isEmpty()) return getString(R.string.err_no_numbers)
        if (numbers.size > MAX_NUMBERS) {
            return resources.getQuantityString(
                R.plurals.err_max_numbers, MAX_NUMBERS, MAX_NUMBERS
            )
        }
        if (numbers.any { abs(it) >= MAX_ABS_NUMBER }) {
            return getString(R.string.err_number_range, MAX_ABS_NUMBER)
        }
        if (abs(target) > MAX_ABS_TARGET) {
            return getString(R.string.err_target_range, MAX_ABS_TARGET)
        }
        return null
    }

    // ------------------------------------------------------------- display

    private fun refreshDisplay() {
        val view = _binding ?: return

        view.textTarget.text = when (targetLabel) {
            TargetLabel.TARGET -> getString(R.string.target_label, target)
            TargetLabel.SOLVING -> getString(R.string.solving)
        }

        view.textNumbers.text = getString(
            R.string.numbers_label,
            if (numbers.isEmpty()) getString(R.string.numbers_empty)
            else numbers.joinToString(", ")
        )

        val typed = buffer.ifEmpty { getString(R.string.input_empty) }
        view.textCurrentInput.text = if (targetEditing) {
            getString(R.string.target_input_label, typed)
        } else {
            getString(R.string.current_input_label, typed)
        }
    }

    private fun toast(message: String) {
        activity?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    // -------------------------------------------------------------- wiring

    private fun setupButtons() {
        // Table driven, so a key and the digit it produces cannot drift apart.
        val digitButtons: Map<Button, Int> = mapOf(
            binding.button0 to 0,
            binding.button1 to 1,
            binding.button2 to 2,
            binding.button3 to 3,
            binding.button4 to 4,
            binding.button5 to 5,
            binding.button6 to 6,
            binding.button7 to 7,
            binding.button8 to 8,
            binding.button9 to 9
        )
        digitButtons.forEach { (button, digit) ->
            button.setOnClickListener { onDigitPressed(digit) }
        }

        binding.buttonSign.setOnClickListener { onSignPressed() }

        binding.buttonDel.setOnClickListener { onDeletePressed() }
        binding.buttonDel.setOnLongClickListener {
            onDeleteLongPressed()
            true
        }

        binding.buttonClear.setOnClickListener { onClearPressed() }
        binding.buttonClear.setOnLongClickListener {
            onClearLongPressed()
            true
        }

        binding.buttonNext.setOnClickListener { onNextPressed() }
        binding.buttonSolve.setOnClickListener { onSolvePressed() }

        // checkable MaterialButton: the widget toggles itself, the listener only follows it
        binding.buttonTarget.addOnCheckedChangeListener { _, isChecked ->
            onTargetToggled(isChecked)
        }
    }

    // ----------------------------------------------------------- lifecycle

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons()
        if (savedInstanceState != null) restoreInput(savedInstanceState)
        refreshDisplay()
    }

    /**
     * Keeps what the user has typed across a configuration change (rotation, theme switch, …).
     *
     * The candidate list, the target and both uncommitted buffers are plain fields, so without this
     * they would be silently dropped and the screen would come back empty.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(KEY_NUMBERS, numbers.toIntArray())
        outState.putInt(KEY_TARGET, target)
        outState.putString(KEY_NUMBER_INPUT, numberInput)
        outState.putString(KEY_TARGET_INPUT, targetInput)
        outState.putBoolean(KEY_TARGET_EDITING, targetEditing)
    }

    private fun restoreInput(state: Bundle) {
        numbers.clear()
        state.getIntArray(KEY_NUMBERS)?.forEach(numbers::add)
        target = state.getInt(KEY_TARGET, DEFAULT_TARGET)
        numberInput = state.getString(KEY_NUMBER_INPUT).orEmpty()
        targetInput = state.getString(KEY_TARGET_INPUT).orEmpty()

        // the widget toggles itself and reports back through the listener, which would clear the
        // buffer we just restored — so the latch is put back without letting the listener react
        targetEditing = state.getBoolean(KEY_TARGET_EDITING, false)
        restoringTargetLatch = true
        binding.buttonTarget.isChecked = targetEditing
        restoringTargetLatch = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        solveJob?.cancel()
        _binding = null
    }

    companion object {
        private const val TAG = "FirstFragment"

        private const val KEY_NUMBERS = "numbers"
        private const val KEY_TARGET = "target"
        private const val KEY_NUMBER_INPUT = "numberInput"
        private const val KEY_TARGET_INPUT = "targetInput"
        private const val KEY_TARGET_EDITING = "targetEditing"

        /** Input limits enforced before the solver is called. */
        const val MAX_NUMBERS = 5

        /** Strict upper bound: a candidate must satisfy `|x| < MAX_ABS_NUMBER`. */
        const val MAX_ABS_NUMBER = 256
        const val MAX_ABS_TARGET = 16384

        const val DEFAULT_TARGET = 24

        /** Longest digit string a buffer accepts. */
        private const val MAX_DIGITS = 6

        /**
         * Long-press threshold in milliseconds (x = 500).
         *
         * The gestures use the platform's own long-press timeout
         * ([android.view.ViewConfiguration.getLongPressTimeout], 500 ms on every standard Android
         * version), so this constant documents the value instead of enforcing it. Making x
         * configurable is a P2 TODO (see README).
         */
        const val LONG_PRESS_MS = 500L
    }
}

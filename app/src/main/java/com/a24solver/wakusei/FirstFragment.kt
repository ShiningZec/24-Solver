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
import com.a24solver.wakusei.settings.Settings
import com.a24solver.wakusei.solver.SolveOutcome
import com.a24solver.wakusei.solver.Solver
import com.a24solver.wakusei.sound.SoundEffects
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

    private val settings by lazy { Settings(requireContext()) }

    /** What the target label currently shows. */
    private enum class TargetLabel { TARGET, SOLVING }

    /**
     * The sounds the input screen makes. Which control plays what is decided at each listener (see
     * [setupButtons]); this only names the three gestures.
     */
    private enum class KeySound { TONE, SOLVE, RESET }

    private val numbers = mutableListOf<Int>()

    /** Buffer of the candidate numbers (may start with '-'). */
    private var numberInput = ""

    /** Buffer of the target value (may start with '-'). */
    private var targetInput = DEFAULT_TARGET.toString()

    private var target = DEFAULT_TARGET

    /**
     * Whether [target] has already been established for the puzzle on screen.
     *
     * The fragment instance outlives its view (going to the settings screen and back rebuilds the
     * view, not the fragment), so this is what tells "the screen is new" apart from "the view is
     * new": only a genuinely new screen takes the default target from the settings, and a target the
     * user is working with is never overwritten behind their back.
     */
    private var targetInitialised = false

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
     * CLEAR long press (see [LongPressGesture], threshold from the settings): the deliberate
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
     * DEL long press (same gesture): "throw away what I am typing; if I am not typing anything,
     * take back the last number I committed".
     *
     *  1. the buffer of the active scope is not empty → empty it. The active scope is the candidate
     *     numbers, or the target while the TARGET latch is on: the same scope a DEL *tap* edits, so
     *     the two variants of the key always act on the same thing.
     *  2. nothing typed but numbers committed → drop the last one.
     *  3. neither → nothing at all, not even a tick.
     *
     * The tick marks the two cases where something really went away (see [hapticFeedback]); the
     * key's sound is the keypad's, not a report of what happened, so it plays either way.
     */
    private fun onDeleteLongPressed() {
        when {
            buffer.isNotEmpty() -> setBuffer("")
            numbers.isNotEmpty() -> numbers.removeAt(numbers.lastIndex)
            else -> return
        }
        hapticFeedback(binding.buttonDel)
        onInputChanged()
    }

    /**
     * Android-managed key feedback for the long-press gestures.
     *
     * [HapticFeedbackConstants.LONG_PRESS] goes through [View.performHapticFeedback]: no permission
     * (no `Vibrator` access) and the system silences it whenever the user turns touch feedback off
     * (`Settings.System.HAPTIC_FEEDBACK_ENABLED`), so the phone's settings stay in charge instead of
     * the app.
     *
     * Two details are fixes for the bug where a long press never actually vibrated:
     *
     *  - it is requested on **the key that was pressed** (it used to be requested on the window root
     *    to dodge `android:hapticFeedbackEnabled="false"` on the keys — but the keys no longer need
     *    that attribute, because the app measures the long press itself and the platform's own buzz
     *    never fires; `FirstFragmentTest` asserts that the keys keep haptics enabled);
     *  - the constant is `LONG_PRESS`, not `KEYBOARD_TAP`: the keyboard tick is the faintest haptic
     *    Android has and is nearly imperceptible on many devices, while this gesture is a deliberate
     *    "start over" and deserves the standard long-press thump.
     *
     * The global user setting is deliberately respected: no `FLAG_IGNORE_GLOBAL_SETTING`.
     */
    private fun hapticFeedback(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
        playSound(KeySound.TONE)

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
        target = settings.defaultTarget
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
        // Table driven, so a key and the digit it produces cannot drift apart. All ten digits are
        // keypad-tone keys.
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
            button.setOnClickListener {
                onDigitPressed(digit)
                playSound(KeySound.TONE)
            }
        }

        // The other five tone keys: [+/-], NEXT, CLEAR, DEL, TARGET — fifteen in all. Their long
        // presses play the reset arpeggio instead, and never a tone as well.
        binding.buttonSign.setOnClickListener {
            onSignPressed()
            playSound(KeySound.TONE)
        }

        binding.buttonNext.setOnClickListener {
            onNextPressed()
            playSound(KeySound.TONE)
        }

        binding.buttonClear.setOnClickListener {
            onClearPressed()
            playSound(KeySound.TONE)
        }
        LongPressGesture(
            view = binding.buttonClear,
            thresholdMs = { settings.longPressMs },
            onLongPress = {
                onClearLongPressed()
                playSound(KeySound.RESET)
            }
        )

        binding.buttonDel.setOnClickListener {
            onDeletePressed()
            playSound(KeySound.TONE)
        }
        LongPressGesture(
            view = binding.buttonDel,
            thresholdMs = { settings.longPressMs },
            onLongPress = {
                onDeleteLongPressed()
                playSound(KeySound.RESET)
            }
        )

        // SOLVE is the one key that plays a phrase rather than a single note.
        binding.buttonSolve.setOnClickListener {
            onSolvePressed()
            playSound(KeySound.SOLVE)
        }

        // checkable MaterialButton: the widget toggles itself, the listener only follows it — and
        // that is also where the latch's tone comes from, so restoring it stays silent.
        binding.buttonTarget.addOnCheckedChangeListener { _, isChecked ->
            onTargetToggled(isChecked)
        }
    }

    /**
     * Plays the sound a control makes, unless the user turned keypad sound off in the settings.
     *
     * Playback itself is asynchronous (see `SoundEffects`), so this never delays the key press.
     */
    private fun playSound(sound: KeySound) {
        if (!settings.soundEnabled) return
        when (sound) {
            KeySound.TONE -> SoundEffects.playKeypadTone()
            KeySound.SOLVE -> SoundEffects.playSolveScale()
            KeySound.RESET -> SoundEffects.playResetScale()
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
        when {
            savedInstanceState != null -> restoreInput(savedInstanceState)
            // Only the *first* time this screen exists does the configured default get applied.
            // Visiting the settings screen destroys this fragment's view and rebuilds it when the
            // user comes back — with a null savedInstanceState — and re-reading the default there
            // was the bug that made changing the default overwrite the target in use.
            !targetInitialised -> {
                target = settings.defaultTarget
                targetInitialised = true
            }
        }
        refreshDisplay()
        offerHelpOnFirstStart(savedInstanceState)
    }

    /**
     * Opens the help page by itself on the very first start, and only then.
     *
     * [savedInstanceState] is part of the condition because this screen is also rebuilt for a
     * rotation: the flag alone would usually be enough (it is stored before the page opens), but a
     * restore that happened in between must never push a second copy of the page on top.
     *
     * The navigation is posted instead of called here: this runs inside the fragment's own view
     * creation, and pushing a destination from there races with the transaction that is still being
     * applied.
     */
    private fun offerHelpOnFirstStart(savedInstanceState: Bundle?) {
        if (savedInstanceState != null || settings.helpShown) return

        settings.helpShown = true
        binding.root.post {
            // the fragment may be gone by the time the post runs (a fast back press, a test teardown)
            if (isAdded && view != null) {
                runCatching { findNavController().navigate(R.id.HelpFragment) }
            }
        }
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
        targetInitialised = true
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

        /** The target bound, shared with the settings screen that configures the default. */
        const val MAX_ABS_TARGET = Settings.MAX_ABS_TARGET

        /** What a fresh screen starts on until the user configures something else. */
        const val DEFAULT_TARGET = Settings.DEFAULT_TARGET

        /** Longest digit string a buffer accepts. */
        private const val MAX_DIGITS = 6


    }
}

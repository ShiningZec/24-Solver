package com.a24solver.wakusei

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.a24solver.wakusei.databinding.FragmentFirstBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Input screen.
 *
 * Digits fill the "current input" buffer, NEXT commits it as a candidate number (or as the
 * target while in target mode), SOLVE validates the input and runs [SyntaxTree.solveIt] on a
 * background dispatcher.
 *
 * Results are deliberately NOT rendered yet: a finished solve only rewrites the target label
 * to "SOLVE COMPLETE" (see [showSolveComplete]). The answers are kept in [lastAnswers] for
 * the future result screen.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    /** What the digit keys are currently editing. */
    private enum class InputMode { NUMBER, TARGET }

    /** What the target label currently shows. */
    private enum class TargetLabel { TARGET, SOLVING, COMPLETE }

    private val numbers = mutableListOf<Int>()
    private var currentInput = ""
    private var mode = InputMode.NUMBER
    private var target = DEFAULT_TARGET
    private var targetLabel = TargetLabel.TARGET
    private var solveJob: Job? = null

    /** Answers of the last finished solve. Not displayed yet. */
    private var lastAnswers: Set<String> = emptySet()

    // --------------------------------------------------------------- input

    private fun onDigitPressed(digit: Int) {
        if (currentInput.length >= INPUT_MAX_LENGTH) return
        // keep the buffer free of leading zeros ("0" then "5" means 5, not 05)
        currentInput = if (currentInput == "0") digit.toString() else currentInput + digit
        onInputChanged()
    }

    private fun onDeletePressed() {
        if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
        onInputChanged()
    }

    private fun onClearPressed() {
        if (currentInput.isNotEmpty()) {
            // First press clears the digits being typed ...
            currentInput = ""
        } else if (mode == InputMode.NUMBER && numbers.isNotEmpty()) {
            // ... with an empty buffer it undoes the last committed number.
            val removed = numbers.removeAt(numbers.lastIndex)
            toast(getString(R.string.msg_number_removed, removed))
        }
        onInputChanged()
    }

    private fun onNextPressed() {
        when (mode) {
            InputMode.NUMBER -> commitNumber()
            InputMode.TARGET -> commitTarget()
        }
    }

    private fun onTargetPressed() {
        when (mode) {
            InputMode.NUMBER -> {
                mode = InputMode.TARGET
                currentInput = target.toString() // start from the value currently in use
                toast(getString(R.string.hint_target_mode))
            }

            InputMode.TARGET -> {
                mode = InputMode.NUMBER         // pressing TARGET again cancels the edit
                currentInput = ""
            }
        }
        onInputChanged()
    }

    /** Any edit brings the plain target label back on screen. */
    private fun onInputChanged() {
        targetLabel = TargetLabel.TARGET
        refreshDisplay()
    }

    // ------------------------------------------------------------- commits

    private fun commitNumber() {
        if (currentInput.isEmpty()) {
            toast(getString(R.string.err_empty_input))
            return
        }
        val value = currentInput.toIntOrNull()
        if (value == null || abs(value) > MAX_ABS_NUMBER) {
            toast(getString(R.string.err_number_range, MAX_ABS_NUMBER))
            return
        }
        if (numbers.size >= MAX_NUMBERS) {
            toast(getString(R.string.err_max_numbers, MAX_NUMBERS))
            return
        }
        numbers.add(value)
        currentInput = ""
        onInputChanged()
    }

    private fun commitTarget() {
        if (currentInput.isEmpty()) {
            toast(getString(R.string.err_empty_input))
            return
        }
        val value = currentInput.toIntOrNull()
        if (value == null || abs(value) > MAX_ABS_TARGET) {
            toast(getString(R.string.err_target_range, MAX_ABS_TARGET))
            return
        }
        target = value
        mode = InputMode.NUMBER
        currentInput = ""
        onInputChanged()
    }

    // --------------------------------------------------------------- solve

    private fun onSolvePressed() {
        if (solveJob?.isActive == true) return

        // Whatever is still being typed counts as input: commit it first, so the value being
        // validated is exactly what the user sees on screen.
        if (mode == InputMode.NUMBER && currentInput.isNotEmpty()) {
            commitNumber()
            if (currentInput.isNotEmpty()) return // the commit was refused, message shown
        }

        validate()?.let { message ->
            toast(message)
            return
        }

        val candidates: Array<Int> = numbers.toTypedArray()
        val targetSnapshot = target

        targetLabel = TargetLabel.SOLVING
        refreshDisplay()
        binding.buttonSolve.isEnabled = false

        solveJob = viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching { SyntaxTree.solveIt(targetSnapshot, candidates) }
            }

            val view = _binding ?: return@launch
            view.buttonSolve.isEnabled = true

            outcome
                .onSuccess { answers ->
                    lastAnswers = answers
                    Log.d(
                        TAG,
                        "solveIt(target=$targetSnapshot, numbers=${candidates.toList()}) " +
                            "-> ${answers.size} answer(s)"
                    )
                    showSolveComplete()
                }
                .onFailure { error ->
                    targetLabel = TargetLabel.TARGET
                    refreshDisplay()
                    Log.w(TAG, "solveIt rejected the input", error)
                    toast(
                        getString(
                            R.string.err_solver,
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
        }
    }

    /**
     * Input validation performed before [SyntaxTree.solveIt] is called: at most
     * [MAX_NUMBERS] candidates, `abs(candidate) <= MAX_ABS_NUMBER` and
     * `abs(target) <= MAX_ABS_TARGET`. Returns an error message, or null when input is valid.
     */
    private fun validate(): String? {
        if (numbers.isEmpty()) return getString(R.string.err_no_numbers)
        if (numbers.size > MAX_NUMBERS) return getString(R.string.err_max_numbers, MAX_NUMBERS)
        if (numbers.any { abs(it) > MAX_ABS_NUMBER }) {
            return getString(R.string.err_number_range, MAX_ABS_NUMBER)
        }
        if (abs(target) > MAX_ABS_TARGET) {
            return getString(R.string.err_target_range, MAX_ABS_TARGET)
        }
        return null
    }

    private fun showSolveComplete() {
        // TODO(result screen): render lastAnswers (count + list) on the SecondFragment page.
        targetLabel = TargetLabel.COMPLETE
        refreshDisplay()
    }

    // ------------------------------------------------------------- display

    private fun refreshDisplay() {
        val view = _binding ?: return

        view.textTarget.text = when (targetLabel) {
            TargetLabel.TARGET -> getString(R.string.target_label, target)
            TargetLabel.SOLVING -> getString(R.string.solving)
            TargetLabel.COMPLETE -> getString(R.string.solve_complete)
        }

        view.textNumbers.text = getString(
            R.string.numbers_label,
            if (numbers.isEmpty()) getString(R.string.numbers_empty)
            else numbers.joinToString(", ")
        )

        val typed = currentInput.ifEmpty { getString(R.string.input_empty) }
        view.textCurrentInput.text = when (mode) {
            InputMode.NUMBER -> getString(R.string.current_input_label, typed)
            InputMode.TARGET -> getString(R.string.target_input_label, typed)
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

        binding.buttonNext.setOnClickListener { onNextPressed() }
        binding.buttonTarget.setOnClickListener { onTargetPressed() }
        binding.buttonSolve.setOnClickListener { onSolvePressed() }
        binding.buttonDel.setOnClickListener { onDeletePressed() }
        binding.buttonClear.setOnClickListener { onClearPressed() }
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
        refreshDisplay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        solveJob?.cancel()
        _binding = null
    }

    companion object {
        private const val TAG = "FirstFragment"

        /** Input limits enforced before the solver is called. */
        const val MAX_NUMBERS = 5
        const val MAX_ABS_NUMBER = 256
        const val MAX_ABS_TARGET = 16384

        const val DEFAULT_TARGET = 24

        /** Longest digit string the input buffer accepts. */
        private const val INPUT_MAX_LENGTH = 6
    }
}

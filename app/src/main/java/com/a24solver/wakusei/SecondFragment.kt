package com.a24solver.wakusei

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.a24solver.wakusei.databinding.FragmentSecondBinding

/**
 * Result screen: the solutions of the last solve at the top, a BACK button at the bottom that
 * returns to the input screen.
 *
 * When the solver found expressions that differ only by the commutativity/associativity of `+` and
 * `*`, a two-way switch lets the user look at either every expression or one representative per
 * such group. The choice is kept in [SolveResultViewModel], so it survives a rotation and coming
 * back from the input screen.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val resultViewModel: SolveResultViewModel by activityViewModels()

    /** Guards against the programmatic check below re-entering the toggle listener. */
    private var syncingToggle = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonBack.setOnClickListener { goBack() }
        binding.toggleSolutionSet.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingToggle) return@addOnButtonCheckedListener
            resultViewModel.select(
                if (checkedId == R.id.button_show_essential) SolutionSet.ESSENTIAL
                else SolutionSet.ALL
            )
            renderAnswers()
        }
        renderAnswers()
    }

    private fun renderAnswers() {
        val result = resultViewModel.result
        if (result == null) { // opened without a solve, e.g. after a process restart
            binding.textResultHeader.text = getString(R.string.result_empty)
            binding.textAnswers.text = ""
            binding.toggleSolutionSet.visibility = View.GONE
            return
        }

        val solutions = result.shownSolutions
        binding.textResultHeader.text = resources.getQuantityString(
            if (result.effectiveSet == SolutionSet.ESSENTIAL) R.plurals.result_header_essential
            else R.plurals.result_header,
            solutions.size,
            solutions.size,
            result.target,
            result.numbers.joinToString(", ")
        )

        binding.textAnswers.text = if (solutions.isEmpty()) {
            getString(R.string.result_empty)
        } else {
            solutions
                .mapIndexed { index, answer -> getString(R.string.result_line, index + 1, answer) }
                .joinToString("\n")
        }

        renderSolutionSetToggle(result)
    }

    private fun renderSolutionSetToggle(result: SolveResult) {
        // With a single solution, or with none, both sets are the same list: the switch would be
        // a control that cannot change anything, so it is not shown at all.
        binding.toggleSolutionSet.visibility = if (result.hasChoice) View.VISIBLE else View.GONE

        binding.buttonShowAll.text = getString(R.string.result_toggle_all, result.all.size)
        binding.buttonShowEssential.text =
            getString(R.string.result_toggle_essential, result.essential.size)

        syncingToggle = true
        binding.toggleSolutionSet.check(
            if (result.effectiveSet == SolutionSet.ESSENTIAL) R.id.button_show_essential
            else R.id.button_show_all
        )
        syncingToggle = false
    }

    private fun goBack() {
        if (!isAdded) return
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

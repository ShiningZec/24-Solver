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
 * Result screen: the answers of the last solve at the top, a BACK button at the bottom that
 * returns to the input screen.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val resultViewModel: SolveResultViewModel by activityViewModels()

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
        renderAnswers()
    }

    private fun renderAnswers() {
        val result = resultViewModel.result
        if (result == null) { // opened without a solve, e.g. after a process restart
            binding.textResultHeader.text = getString(R.string.result_empty)
            binding.textAnswers.text = ""
            return
        }

        binding.textResultHeader.text = getString(
            R.string.result_header,
            result.answers.size,
            result.target,
            result.numbers.joinToString(", ")
        )

        binding.textAnswers.text = if (result.answers.isEmpty()) {
            getString(R.string.result_empty)
        } else {
            result.answers
                .mapIndexed { index, answer -> getString(R.string.result_line, index + 1, answer) }
                .joinToString("\n")
        }
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

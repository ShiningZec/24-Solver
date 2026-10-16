package com.a24solver.wakusei

import androidx.lifecycle.ViewModel
import com.a24solver.wakusei.solver.SolveOutcome

/** Which of the two solution sets the result screen is showing. */
enum class SolutionSet { ALL, ESSENTIAL }

/**
 * The solutions of one solve run plus the display choice, shared between the input screen and the
 * result screen.
 *
 * The two lists come from the solver in one pass: [all] is every distinct expression, [essential]
 * one representative per class of expressions that differ only by the commutativity/associativity
 * of `+` and `*`. Keeping both means switching the result screen between them is instant and the
 * choice survives a configuration change, because it lives here rather than in the fragment.
 */
data class SolveResult(
    val target: Int,
    val numbers: List<Int>,
    val all: List<String>,
    val essential: List<String>,
    val selected: SolutionSet = SolutionSet.ALL
) {
    /** False when both sets are identical, i.e. when there is nothing to switch between. */
    val hasChoice: Boolean get() = essential.size != all.size

    /** The selection actually in force: without a choice, the full list is always the right one. */
    val effectiveSet: SolutionSet get() = if (hasChoice) selected else SolutionSet.ALL

    val shownSolutions: List<String>
        get() = if (effectiveSet == SolutionSet.ESSENTIAL) essential else all
}

/**
 * Activity-scoped holder for the last [SolveResult].
 *
 * The answer set can be large (hundreds of strings for five numbers), so it lives in a ViewModel
 * instead of navigation arguments: arguments end up in the saved instance state, which has to stay
 * small.
 */
class SolveResultViewModel : ViewModel() {

    var result: SolveResult? = null
        private set

    fun setResult(value: SolveResult) {
        result = value
    }

    /** Switches the result screen between the full and the essentially-different solution set. */
    fun select(set: SolutionSet) {
        result = result?.copy(selected = set)
    }

    /** Convenience for callers that hold solver output rather than a [SolveResult]. */
    fun setResult(outcome: SolveOutcome) {
        setResult(
            SolveResult(
                target = outcome.target,
                numbers = outcome.numbers,
                all = outcome.allSolutions,
                essential = outcome.essentialSolutions
            )
        )
    }
}

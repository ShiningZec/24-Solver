package com.a24solver.wakusei

import androidx.lifecycle.ViewModel

/** Result of one solve run, shared between the input screen and the result screen. */
data class SolveResult(
    val target: Int,
    val numbers: List<Int>,
    val answers: List<String>
)

/**
 * Activity-scoped holder for the last [SolveResult].
 *
 * The answer set can be large (hundreds to thousands of strings with 5 numbers), so it lives in
 * a ViewModel instead of navigation arguments: arguments end up in the saved instance state,
 * which has to stay small.
 */
class SolveResultViewModel : ViewModel() {

    var result: SolveResult? = null
        private set

    fun setResult(value: SolveResult) {
        result = value
    }
}

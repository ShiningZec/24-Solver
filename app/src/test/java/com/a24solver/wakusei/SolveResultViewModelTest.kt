package com.a24solver.wakusei

import com.a24solver.wakusei.solver.SolveOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The display rules of the result screen: which list is shown, and when the switch is offered. */
class SolveResultViewModelTest {

    private fun result(
        all: List<String> = listOf("1 + 2 + 3 + 4", "4 * 3 * 2 * 1"),
        essential: List<String> = listOf("1 + 2 + 3 + 4"),
        selected: SolutionSet = SolutionSet.ALL
    ) = SolveResult(target = 10, numbers = listOf(1, 2, 3, 4), all = all, essential = essential, selected = selected)

    @Test
    fun showsTheFullListByDefault() {
        val subject = result()
        assertTrue(subject.hasChoice)
        assertEquals(SolutionSet.ALL, subject.effectiveSet)
        assertEquals(subject.all, subject.shownSolutions)
    }

    @Test
    fun showsTheEssentialListWhenSelected() {
        val subject = result(selected = SolutionSet.ESSENTIAL)
        assertEquals(SolutionSet.ESSENTIAL, subject.effectiveSet)
        assertEquals(subject.essential, subject.shownSolutions)
    }

    @Test
    fun fallsBackToTheFullListWhenBothSetsAreIdentical() {
        // e.g. a single solution, or none: selecting "essential" would change nothing, which is
        // exactly why the screen hides the switch — and why the wording follows the full list
        val subject = result(
            all = listOf("8 / (3 - 8 / 3)"),
            essential = listOf("8 / (3 - 8 / 3)"),
            selected = SolutionSet.ESSENTIAL
        )
        assertFalse(subject.hasChoice)
        assertEquals(SolutionSet.ALL, subject.effectiveSet)
        assertEquals(subject.all, subject.shownSolutions)
    }

    @Test
    fun theViewModelKeepsTheSelectionAndTheSolutions() {
        val viewModel = SolveResultViewModel()
        viewModel.setResult(
            SolveOutcome(
                target = 24,
                numbers = listOf(1, 2, 3, 4),
                allSolutions = listOf("a", "b"),
                essentialSolutions = listOf("a")
            )
        )

        assertEquals(24, viewModel.result?.target)
        assertEquals(listOf(1, 2, 3, 4), viewModel.result?.numbers)
        assertEquals(SolutionSet.ALL, viewModel.result?.effectiveSet)

        viewModel.select(SolutionSet.ESSENTIAL)
        assertEquals(SolutionSet.ESSENTIAL, viewModel.result?.effectiveSet)
        assertEquals(listOf("a"), viewModel.result?.shownSolutions)

        // selecting before any solve must not invent a result
        val empty = SolveResultViewModel()
        empty.select(SolutionSet.ESSENTIAL)
        assertEquals(null, empty.result)
    }
}

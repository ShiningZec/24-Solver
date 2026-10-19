package com.a24solver.wakusei.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SolverTest {

    // ------------------------------------------------------- known-good answers

    @Test
    fun findsTheClassicTwentyFour() {
        val outcome = Solver.solve(24, listOf(1, 2, 3, 4))

        assertTrue(outcome.hasSolutions)
        assertTrue("(1 + 2 + 3) * 4" in outcome.allSolutions)
        assertTrue("1 * 2 * 3 * 4" in outcome.allSolutions)
    }

    @Test
    fun findsAnswersThatNeedFractionalIntermediateResults() {
        // the only solution of 3,3,8,8 is 8 / (3 - 8/3) — impossible to find in floating point
        val outcome = Solver.solve(24, listOf(3, 3, 8, 8))
        assertEquals(listOf("8 / (3 - 8 / 3)"), outcome.allSolutions)
        assertEquals(listOf("8 / (3 - 8 / 3)"), outcome.essentialSolutions)

        // 60 from 1,1,9,9,6 needs (1 + 1/9) * 9 * 6
        val sixty = Solver.solve(60, listOf(1, 1, 9, 9, 6))
        assertTrue(sixty.allSolutions.any { it.contains("1 / 9") })
        assertEquals(Rational.of(60), ExpressionParser.evaluate(sixty.allSolutions.first()))
    }

    /**
     * Solution counts for inputs whose expected values were produced by an independent Python/Node
     * re-implementation of the search, not by this code (see the README's appendix).
     */
    @Test
    fun reportsTheIndependentlyVerifiedSolutionCounts() {
        val cases = listOf(
            // target, numbers, every expression, essentially different
            Case(24, listOf(1, 2, 3, 4), 242, 28),
            Case(24, listOf(3, 3, 8, 8), 1, 1),
            Case(24, listOf(5, 5, 5, 1), 2, 1),
            Case(24, listOf(23, 23, 23, 23), 2, 1),
            Case(24, listOf(6, 6, 6, 6), 7, 3),
            Case(24, listOf(8, 3, 8, 3), 1, 1),
            Case(24, listOf(1, 1, 1, 1), 0, 0),
            Case(24, listOf(7, 7, 7, 7), 0, 0),
            Case(0, listOf(1, 1, 1, 1), 66, 45),
            Case(60, listOf(1, 1, 9, 9, 6), 26, 2),
            Case(10, listOf(-5, 2, 3), 8, 5),
            Case(-6, listOf(1, 2, 3), 0, 0)
        )

        for (case in cases) {
            val outcome = Solver.solve(case.target, case.numbers)
            assertEquals(
                "every expression for ${case.numbers} -> ${case.target}",
                case.allSolutions,
                outcome.allSolutions.size
            )
            assertEquals(
                "essentially different solutions for ${case.numbers} -> ${case.target}",
                case.essentialSolutions,
                outcome.essentialSolutions.size
            )
        }
    }

    // ------------------------------------------------------- every answer is real

    @Test
    fun everyAnswerIsAnExactSolutionThatUsesEachNumberExactlyOnce() {
        val inputs = listOf(
            24 to listOf(1, 2, 3, 4),
            24 to listOf(1, 3, 4, 6),
            60 to listOf(1, 1, 9, 9, 6),
            24 to listOf(5, 5, 5, 1),
            0 to listOf(1, 1, 1, 1),
            36 to listOf(-6, -6, 6, 6),
            24 to listOf(3, 3, 8, 8)
        )

        for ((target, numbers) in inputs) {
            val outcome = Solver.solve(target, numbers)
            assertTrue("no answers at all for $numbers -> $target", outcome.hasSolutions)
            for (answer in outcome.allSolutions) {
                val parsed = ExpressionParser.parse(answer)
                assertEquals("$answer does not equal $target", Rational.of(target.toLong()), parsed.evaluate())
                assertEquals(
                    "$answer does not use $numbers exactly once",
                    numbers.sorted(),
                    parsed.numbers().sorted()
                )
            }
        }
    }

    @Test
    fun answersAreUniqueAndOrderedSimplestFirst() {
        val outcome = Solver.solve(24, listOf(1, 2, 3, 4))

        assertEquals(outcome.allSolutions.size, outcome.allSolutions.toSet().size)
        assertEquals(outcome.essentialSolutions.size, outcome.essentialSolutions.toSet().size)

        val complexity = outcome.allSolutions.map { it.length }
        assertEquals(complexity.sorted(), complexity)
    }

    @Test
    fun essentialSolutionsAreOnePerCanonicalClass() {
        val outcome = Solver.solve(24, listOf(1, 2, 3, 4))

        // every essential answer is one of the answers ...
        assertTrue(outcome.allSolutions.containsAll(outcome.essentialSolutions))

        // ... the classes are disjoint and cover every answer ...
        val classes = outcome.allSolutions.groupBy { ExpressionParser.parse(it).canonicalKey() }
        assertEquals(classes.size, outcome.essentialSolutions.size)
        assertEquals(
            classes.keys,
            outcome.essentialSolutions.map { ExpressionParser.parse(it).canonicalKey() }.toSet()
        )

        // ... and each representative really is the simplest member of its class
        for ((_, members) in classes) {
            val representative = members.first { it in outcome.essentialSolutions }
            assertEquals(members.minOf { it.length }, representative.length)
        }
    }

    // ------------------------------------------------------------ differential

    @Test(timeout = 120_000)
    fun agreesWithTheBruteForceOracleOnRandomSmallInputs() {
        val random = Random(20261016)
        repeat(60) {
            val numbers = List(random.nextInt(2, 5)) { random.nextInt(-12, 13) }
            val target = random.nextInt(-30, 31)

            val expected = BruteForceOracle.solve(target, numbers)
            val actual = Solver.solve(target, numbers)

            assertEquals("numbers=$numbers target=$target", expected, actual.allSolutions.toSet())
            assertEquals(expected.size, actual.allSolutions.size)
            assertEquals(
                "essential count for numbers=$numbers target=$target",
                expected.map { ExpressionParser.parse(it).canonicalKey() }.toSet().size,
                actual.essentialSolutions.size
            )
        }
    }

    @Test(timeout = 300_000)
    fun agreesWithTheBruteForceOracleOnFiveNumbers() {
        // 5! x 4^4 x Catalan(4) = 430,080 full evaluations per case, twice over
        for (numbers in listOf(listOf(1, 2, 3, 4, 5), listOf(3, 3, 8, 8, 1))) {
            for (target in listOf(24, 30)) {
                val expected = BruteForceOracle.solve(target, numbers)
                val actual = Solver.solve(target, numbers)
                assertEquals("numbers=$numbers target=$target", expected, actual.allSolutions.toSet())
            }
        }
    }

    // ---------------------------------------------------------------- limits

    @Test
    fun rejectsInputOutsideItsContract() {
        assertThrows(IllegalArgumentException::class.java) { Solver.solve(24, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            Solver.solve(24, List(9) { 1 })
        }
        assertThrows(IllegalArgumentException::class.java) { Solver.solve(24, listOf(1000)) }
        assertThrows(IllegalArgumentException::class.java) { Solver.solve(24, listOf(-1000)) }
        // the bounds themselves are inside the contract
        assertTrue(Solver.solve(999, listOf(999)).hasSolutions)
        assertTrue(Solver.solve(-999, listOf(-999)).hasSolutions)
    }

    @Test
    fun refusesToEnumerateRatherThanExhaustingMemory() {
        val exception = assertThrows(SolverLimitException::class.java) {
            Solver.solve(24, listOf(1, 2, 3, 4, 5), expressionBudget = 64)
        }
        assertTrue(
            "the message should explain the budget, was: ${exception.message}",
            exception.message!!.contains("64")
        )
        // the same input is fine with a realistic budget
        assertTrue(Solver.solve(24, listOf(1, 2, 3, 4, 5)).allSolutions.isNotEmpty())
    }

    @Test(timeout = 60_000)
    fun theBudgetIsAboveEveryInputTheInputScreenAccepts() {
        // The worst five-number input — five distinct values, so no rendering collides — stores one
        // entry per distinct expression over all masks:
        //   sum over k of C(5,k) * Catalan(k-1) * 4^(k-1) * k!  =  470,485
        val worstCase = (1..5).sumOf { k ->
            binomial(5, k) * catalan(k - 1) * power(4, k - 1) * factorial(k)
        }
        assertEquals(470_485, worstCase)
        assertTrue(
            "the budget must cover the worst five-number input ($worstCase)",
            Solver.MAX_STORED_EXPRESSIONS > worstCase
        )

        // and such an input really does complete, on the real budget, with an answer
        val outcome = Solver.solve(24, listOf(13, 17, 19, 23, 29))
        assertTrue(outcome.allSolutions.isNotEmpty())

        // six numbers are out of reach and say so — no OutOfMemoryError, no silent hang
        assertThrows(SolverLimitException::class.java) {
            Solver.solve(24, listOf(1, 2, 3, 4, 5, 6))
        }
        assertThrows(SolverLimitException::class.java) {
            Solver.solve(24, listOf(1, 2, 3, 4, 5, 6, 7, 8))
        }
    }

    private fun factorial(n: Int): Int = (2..n).fold(1) { acc, i -> acc * i }

    private fun power(base: Int, exponent: Int): Int =
        (1..exponent).fold(1) { acc, _ -> acc * base }

    private fun binomial(n: Int, k: Int): Int = factorial(n) / (factorial(k) * factorial(n - k))

    private fun catalan(n: Int): Int = binomial(2 * n, n) / (n + 1)

    // -------------------------------------------------------------- edge cases

    @Test
    fun handlesASingleNumberAndOneOperator() {
        assertEquals(listOf("12"), Solver.solve(12, listOf(12)).allSolutions)
        assertFalse(Solver.solve(13, listOf(12)).hasSolutions)

        assertEquals(listOf("12 + 13", "13 + 12"), Solver.solve(25, listOf(12, 13)).allSolutions)
        // both orders are one equivalence class
        assertEquals(listOf("12 + 13"), Solver.solve(25, listOf(12, 13)).essentialSolutions)
    }

    @Test
    fun handlesNegativeNumbersAndTargets() {
        val outcome = Solver.solve(-6, listOf(-2, 3))
        assertEquals(listOf("(-2) * 3", "3 * (-2)"), outcome.allSolutions)
        assertEquals(listOf("(-2) * 3"), outcome.essentialSolutions)
    }

    @Test
    fun divisionByZeroNeverBecomesASolution() {
        // 1 / 0 is undefined, so nothing built from 1 and 0 can reach 1 by dividing
        val outcome = Solver.solve(1, listOf(1, 0))
        assertTrue(outcome.allSolutions.isNotEmpty())
        for (answer in outcome.allSolutions) {
            assertFalse("division by zero leaked into $answer", answer.contains("/ 0"))
        }
    }

    @Test
    fun duplicateNumbersDoNotProduceDuplicateAnswers() {
        val outcome = Solver.solve(24, listOf(6, 6, 6, 6))
        assertEquals(outcome.allSolutions.size, outcome.allSolutions.toSet().size)
        assertEquals(7, outcome.allSolutions.size)
        // 6 + 6 + 6 + 6 = 24 is one class ...
        assertTrue(outcome.essentialSolutions.contains("6 + 6 + 6 + 6"))
        // ... and 6 * 6 - 6 - 6 = 24 another
        assertTrue(outcome.essentialSolutions.contains("6 * 6 - 6 - 6"))
    }

    private data class Case(
        val target: Int,
        val numbers: List<Int>,
        val allSolutions: Int,
        val essentialSolutions: Int
    )
}

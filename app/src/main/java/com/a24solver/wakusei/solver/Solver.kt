package com.a24solver.wakusei.solver

/**
 * The result of one solve: the target, the numbers it was asked about, and the expressions that
 * reach the target.
 *
 * [allSolutions] lists every distinct expression, [essentialSolutions] keeps one representative per
 * equivalence class under the commutativity and associativity of `+` and `*` (see
 * [Expression.canonicalKey]). Both are ordered simplest-first (fewest characters, then
 * lexicographically), so the most readable answer is the one at the top of the list.
 */
data class SolveOutcome(
    val target: Int,
    val numbers: List<Int>,
    val allSolutions: List<String>,
    val essentialSolutions: List<String>
) {
    val hasSolutions: Boolean get() = allSolutions.isNotEmpty()
}

/**
 * Thrown when enumeration would need more memory than the solver is willing to spend — see
 * [Solver.MAX_STORED_EXPRESSIONS]. It is an [IllegalArgumentException] so that callers can treat it
 * like the other input rejections, but it is a distinct type because the input itself is legal: it
 * is simply too big to enumerate exhaustively.
 */
class SolverLimitException(message: String) : IllegalArgumentException(message)

/**
 * The solver: given a target and a list of numbers, it lists the expressions that use every number
 * exactly once, use only `+ - * /` and reach the target exactly.
 *
 * ### How it works
 *
 * Instead of walking every tree shape × operator assignment × permutation (which re-evaluates the
 * same sub-expression millions of times and wastes a factor of four on an operator slot no tree
 * reads), the solver runs a subset dynamic program over bitmasks of the *positions* of the input:
 *
 * ```
 * values[mask] = { expression : expression uses exactly the numbers in `mask`, once each }
 * ```
 *
 * Each mask is built from every unordered split into two non-empty sub-masks, so every binary tree
 * is constructed exactly once and sub-results are shared between all trees that use them. The
 * search space drops from `Catalan(n-1) · 4^n · n!` to roughly `3^n` split/value combinations.
 *
 * Enumeration is exhaustive: nothing is pruned, so "no solutions" means "there are none", not "the
 * heuristic gave up". Arithmetic is exact [Rational] throughout, so fractions such as
 * `(1 + 1/9) * 9` are neither lost nor invented.
 *
 * ### Limits
 *
 * Enumeration is practical up to five numbers — about 50 ms for four and 250 ms for five on a
 * desktop JVM — which is also the input screen's limit. The DP keeps expressions rather than only
 * values, so its memory grows far faster than the number of solutions: a six-number input already
 * has 30,965,760 distinct expressions in its top mask alone. Rather than thrash for hours or die
 * with an `OutOfMemoryError`, the solver stops once its tables pass [MAX_STORED_EXPRESSIONS] and
 * raises [SolverLimitException]; that happens within about half a second, for six numbers and up.
 */
object Solver {

    /** Largest accepted input size. */
    const val MAX_NUMBERS = 8

    /** Every input number must satisfy `-MAX_ABS_NUMBER < x < MAX_ABS_NUMBER`. */
    const val MAX_ABS_NUMBER = 256

    /**
     * Upper bound on the number of expressions the memo tables may hold, across all masks.
     *
     * This is a memory guard, not a solution cap. It is set just above the worst case the input
     * screen can produce: for five distinct numbers the tables hold every distinct expression over
     * all masks, `Σ C(5,k) · Catalan(k-1) · 4^(k-1) · k! = 470,485` of them, and de-duplication by
     * rendering can only lower that. Every input the UI accepts therefore fits, while a six-number
     * input — whose top mask alone has 30,965,760 expressions — stops quickly and cleanly.
     */
    const val MAX_STORED_EXPRESSIONS = 500_000

    /**
     * Every expression built from [numbers] that uses each number exactly once and evaluates
     * exactly to [target].
     *
     * @throws IllegalArgumentException when the input violates [MAX_NUMBERS] or [MAX_ABS_NUMBER],
     *   or — as [SolverLimitException] — when the search is too large to enumerate exhaustively.
     */
    fun solve(target: Int, numbers: List<Int>): SolveOutcome =
        solve(target, numbers, MAX_STORED_EXPRESSIONS)

    /** [solve] with an explicit memory budget; the budget is a parameter so tests can trip it. */
    internal fun solve(target: Int, numbers: List<Int>, expressionBudget: Int): SolveOutcome {
        require(numbers.size in 1..MAX_NUMBERS) {
            "The solver takes between 1 and $MAX_NUMBERS numbers, but got ${numbers.size}"
        }
        require(numbers.all { it > -MAX_ABS_NUMBER && it < MAX_ABS_NUMBER }) {
            "Every number must satisfy |x| < $MAX_ABS_NUMBER, but got $numbers"
        }

        val size = numbers.size
        val table = buildTables(numbers, expressionBudget)
        val reached = table[(1 shl size) - 1]?.get(Rational.of(target.toLong()))

        return SolveOutcome(
            target = target,
            numbers = numbers,
            allSolutions = reached?.keys?.sortedByComplexity() ?: emptyList(),
            essentialSolutions = essential(reached?.values ?: emptyList())
        )
    }

    /**
     * `values[mask]` for every non-empty mask: the expression trees that use exactly the numbers
     * whose bits are set in `mask`, grouped by exact value and de-duplicated by rendering.
     */
    private fun buildTables(
        numbers: List<Int>,
        expressionBudget: Int
    ): Array<HashMap<Rational, LinkedHashMap<String, Expression>>?> {
        val size = numbers.size
        val table = arrayOfNulls<HashMap<Rational, LinkedHashMap<String, Expression>>>(1 shl size)
        var stored = 0

        fun store(
            bucket: HashMap<Rational, LinkedHashMap<String, Expression>>,
            value: Rational,
            expression: Expression
        ) {
            val byRender = bucket.getOrPut(value) { LinkedHashMap() }
            if (byRender.putIfAbsent(expression.render(), expression) == null) {
                stored++
                if (stored > expressionBudget) {
                    throw SolverLimitException(
                        "Enumerating every expression for ${numbers.size} numbers needs more " +
                            "than $expressionBudget intermediate results, so this input is too " +
                            "large to enumerate exhaustively."
                    )
                }
            }
        }

        for (position in 0 until size) {
            val literal = Expression.Literal(numbers[position])
            val bucket = HashMap<Rational, LinkedHashMap<String, Expression>>()
            bucket[literal.evaluate()] = linkedMapOf(literal.render() to literal)
            stored++
            table[1 shl position] = bucket
        }

        for (mask in 1 until table.size) {
            if (mask.countOneBits() < 2) continue

            val bucket = HashMap<Rational, LinkedHashMap<String, Expression>>()
            var leftMask = (mask - 1) and mask
            while (leftMask > 0) {
                val rightMask = mask xor leftMask
                // each unordered split once — `combine` emits both operand orders, so every
                // expression tree is still produced exactly once overall
                if (leftMask < rightMask) {
                    combine(table[leftMask]!!, table[rightMask]!!) { value, expression ->
                        store(bucket, value, expression)
                    }
                }
                leftMask = (leftMask - 1) and mask
            }
            table[mask] = bucket
        }

        return table
    }

    /**
     * Emits every expression that combines one expression from [left] with one from [right].
     *
     * Both operand orders are produced for all four operators: `a - b` and `b - a` are different
     * solutions, and `a + b` / `b + a` are different *expressions* even though they share a
     * canonical key. Division by zero is dropped here, which is the only place it can arise.
     */
    private fun combine(
        left: Map<Rational, LinkedHashMap<String, Expression>>,
        right: Map<Rational, LinkedHashMap<String, Expression>>,
        emit: (Rational, Expression) -> Unit
    ) {
        for ((leftValue, leftExpressions) in left) {
            for ((rightValue, rightExpressions) in right) {
                for (leftExpression in leftExpressions.values) {
                    for (rightExpression in rightExpressions.values) {
                        for (operator in Operator.entries) {
                            binary(
                                leftExpression, leftValue, operator, rightExpression, rightValue
                            )?.let { (expression, value) -> emit(value, expression) }
                            binary(
                                rightExpression, rightValue, operator, leftExpression, leftValue
                            )?.let { (expression, value) -> emit(value, expression) }
                        }
                    }
                }
            }
        }
    }

    /**
     * One representative per equivalence class, simplest first: the shortest rendering in the
     * class, and the lexicographically smallest one when several are equally short.
     */
    private fun essential(answers: Collection<Expression>): List<String> =
        answers
            .groupBy { it.canonicalKey() }
            .map { (_, equivalents) ->
                equivalents.map { it.render() }.minWith(compareBy({ it.length }, { it }))
            }
            .sortedByComplexity()

    private fun Collection<String>.sortedByComplexity(): List<String> =
        sortedWith(compareBy({ it.length }, { it }))
}

package com.a24solver.wakusei.solver

/**
 * An intentionally naive, self-contained reference implementation of the 24-point search, used to
 * differentially test [Solver].
 *
 * It shares only the value/printing layer ([Rational], [Expression]) with the solver; the search
 * itself is the opposite approach — enumerate every binary tree shape × every operator assignment ×
 * every permutation of the input positions and evaluate each tree from scratch. No memoisation, no
 * pruning, no canonicalisation, so a shared bug in the solver's dynamic program cannot hide here.
 *
 * The tree shapes are generated and consumed without the slot-numbering scheme the old
 * implementation used: a traversal counter picks the next leaf value and the next operator, which
 * keeps this oracle structurally different from what it is checking.
 */
object BruteForceOracle {

    private val operators = Operator.entries

    /** Every distinct rendering of an expression that reaches [target]. */
    fun solve(target: Int, numbers: List<Int>): Set<String> {
        val wanted = Rational.of(target.toLong())
        val arrangements = permutations(numbers)
        val answers = mutableSetOf<String>()
        for (shape in shapes(numbers.size)) {
            for (operatorsUsed in operatorAssignments(numbers.size - 1)) {
                for (permutation in arrangements) {
                    val leaves = permutation.toIntArray()
                    if (evaluate(shape, leaves, operatorsUsed) != wanted) continue
                    answers += render(shape, leaves, operatorsUsed)
                }
            }
        }
        return answers
    }

    // ---- tree shapes ----------------------------------------------------------------------------

    private sealed interface Shape {
        data object Leaf : Shape
        data class Fork(val left: Shape, val right: Shape) : Shape
    }

    private fun shapes(size: Int): Sequence<Shape> = sequence {
        if (size == 1) {
            yield(Shape.Leaf)
            return@sequence
        }
        for (leftSize in 1 until size) {
            for (left in shapes(leftSize)) {
                for (right in shapes(size - leftSize)) {
                    yield(Shape.Fork(left, right))
                }
            }
        }
    }

    /** Walks a shape and hands out the next leaf value / operator, in traversal order. */
    private class Cursor(private val leaves: IntArray, private val operatorsUsed: IntArray) {
        private var leafIndex = 0
        private var operatorIndex = 0

        fun nextLeaf(): Int = leaves[leafIndex++]

        fun nextOperator(): Operator = operators[operatorsUsed[operatorIndex++]]
    }

    private fun evaluate(shape: Shape, leaves: IntArray, operatorsUsed: IntArray): Rational? {
        val cursor = Cursor(leaves, operatorsUsed)
        fun eval(node: Shape): Rational? = when (node) {
            is Shape.Leaf -> Rational.of(cursor.nextLeaf().toLong())
            is Shape.Fork -> {
                val left = eval(node.left)
                val right = eval(node.right)
                if (left == null || right == null) {
                    null
                } else {
                    when (cursor.nextOperator()) {
                        Operator.PLUS -> left + right
                        Operator.MINUS -> left - right
                        Operator.TIMES -> left * right
                        Operator.DIVIDE -> left.divide(right)
                    }
                }
            }
        }
        return eval(shape)
    }

    private fun render(shape: Shape, leaves: IntArray, operatorsUsed: IntArray): String {
        val cursor = Cursor(leaves, operatorsUsed)
        fun build(node: Shape): Expression = when (node) {
            is Shape.Leaf -> Expression.Literal(cursor.nextLeaf())
            is Shape.Fork -> {
                val left = build(node.left)
                val right = build(node.right)
                Expression.Binary(left, right, cursor.nextOperator())
            }
        }
        return build(shape).render()
    }

    // ---- operator assignments and permutations --------------------------------------------------

    /** All `4^(size)` assignments, one operator per internal node (four per node, not four per leaf). */
    private fun operatorAssignments(size: Int): Sequence<IntArray> = sequence {
        val assignment = IntArray(size)
        while (true) {
            yield(assignment.copyOf())
            var index = 0
            while (index < size) {
                assignment[index] = (assignment[index] + 1) % operators.size
                if (assignment[index] == 0) index++ else break
            }
            if (index == size) return@sequence
        }
    }

    /** All `size!` orderings of the input positions (duplicates included on purpose). */
    private fun permutations(numbers: List<Int>): List<List<Int>> {
        val result = ArrayList<List<Int>>()
        fun backtrack(chosen: MutableList<Int>, used: BooleanArray) {
            if (chosen.size == numbers.size) {
                result += chosen.toList()
                return
            }
            for (index in numbers.indices) {
                if (used[index]) continue
                used[index] = true
                chosen += numbers[index]
                backtrack(chosen, used)
                chosen.removeAt(chosen.lastIndex)
                used[index] = false
            }
        }
        backtrack(mutableListOf(), BooleanArray(numbers.size))
        return result
    }
}

package com.a24solver.wakusei.solver

/** The four binary operators the solver knows, with their printing precedence. */
enum class Operator(val symbol: String, val precedence: Int) {
    PLUS("+", 1),
    MINUS("-", 1),
    TIMES("*", 2),
    DIVIDE("/", 2)
}

/**
 * A solution expression: a binary tree whose leaves are the input numbers.
 *
 * Expressions are immutable. [evaluate] uses exact [Rational] arithmetic, so an expression either
 * has one exact value or is undefined (a division by zero somewhere inside) — there is no rounding
 * and therefore no "close enough".
 *
 * Only [Solver] builds these, and it never builds a [Binary] whose [Operator.DIVIDE] divisor is
 * zero, so [evaluate] always succeeds for the trees the solver hands out.
 */
sealed interface Expression {

    fun evaluate(): Rational

    /**
     * The expression as an infix string with the minimum number of parentheses that still parses
     * back to this exact tree: the left operand of an equal-precedence operator needs none, the
     * right one does. `((1 + 2) + 3) * 4` prints as `(1 + 2 + 3) * 4`, `1 + (2 + 3)` keeps its
     * parentheses.
     */
    fun render(): String

    /**
     * A key that is identical for expressions differing only by the commutativity and
     * associativity of `+` and `*`: the operands of a maximal chain of one operator are sorted.
     *
     * `(12 + 13)` and `(13 + 12)` share a key, and so do `((1 + 2) + 3)` and `(1 + (2 + 3))`.
     * Subtraction and division are *not* rewritten (`a - b` is not folded into `a + (-b)`), so the
     * relation never merges two expressions a user would consider genuinely different.
     */
    fun canonicalKey(): String

    /** A leaf: one of the input numbers. */
    data class Literal(val number: Int) : Expression {
        override fun evaluate(): Rational = Rational.of(number.toLong())

        override fun render(): String = number.toString()

        override fun canonicalKey(): String = render()
    }

    /** An interior node: `left operator right`. */
    data class Binary(
        val left: Expression,
        val right: Expression,
        val operator: Operator
    ) : Expression {

        override fun evaluate(): Rational = when (operator) {
            Operator.PLUS -> left.evaluate() + right.evaluate()
            Operator.MINUS -> left.evaluate() - right.evaluate()
            Operator.TIMES -> left.evaluate() * right.evaluate()
            Operator.DIVIDE -> {
                val divisor = right.evaluate()
                left.evaluate().divide(divisor) ?: error("division by zero in ${render()}")
            }
        }

        override fun render(): String =
            "${operand(left, isLeftOperand = true)} ${operator.symbol} " +
                operand(right, isLeftOperand = false)

        override fun canonicalKey(): String {
            if (operator == Operator.PLUS || operator == Operator.TIMES) {
                val operands = flatten(operator).map { it.canonicalKey() }.sorted()
                return operands.joinToString(separator = operator.symbol, prefix = "(", postfix = ")")
            }
            return "(${left.canonicalKey()}${operator.symbol}${right.canonicalKey()})"
        }

        /** Every operand of the maximal chain of [op] this node belongs to, in tree order. */
        private fun flatten(op: Operator): List<Expression> = buildList {
            fun collect(node: Expression) {
                if (node is Binary && node.operator == op) {
                    collect(node.left)
                    collect(node.right)
                } else {
                    add(node)
                }
            }
            collect(this@Binary)
        }

        private fun operand(child: Expression, isLeftOperand: Boolean): String {
            val rendered = child.render()
            return if (needsParentheses(child, isLeftOperand)) "($rendered)" else rendered
        }

        private fun needsParentheses(child: Expression, isLeftOperand: Boolean): Boolean =
            when (child) {
                // a negative literal is printed in parentheses so that "2 - -7" stays readable
                is Literal -> child.number < 0
                is Binary -> child.operator.precedence < operator.precedence ||
                    (!isLeftOperand && child.operator.precedence == operator.precedence)
            }
    }
}

/**
 * `left operator right` together with its exact value, or null when the operation is undefined
 * because [rightValue] is zero.
 *
 * The operand values are passed in because the solver already has them — they are the keys of its
 * memo table — and recomputing them for every candidate tree would dominate the runtime.
 */
internal fun binary(
    left: Expression,
    leftValue: Rational,
    operator: Operator,
    right: Expression,
    rightValue: Rational
): Pair<Expression, Rational>? {
    val value = when (operator) {
        Operator.PLUS -> leftValue + rightValue
        Operator.MINUS -> leftValue - rightValue
        Operator.TIMES -> leftValue * rightValue
        Operator.DIVIDE -> leftValue.divide(rightValue) ?: return null
    }
    return Expression.Binary(left, right, operator) to value
}

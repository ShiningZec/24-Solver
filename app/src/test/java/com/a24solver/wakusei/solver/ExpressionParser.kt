package com.a24solver.wakusei.solver

/**
 * A small recursive-descent parser for the strings [Expression.render] produces, written straight
 * from the usual precedence rules rather than from the printer's code.
 *
 * It exists so the tests can make two independent statements about every answer the solver hands
 * out: the printed text parses back into exactly the tree that was printed, and that tree evaluates
 * to the requested target. A printer bug (a dropped parenthesis that changes the meaning of the
 * expression) therefore fails the test suite even though the solver "found" a solution.
 *
 * ```
 * expression := term (('+' | '-') term)*
 * term       := factor (('*' | '/') factor)*
 * factor     := number | '(' expression ')'
 * number     := '-'? digit+
 * ```
 */
object ExpressionParser {

    fun parse(text: String): Expression {
        val parser = Parser(text)
        val expression = parser.expression()
        parser.expectEnd()
        return expression
    }

    /** Convenience: parse [text] and evaluate it. */
    fun evaluate(text: String): Rational = parse(text).evaluate()

    private class Parser(private val text: String) {
        private var position = 0

        fun expression(): Expression {
            var left = term()
            while (true) {
                left = when (peek()) {
                    '+' -> binary(left, Operator.PLUS) { term() }
                    '-' -> binary(left, Operator.MINUS) { term() }
                    else -> return left
                }
            }
        }

        private fun term(): Expression {
            var left = factor()
            while (true) {
                left = when (peek()) {
                    '*' -> binary(left, Operator.TIMES) { factor() }
                    '/' -> binary(left, Operator.DIVIDE) { factor() }
                    else -> return left
                }
            }
        }

        private fun factor(): Expression {
            skipSpaces()
            if (peek() == '(') {
                position++
                val inner = expression()
                skipSpaces()
                require(peek() == ')') { "missing ')' at $position in \"$text\"" }
                position++
                return inner
            }
            return Expression.Literal(number())
        }

        private fun binary(
            left: Expression,
            operator: Operator,
            right: () -> Expression
        ): Expression {
            position++ // consume the operator that peek() already looked at
            return Expression.Binary(left, right(), operator)
        }

        private fun number(): Int {
            skipSpaces()
            val start = position
            // raw access, not peek(): peek() skips spaces, which would swallow the space that
            // separates this number from the operator that follows it
            if (text.getOrNull(position) == '-') position++
            while (text.getOrNull(position)?.isDigit() == true) position++
            require(position > start) { "expected a number at $start in \"$text\"" }
            return text.substring(start, position).toInt()
        }

        private fun peek(): Char? {
            skipSpaces()
            return text.getOrNull(position)
        }

        fun expectEnd() {
            skipSpaces()
            require(position == text.length) {
                "unexpected trailing input at $position in \"$text\""
            }
        }

        private fun skipSpaces() {
            while (text.getOrNull(position) == ' ') position++
        }
    }
}

/** Every literal of the tree, in tree order — used to check that an answer uses each input once. */
fun Expression.numbers(): List<Int> = when (this) {
    is Expression.Literal -> listOf(number)
    is Expression.Binary -> left.numbers() + right.numbers()
}

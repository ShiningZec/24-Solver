package com.a24solver.wakusei.solver

import com.a24solver.wakusei.solver.Expression.Binary
import com.a24solver.wakusei.solver.Expression.Literal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExpressionTest {

    private fun plus(left: Expression, right: Expression) = Binary(left, right, Operator.PLUS)
    private fun minus(left: Expression, right: Expression) = Binary(left, right, Operator.MINUS)
    private fun times(left: Expression, right: Expression) = Binary(left, right, Operator.TIMES)
    private fun divide(left: Expression, right: Expression) = Binary(left, right, Operator.DIVIDE)

    // ------------------------------------------------------------------ printing

    @Test
    fun printsWithTheMinimumParenthesesThatKeepTheMeaning() {
        // left operands of an equal-precedence operator chain need no parentheses ...
        assertEquals("1 + 2 + 3", plus(plus(Literal(1), Literal(2)), Literal(3)).render())
        assertEquals("1 - 2 - 3", minus(minus(Literal(1), Literal(2)), Literal(3)).render())
        assertEquals("1 * 2 * 3", times(times(Literal(1), Literal(2)), Literal(3)).render())
        assertEquals("6 / 2 / 3", divide(divide(Literal(6), Literal(2)), Literal(3)).render())
        assertEquals("6 / 2 * 3", times(divide(Literal(6), Literal(2)), Literal(3)).render())

        // ... the right one does, because the printer is left-associative
        assertEquals("1 + (2 + 3)", plus(Literal(1), plus(Literal(2), Literal(3))).render())
        assertEquals("1 - (2 - 3)", minus(Literal(1), minus(Literal(2), Literal(3))).render())
        assertEquals("1 * (2 * 3)", times(Literal(1), times(Literal(2), Literal(3))).render())
        assertEquals("6 / (2 * 3)", divide(Literal(6), times(Literal(2), Literal(3))).render())

        // lower precedence inside a higher one always keeps its parentheses
        assertEquals("(1 + 2) * (3 + 4)", times(plus(Literal(1), Literal(2)), plus(Literal(3), Literal(4))).render())
        assertEquals("(1 + 2) * 3", times(plus(Literal(1), Literal(2)), Literal(3)).render())
        assertEquals("1 * (2 + 3)", times(Literal(1), plus(Literal(2), Literal(3))).render())
        assertEquals("1 - (2 + 3)", minus(Literal(1), plus(Literal(2), Literal(3))).render())
        assertEquals("(1 - 2) / 3", divide(minus(Literal(1), Literal(2)), Literal(3)).render())

        // higher precedence inside a lower one needs nothing
        assertEquals("1 + 2 * 3", plus(Literal(1), times(Literal(2), Literal(3))).render())
        assertEquals("2 * 3 + 1", plus(times(Literal(2), Literal(3)), Literal(1)).render())

        // a negative literal is always parenthesised so the sign stays unambiguous
        assertEquals("2 - (-7)", minus(Literal(2), Literal(-7)).render())
        assertEquals("(-7) + 2", plus(Literal(-7), Literal(2)).render())
        assertEquals("2 * (-7)", times(Literal(2), Literal(-7)).render())
        assertEquals("-7", Literal(-7).render())
    }

    @Test
    fun printingSurvivesARoundTripThroughAnIndependentParser() {
        val trees = listOf(
            plus(plus(Literal(1), Literal(2)), Literal(3)),
            plus(Literal(1), plus(Literal(2), Literal(3))),
            times(plus(Literal(1), Literal(2)), plus(Literal(3), Literal(4))),
            minus(Literal(1), minus(Literal(2), Literal(3))),
            divide(Literal(6), times(Literal(2), Literal(3))),
            times(divide(Literal(6), Literal(2)), Literal(3)),
            minus(Literal(2), Literal(-7)),
            divide(Literal(-8), minus(Literal(3), Literal(7))),
            divide(divide(Literal(1), Literal(9)), Literal(1))
        )
        for (tree in trees) {
            assertEquals("round trip of ${tree.render()}", tree, ExpressionParser.parse(tree.render()))
        }
    }

    @Test
    fun printingOfRandomTreesRoundTripsStructurally() {
        val random = Random(20261016)
        repeat(500) {
            val tree = randomTree(random, depth = 4)
            assertEquals(
                "round trip of ${tree.render()}",
                tree,
                ExpressionParser.parse(tree.render())
            )
        }
    }

    private fun randomTree(random: Random, depth: Int): Expression {
        if (depth == 0 || random.nextInt(3) == 0) return Literal(random.nextInt(-12, 13))
        val left = randomTree(random, depth - 1)
        val right = randomTree(random, depth - 1)
        return Binary(left, right, Operator.entries[random.nextInt(Operator.entries.size)])
    }

    // ------------------------------------------------------------ canonical form

    @Test
    fun canonicalKeyFoldsCommutativityAndAssociativity() {
        fun key(expression: Expression) = expression.canonicalKey()

        // commutativity of + and *
        assertEquals(key(plus(Literal(12), Literal(13))), key(plus(Literal(13), Literal(12))))
        assertEquals(key(times(Literal(3), Literal(4))), key(times(Literal(4), Literal(3))))

        // associativity, mixed with commutativity
        assertEquals(
            key(plus(plus(Literal(1), Literal(2)), Literal(3))),
            key(plus(Literal(3), plus(Literal(2), Literal(1))))
        )
        assertEquals(
            key(times(times(Literal(1), Literal(2)), Literal(3))),
            key(times(Literal(3), times(Literal(2), Literal(1))))
        )

        // subtraction and division are not commutative, so they stay apart
        assertNotEquals(key(minus(Literal(3), Literal(4))), key(minus(Literal(4), Literal(3))))
        assertNotEquals(key(divide(Literal(3), Literal(4))), key(divide(Literal(4), Literal(3))))

        // and a different structure is a different key even when the value matches
        assertNotEquals(
            key(times(plus(Literal(1), Literal(2)), Literal(3))),
            key(plus(Literal(1), times(Literal(2), Literal(3))))
        )
        // (1 + 2) * 3 and 3 * (2 + 1) are the same solution written differently
        assertEquals(
            key(times(plus(Literal(1), Literal(2)), Literal(3))),
            key(times(Literal(3), plus(Literal(2), Literal(1))))
        )
    }

    // --------------------------------------------------------------- evaluation

    @Test
    fun evaluationIsExact() {
        // (1 + 1/9) * 9 = 10 exactly, which no double arithmetic would guarantee
        val expression = times(plus(Literal(1), divide(Literal(1), Literal(9))), Literal(9))
        assertEquals(Rational.of(10), expression.evaluate())
        assertEquals("(1 + 1 / 9) * 9", expression.render())

        assertEquals(Rational.of(24), times(plus(plus(Literal(1), Literal(2)), Literal(3)), Literal(4)).evaluate())
        assertEquals(Rational.of(-24), times(Literal(-4), plus(Literal(1), Literal(5))).evaluate())
    }

    @Test
    fun evaluationAgreesWithTheParserForEveryOperator() {
        val random = Random(7)
        var checked = 0
        repeat(300) {
            val tree = randomDefinedTree(random, depth = 3)
            val text = tree.render()
            assertEquals(text, tree.evaluate(), ExpressionParser.evaluate(text))
            checked++
        }
        assertTrue("expected the fuzz loop to run", checked == 300)
    }

    /** A random tree whose value exists, i.e. one that never divides by zero. */
    private fun randomDefinedTree(random: Random, depth: Int): Expression {
        while (true) {
            val tree = randomTree(random, depth)
            if (runCatching { tree.evaluate() }.isSuccess) return tree
        }
    }
}

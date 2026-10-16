package com.a24solver.wakusei.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RationalTest {

    @Test
    fun constructionNormalisesSignAndTerms() {
        assertEquals("-1/3", Rational.of(2, -6).toString())
        assertEquals(Rational.of(-1, 3), Rational.of(2, -6))
        assertEquals("0", Rational.of(0, -5).toString())
        assertEquals(Rational.ZERO, Rational.of(0, -5))
        assertEquals("2", Rational.of(2).toString())
        assertEquals(Rational.ONE, Rational.of(-7, -7))
        assertEquals("-1/3", Rational.of(2, -6).toString())
    }

    @Test
    fun equalValuesShareEqualsAndHashCode() {
        val map = HashMap<Rational, String>()
        map[Rational.of(1, 3)] = "a third"
        // a different route to the same value must find the same entry
        map[Rational.of(5, 15)] = "still a third"
        assertEquals(1, map.size)
        assertEquals("still a third", map[Rational.of(-2, -6)])
        assertEquals(Rational.of(1, 3).hashCode(), Rational.of(-2, -6).hashCode())
    }

    @Test
    fun rejectsZeroDenominator() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Rational.of(1, 0)
        }
        assertEquals("Denominator must be non-zero", exception.message)
    }

    @Test
    fun arithmeticIsExact() {
        assertEquals("7/12", (Rational.of(1, 3) + Rational.of(1, 4)).toString())
        assertEquals("-1/12", (Rational.of(1, 4) - Rational.of(1, 3)).toString())
        assertEquals("1/2", (Rational.of(2, 3) * Rational.of(3, 4)).toString())
        assertEquals("-6", (Rational.of(3, 2) / Rational.of(-1, 4)).toString())
        assertEquals("0", (Rational.of(1, 3) - Rational.of(1, 3)).toString())

        val quotient = requireNotNull(Rational.of(2, 4).divide(Rational.of(3, 4)))
        assertEquals("1", (Rational.of(1, 3) + quotient).toString())
        // a result that no double could represent exactly
        assertEquals("1/3", (Rational.of(1) / Rational.of(3)).toString())
    }

    @Test
    fun divisionByZeroIsNullRatherThanAnException() {
        assertNull(Rational.of(3, 2).divide(Rational.ZERO))
        assertNull(Rational.of(0).divide(Rational.ZERO))
        assertEquals(Rational.ZERO, Rational.of(0).divide(Rational.of(5)))
    }

    @Test
    fun comparisonIsExact() {
        assertTrue(Rational.of(1, 3) < Rational.of(1, 2))
        assertTrue(Rational.of(1, 3) > Rational.of(-1, 2))
        assertTrue(Rational.of(2, 6).compareTo(Rational.of(1, 3)) == 0)
        // values far below double precision are still ordered and still non-zero
        val tiny = Rational.of(1, 1_000_000_000_000_000_000L)
        assertNotEquals(Rational.ZERO, tiny)
        assertTrue(tiny > Rational.ZERO)
    }

    @Test
    fun arithmeticDoesNotWrapAroundWhereLongWould() {
        // 255^8 = 17878103347812890625 > Long.MAX_VALUE (9223372036854775807): the same product in
        // Long arithmetic wraps to -568640725896660991, which would silently invent or hide
        // solutions at the top of the solver's input range.
        val product = (1..8).fold(Rational.ONE) { accumulator, _ -> accumulator * Rational.of(255) }
        assertEquals("17878103347812890625", product.toString())
        assertNotEquals(Rational.of(-568640725896660991L), product)

        var wrapped = 1L
        repeat(8) { wrapped *= 255L }
        assertEquals(-568640725896660991L, wrapped)
    }

    @Test
    fun unaryOperatorsKeepTheNormalForm() {
        assertEquals("-2/3", (-Rational.of(2, 3)).toString())
        assertEquals(Rational.ZERO, -Rational.ZERO)
    }
}

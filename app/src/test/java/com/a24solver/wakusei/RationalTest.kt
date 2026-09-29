package com.a24solver.wakusei

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RationalTest {

    @Test
    fun testConstruct() {
        val r1 = Rational(2, -6)
        assertEquals("-1/3", r1.toString())

        val r2 = Rational(0, -5)
        assertEquals("0", r2.toString())

        val r3 = Rational(2)
        assertEquals("2", r3.toString())

        val exception = assertThrows(
            IllegalArgumentException::class.java,
            {
                Rational(1,0)
            }
        )
        assertEquals("Denominator must be non-zero", exception.message)
    }

    @Test
    fun testAdd() {
        val r1 = Rational(1, 3)
        val r2 = Rational(1, 4)
        assertEquals("7/12", r1.add(r2).toString())
    }

    @Test
    fun testSubtract() {
        val r1 = Rational(1, 4)
        val r2 = Rational(1, 3)
        assertEquals("-1/12", r1.subtract(r2).toString())
    }

    @Test
    fun testMultiply() {
        val r1 = Rational(2, 3)
        val r2 = Rational(3, 4)
        assertEquals("1/2", r1.multiply(r2).toString())
    }

    @Test
    fun testDivide() {
        val r1 = Rational(3, 2)
        val r2 = Rational(-1, 4)
        assertEquals("-6", r1.divide(r2)!!.toString())

        val r3 = Rational(0, 1)
        assertEquals(null, r1.divide(r3))
    }

    @Test
    fun testAllComputes() {
        val r1 = Rational(1,3)
        val r2 = Rational(2,4)
        val r3 = Rational(3,4)
        assertEquals("1", (r1 + (r2 / r3)!!).toString())
    }

    @Test
    fun testCompares() {
        val r1 = Rational(1, 3)
        val r2 = Rational(1,2)
        val r3 = Rational(-1,2)
        val r4 = -r3

        assertEquals("true", (r1 < r2).toString())
        assertEquals("true", (r1 > r3).toString())
        assertEquals("true", (r2 == r4).toString())
    }
}
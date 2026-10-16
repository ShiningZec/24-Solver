package com.a24solver.wakusei.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The limiter that lets notes overlap without the sum going off the scale.
 *
 * The property that matters is the *identity* below the knee: a single note is never touched, so
 * making notes overlap cannot make a lone keypad tone sound different or quieter.
 */
class SoftLimiterTest {

    @Test
    fun everythingBelowTheKneePassesThroughUntouched() {
        for (value in listOf(0.0, 0.1, -0.1, 0.5, -0.55, SoftLimiter.KNEE)) {
            assertEquals(value, SoftLimiter.limit(value), 1e-12)
        }
    }

    @Test
    fun theCurveIsContinuousAtTheKnee() {
        val justAbove = SoftLimiter.limit(SoftLimiter.KNEE + 1e-9)
        assertEquals(SoftLimiter.KNEE, justAbove, 1e-6)
    }

    @Test
    fun nothingReachesFullScale() {
        for (value in listOf(0.8, 1.0, 2.0, 5.0, 100.0)) {
            assertTrue("$value was not contained", SoftLimiter.limit(value) <= 1.0)
            assertTrue("-$value was not contained", SoftLimiter.limit(-value) >= -1.0)
            // what actually matters: the result still fits in a 16-bit sample
            assertTrue(
                "$value does not fit in a sample",
                (SoftLimiter.limit(value) * Short.MAX_VALUE).toInt() <= Short.MAX_VALUE
            )
        }
    }

    @Test
    fun aSumInsideTheKneeComesBackBitForBit() {
        // a lone keypad tone goes through this path
        for (sum in listOf(0, 1, -1, 1000, -1000, SoftLimiter.KNEE_SAMPLE, -SoftLimiter.KNEE_SAMPLE)) {
            assertEquals("sum $sum", sum.toShort(), SoftLimiter.limitSample(sum))
        }
    }

    @Test
    fun aSumAboveTheKneeIsBentIntoRange() {
        val loud = SoftLimiter.limitSample(5 * 18_000)
        assertTrue("the sum was not contained: $loud", loud <= Short.MAX_VALUE)
        assertTrue("and it should still be loud, was $loud", loud > Short.MAX_VALUE / 2)
        assertTrue(SoftLimiter.limitSample(-5 * 18_000) >= -Short.MAX_VALUE)
    }

    @Test
    fun theCurveKeepsGoingUp() {
        var previous = SoftLimiter.limit(SoftLimiter.KNEE)
        for (value in listOf(0.75, 0.9, 1.2, 2.0, 4.0, 8.0)) {
            val limited = SoftLimiter.limit(value)
            assertTrue("$value did not raise the output", limited > previous)
            previous = limited
        }
    }

    @Test
    fun itIsOddSymmetric() {
        for (value in listOf(0.3, 0.9, 3.0)) {
            assertEquals(-SoftLimiter.limit(value), SoftLimiter.limit(-value), 1e-12)
        }
    }

    @Test
    fun fiveOverlappingNotesStillFitInASample() {
        // five notes at the peak a single note reaches: without limiting this is 2.75x full scale
        val sum = 5 * 0.55
        val limited = SoftLimiter.limit(sum)

        assertTrue("the sum must be contained, was $limited", limited < 1.0)
        assertTrue("and it should still be loud", limited > 0.9)
        assertTrue(abs(limited - 1.0) < 0.1)
    }
}

package com.a24solver.wakusei.solver

import java.math.BigInteger

/**
 * An exact rational number.
 *
 * Instances are immutable and *always* kept in lowest terms with the sign carried by the
 * numerator and no negative denominator, so structural equality is value equality:
 * `Rational.of(2, -6) == Rational.of(-1, 3)`. That invariant is what lets a `HashMap<Rational, _>`
 * be used as the solver's memo table.
 *
 * Arithmetic is [BigInteger]-backed rather than `Long`-backed on purpose. A `Long` version of this
 * class silently wraps once an intermediate numerator or denominator passes 2^63-1, and that is
 * reachable inside the solver's own input contract (`n <= 8`, `|x| < 256`): every input number may
 * be as large as 255, and a full product of eight of them is
 * `255^8 = 17878103347812890625`, which wraps to `-568640725896660991` as a signed 64-bit integer.
 * A wrapped value is not a rounding error that shows up as "almost right" — it can silently invent
 * a solution or hide one, which is exactly what an enumerator that promises *all* solutions must
 * never do. (Seven or fewer numbers stay inside `Long`: `255^7` still fits in 56 bits. The
 * correction therefore only matters at the top of the input range, and it costs nothing in
 * clarity.)
 */
class Rational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger
) : Comparable<Rational> {

    init {
        require(denominator != BigInteger.ZERO) { "Denominator must be non-zero" }
    }

    val isZero: Boolean get() = numerator.signum() == 0

    val isInteger: Boolean get() = denominator == BigInteger.ONE

    /** The value as a `Double`; only for display or unscientific spot checks, never for equality. */
    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    operator fun plus(other: Rational): Rational {
        // Denormalised fast path: both operands are integers, so no reduction is needed.
        if (denominator == BigInteger.ONE && other.denominator == BigInteger.ONE) {
            return of(numerator.add(other.numerator))
        }
        return normalise(
            numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
            denominator.multiply(other.denominator)
        )
    }

    operator fun minus(other: Rational): Rational {
        if (denominator == BigInteger.ONE && other.denominator == BigInteger.ONE) {
            return of(numerator.subtract(other.numerator))
        }
        return normalise(
            numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
            denominator.multiply(other.denominator)
        )
    }

    operator fun times(other: Rational): Rational {
        if (denominator == BigInteger.ONE && other.denominator == BigInteger.ONE) {
            return of(numerator.multiply(other.numerator))
        }
        return normalise(numerator.multiply(other.numerator), denominator.multiply(other.denominator))
    }

    /**
     * `this / other`, or null when [other] is zero — division by zero is not an error the solver
     * wants to throw for, it just invalidates that branch of the search.
     */
    fun divide(other: Rational): Rational? {
        if (other.isZero) return null
        if (numerator.signum() == 0) return ZERO
        return normalise(numerator.multiply(other.denominator), denominator.multiply(other.numerator))
    }

    operator fun unaryMinus(): Rational = Rational(numerator.negate(), denominator)

    /** `this / other`; see [divide]. */
    operator fun div(other: Rational): Rational? = divide(other)

    override fun compareTo(other: Rational): Int =
        numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator))

    override fun equals(other: Any?): Boolean =
        other is Rational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    /** `3`, or `-1/3` for a proper fraction — the form used inside solution expressions. */
    override fun toString(): String =
        if (isInteger) numerator.toString() else "$numerator/$denominator"

    companion object {
        val ZERO: Rational = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE: Rational = Rational(BigInteger.ONE, BigInteger.ONE)

        fun of(value: Long): Rational = of(BigInteger.valueOf(value))

        fun of(numerator: BigInteger): Rational = Rational(numerator, BigInteger.ONE)

        /** The rational `numerator / denominator`, reduced to lowest terms. */
        fun of(numerator: Long, denominator: Long): Rational =
            normalise(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator))

        private fun normalise(numerator: BigInteger, denominator: BigInteger): Rational =
            if (numerator.signum() == 0) {
                ZERO
            } else if (denominator.signum() < 0) {
                reduce(numerator.negate(), denominator.negate())
            } else {
                reduce(numerator, denominator)
            }

        private fun reduce(numerator: BigInteger, denominator: BigInteger): Rational {
            val gcd = numerator.gcd(denominator)
            return if (gcd == BigInteger.ONE) {
                Rational(numerator, denominator)
            } else {
                Rational(numerator.divide(gcd), denominator.divide(gcd))
            }
        }
    }
}

package com.a24solver.wakusei

import kotlin.math.abs

data class Rational (private var n: Long, private var d: Long = 1L) {
/****************************************
* Rational Number Class
*
* * var n: Long = 0; // numerator
* * var d: Long = 1; // denominator
*
* Refuse: NaN and Infinity
*/

    init {
        require (d != 0L) { "Denominator must be non-zero" }
        if (this.n == 0L) {
            this.d = 1
        } else {
            if (d < 0L) {
                this.n = -this.n
                this.d = -this.d
            } else {
                this.n = this.n
                this.d = this.d
            }
            reduce()
        }
    }

    private fun reduce() {
        val gcd = gcd(this.n, this.d)
        this.n /= gcd
        this.d /= gcd
    }

    fun add(other: Rational): Rational {
        return Rational(this.n * other.d + other.n * this.d, this.d * other.d)
    }

    fun subtract(other: Rational): Rational {
        return Rational(this.n * other.d - other.n * this.d, this.d * other.d)
    }

    fun multiply(other: Rational): Rational {
        return Rational(this.n * other.n, this.d * other.d)
    }

    fun divide(other: Rational): Rational? {
        if (other.n == 0L) {
            return null // Division by zero
        }
        return Rational(this.n * other.d, this.d * other.n)
    }

//    fun copy(): Rational = Rational(this.n, this.d)

    operator fun unaryPlus(): Rational = this.copy()
    operator fun unaryMinus(): Rational = Rational(-this.n, this.d)

//    override operator fun equals(other: Any?): Boolean {
//        return if (other !is Rational) false
//        else other.n == this.n && other.d == this.d
//    }

    operator fun plus(other: Rational): Rational = this.add(other)
    operator fun minus(other: Rational): Rational = this.subtract(other)
    operator fun times(other: Rational): Rational = this.multiply(other)
    operator fun div(other: Rational): Rational? = this.divide(other)

    operator fun compareTo(other: Rational): Int {
        return (this.n * other.d).compareTo(this.d * other.n)
    }

    override fun toString(): String {
        return if (d == 1L) "$n" else "$n/$d"
    }
}

fun gcd(a: Long, b: Long): Long {
    var x = abs(a)
    var y = abs(b)
    while (y != 0L) {
        val temp = y
        y = x % y
        x = temp
    }
    return x
}


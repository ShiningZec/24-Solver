package com.a24solver.wakusei

import kotlin.math.abs

class SyntaxTree {
    companion object
    {
        fun solveIt(target: Int, inputArray: Array<Int>): Set<String> {
            // map input
            require(inputArray.size in 1..8) {
                "Too big or too small. Input size should be > 0 and < 9"
            }
            require(
                inputArray.all {abs(it) < 256}
            ) {
                "Input number too big. A number should be < 256 and > -256"
            }
            val size = inputArray.size
            val numbers: Array<Rational> = inputArray.map{Rational(it.toLong())}.toTypedArray()
            fun resolve(root: Node?, signAssign: Array<Int>, permutation: Array<Int>) : Rational? {
                return if (root == null) null
                else if (root.isLeaf) numbers[permutation[root.leafIdx]]
                else {
                    val leftVal = resolve(root.left, signAssign, permutation)
                    val rightVal = resolve(root.right, signAssign, permutation)
                    if (leftVal == null || rightVal == null) null
                    else when (signAssign[root.signIdx] % 4) {
                        0 -> leftVal + rightVal
                        1 -> leftVal - rightVal
                        2 -> leftVal * rightVal
                        3 -> leftVal / rightVal
                        else -> null // should not happen though
                    }
                }
            }

            fun myPrint(root: Node?, signAssign: Array<Int>, permutation: Array<Int>) : String {
                return if (root == null) ""
                else if (root.isLeaf) numbers[permutation[root.leafIdx]].toString()
                else {
                    val leftVal: String = myPrint(root.left, signAssign, permutation)
                    val rightVal: String = myPrint(root.right, signAssign, permutation)
                    when (signAssign[root.signIdx] % 4) {
                        0 -> "($leftVal + $rightVal)"
                        1 -> "($leftVal - $rightVal)"
                        2 -> "($leftVal * $rightVal)"
                        3 -> "($leftVal / $rightVal)"
                        else -> "" // should not happen though
                    }
                }
            }
            val answerSheet: MutableSet<String> = mutableSetOf()

            for (root in generateTrees(size)) {
                for (signAssign in SignAssign(size)) {
                    for (permutation in permutations(size)) {
                        val result = resolve(root, signAssign, permutation)
                        if (Rational(target.toLong()) == result) {
                            answerSheet.add(myPrint(root, signAssign, permutation))
                        }
                    }
                }
            }
            return answerSheet.toSet()
        }
    }
}


fun generateTrees(
    size: Int,
    offset: Int = 0,
    offset2: Int = 0
): Sequence<Node> = sequence {
    require(size >= 1) {
        "Tree must contain at least one leaf"
    }

    if (size == 1) {
        yield(
            Node(
                isLeaf = true,
                leafIdx = offset
            )
        )
        return@sequence
    }

    for (leftSize in 1 until size) {
        val rightSize = size - leftSize

        for (leftTree in generateTrees(leftSize, offset, offset2 + 1)) {
            for (rightTree in generateTrees(
                rightSize, offset + leftSize, offset2 + leftSize
            )) {
                yield(
                    Node(
                        isLeaf = false,
                        left = leftTree,
                        right = rightTree,
                        signIdx = offset2
                    )
                )
            }
        }
    }
}

fun permutations(size: Int): Sequence<Array<Int>> = sequence {
    require(size > 0) { "A size must be above 0" }
    val bucket: Array<Int> = Array(size){0}
    val vis: Array<Boolean> = Array(size){false}
    suspend fun SequenceScope<Array<Int>>.backtrack(cur: Int) {
        if (cur == size) {
            yield(bucket)
            return
        }

        for (num in 0..<size) {
            if (vis[num]) continue

            vis[num] = true
            bucket[cur] = num
            backtrack(cur + 1)
            vis[num] = false
        }
    }

    backtrack(0)
}


class SignAssign(size: Int) : Iterable<Array<Int>> {
    private val signAssign: Array<Int> = Array(size){0}
    var isNew: Boolean = true

    override fun iterator(): Iterator<Array<Int>> {
        return object : Iterator<Array<Int>> {

            override fun hasNext() = signAssign.any { i -> i != 3 }
            override fun next(): Array<Int> {
                if (isNew) {
                    isNew = false
                    return signAssign
                } else {
                    var i = 0
                    while (i < signAssign.size) {
                        signAssign[i] = (signAssign[i] + 1) % 4
                        if (signAssign[i] == 0) (++i)
                        else break
                    }
                    return signAssign
                }
            }
        }
    }
}

class Node (
    var isLeaf: Boolean = false,
    var left: Node? = null,
    var right: Node? = null,
    var signIdx: Int = 0,
    var leafIdx: Int = 0
) {
/************************************
* * Node
*
* A Node Contains Information
**/
}

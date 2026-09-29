package com.a24solver.wakusei

import org.junit.Assert.assertTrue
import org.junit.Test


class SyntaxTreeTest {

    @Test
    fun testSolveIt1() {
        val target = 12
        val numbers = arrayOf(12)
        val expect = "12"
        val answerSheet = SyntaxTree.solveIt(target, numbers)
        assertTrue(answerSheet.any { it == expect })
    }

    @Test
    fun testSolveIt2() {
        val target = 25
        val numbers = arrayOf(12, 13)
        val expect = "(12 + 13)"
        val answerSheet = SyntaxTree.solveIt(target, numbers)
        assertTrue(answerSheet.any { it == expect })
    }

    @Test
    fun testSolveIt3() {
        val target = 12
        val numbers = arrayOf(9, 8, 6)
        val expect = "((9 * 8) / 6)"
        val answerSheet = SyntaxTree.solveIt(target, numbers)
        assertTrue(answerSheet.any { it == expect })
    }

    @Test
    fun testSolveIt4() {
        val target = 24
        val numbers = arrayOf(23, 23, 23, 23)
        val expect = "(((23 * 23) + 23) / 23)"
        val answerSheet = SyntaxTree.solveIt(target, numbers)
        assertTrue(answerSheet.any { it == expect })
    }

    @Test
    fun testSolveIt5() {
        val target = 60
        val numbers = arrayOf(1, 1, 9, 9, 6)
        val expect = "(((1 + (1 / 9)) * 9) * 6)"
        val answerSheet = SyntaxTree.solveIt(target, numbers)
        assertTrue(answerSheet.any { it == expect })
    }

//    @Test
//    fun testSolveIt6() {
//        val target = 420
//        val numbers = arrayOf(1, 1, 9, 9, 6, 7)
//        val expect = "(((1 + (1 / 9)) * 9) * (6 * 7))"
//        val answerSheet = SyntaxTree.solveIt(target, numbers)
//        assertTrue(answerSheet.any { it == "(((1 + (1 / 9)) * 9) * (6 * 7))" })
//    }

    @Test
    fun testPermutations() {
        var flag = false
        for (permu in permutations(6)) {
            if (permu.contentEquals(arrayOf(3, 4, 5, 2, 0, 1))) {
                flag = true
                break
            }
        }
        assertTrue(flag)  // Any permutation should be met under all permutations
    }

    @Test
    fun testSigns() {
        var flag = false
        for (permu in SignAssign(6)) {
            if (permu.contentEquals(arrayOf(3,3,3,3,3,3))) {
                flag = true
                break
            }
        }
        assertTrue(flag)  // Any sign list should be met under all sign permutations
    }

    @Test
    fun testGenTrees() {
        val size = 4

        val leafBucket: Array<Boolean> = Array(size){false}
        val nonleafBucket: Array<Boolean> = Array(size - 1){false}

        var leafConflict = false
        var nonleafConflict = false

        fun trace(root: Node?) {
            if (root == null) return

            if (root.isLeaf) {
                if (!leafBucket[root.leafIdx]) leafBucket[root.leafIdx] = true
                else leafConflict = true
            }
            else {
                if (!nonleafBucket[root.signIdx]) nonleafBucket[root.signIdx] = true
                else nonleafConflict = true

                trace(root.left)
                trace(root.right)
            }
        }
        for (root in generateTrees(size)) {
            leafBucket.fill(false)
            nonleafBucket.fill(false)
            trace(root)
            assertTrue(
                leafBucket.all{ it } &&
                nonleafBucket.all{ it } &&
                !leafConflict &&
                !nonleafConflict
            )
        }
    }

}
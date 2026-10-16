package com.a24solver.wakusei.solver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one architectural promise the solver makes: `com.a24solver.wakusei.solver` is plain
 * Kotlin over `java.math`, with no dependency on Android at all.
 *
 * That is what lets the whole search be tested on a bare JVM in milliseconds, and it keeps the puzzle
 * logic usable from a future CLI, a benchmark or a different UI. It is easy to lose by accident —
 * one `android.util.Log` or `Context` parameter and the package is silently welded to the framework
 * — so it is checked, not just documented.
 *
 * The check reads the compiled production class files and looks for Android package references in
 * their constant pools. It is a heuristic, but a strict one for this purpose: any use of an Android
 * type necessarily leaves its name in the class file.
 */
class SolverPurityTest {

    @Test
    fun theSolverPackageDoesNotReferenceAndroid() {
        val classes = solverClassFiles()
        assumeTrue(
            "the compiled solver is not on disk as a directory in this runtime",
            classes.isNotEmpty()
        )

        // assembled at runtime so this test's own class file cannot match its own needles
        val androidPackages = listOf("android", "androidx").map { "$it/" }

        for (file in classes) {
            val constantPool = String(file.readBytes(), Charsets.ISO_8859_1)
            for (packageName in androidPackages) {
                assertFalse(
                    "${file.name} references $packageName — the solver package must stay " +
                        "framework-free so it can run on a bare JVM",
                    constantPool.contains(packageName)
                )
            }
        }

        assertTrue("expected the solver to be several classes", classes.size >= 3)
        assertTrue(
            "the scan did not find class metadata, so it proves nothing",
            classes.any { String(it.readBytes(), Charsets.ISO_8859_1).contains("java/lang") }
        )
    }

    /**
     * The production `solver` package as compiled class files, located through the code source of
     * [Solver] itself — *not* through the classloader, which would also hand back this test's own
     * classes from the very same package.
     */
    private fun solverClassFiles(): List<File> {
        val packagePath = "com/a24solver/wakusei/solver"
        val classesRoot = Solver::class.java.protectionDomain
            ?.codeSource
            ?.location
            ?.let { runCatching { File(it.toURI()) }.getOrNull() }
            ?: return emptyList()

        val packageDirectory =
            if (classesRoot.name == "solver") classesRoot else File(classesRoot, packagePath)
        if (!packageDirectory.isDirectory) return emptyList()

        return packageDirectory.listFiles { file: File ->
            file.extension == "class" && !file.name.endsWith("Test.class")
        }?.toList().orEmpty()
    }
}

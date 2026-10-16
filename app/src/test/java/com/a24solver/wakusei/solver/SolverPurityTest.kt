package com.a24solver.wakusei.solver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.jar.JarFile

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
        val classes = solverClasses()

        // A test that cannot see the code it guards would pass while proving nothing, so this is a
        // failure rather than an assumption: the classes are on disk either as a directory or inside
        // a jar, and both are handled below.
        assertTrue("could not find the compiled solver classes to scan", classes.isNotEmpty())

        // assembled at runtime so this test's own class file cannot match its own needles
        val androidPackages = listOf("android", "androidx").map { "$it/" }

        for ((name, bytes) in classes) {
            val constantPool = String(bytes, Charsets.ISO_8859_1)
            for (packageName in androidPackages) {
                assertFalse(
                    "$name references $packageName — the solver package must stay framework-free " +
                        "so it can run on a bare JVM",
                    constantPool.contains(packageName)
                )
            }
            assertTrue(
                "$name does not look like a class file, so the scan proves nothing",
                constantPool.contains("java/lang")
            )
        }

        assertTrue(
            "expected the solver to be several classes, scanned ${classes.size}",
            classes.size >= 3
        )
    }

    /**
     * The production `solver` package as class files, located through the code source of [Solver]
     * itself — *not* through the classloader, which would also hand back this test's own classes from
     * the very same package. Handles both shapes the build can hand over: a directory of classes and
     * a jar.
     */
    private fun solverClasses(): Map<String, ByteArray> {
        val packagePath = "com/a24solver/wakusei/solver"
        val location = Solver::class.java.protectionDomain?.codeSource?.location ?: return emptyMap()
        val source = runCatching { File(location.toURI()) }.getOrNull() ?: return emptyMap()

        return when {
            source.isDirectory -> {
                val directory =
                    if (source.name == "solver") source else File(source, packagePath)
                directory.listFiles { file: File ->
                    file.extension == "class" && !file.name.endsWith("Test.class")
                }?.associate { it.name to it.readBytes() }.orEmpty()
            }

            source.isFile -> JarFile(source).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.startsWith("$packagePath/") && it.name.endsWith(".class") }
                    .filterNot { it.name.endsWith("Test.class") }
                    .associate { entry ->
                        entry.name.substringAfterLast('/') to jar.getInputStream(entry).readBytes()
                    }
            }

            else -> emptyMap()
        }
    }
}

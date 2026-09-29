package com.a24solver.wakusei

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Guards the launcher icon artwork.
 *
 * Two things are checked: the icon resources resolve (the two adaptive-icon layers plus the wrapper the
 * manifest points at), and the vector geometry in `res/drawable/ic_launcher_foreground.xml` stays inside
 * the adaptive-icon safe circle — a centred 66dp disc on the 108dp canvas. Launchers may crop away
 * everything outside that disc, so a mark drifting outwards loses its edge on some devices.
 *
 * Deliberately no raster assertions: Robolectric cannot rasterise a VectorDrawable here (drawing one and
 * reading the bitmap back yields empty pixels), so the artwork was eyeballed by rendering the very same
 * path data as SVG in a browser instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconTest {

    private val centre = 54.0      // the viewport is 108 x 108
    private val safeRadius = 33.0  // a 66dp disc
    private val source = File("src/main/res/drawable/ic_launcher_foreground.xml")

    // ---- resources ------------------------------------------------------------------------------

    @Test
    fun iconResourcesResolve() {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density

        val foreground = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        assertNotNull("foreground layer is missing", foreground)
        assertTrue(
            "foreground should be a 108dp canvas, was ${foreground!!.intrinsicWidth}px",
            foreground.intrinsicWidth == (108 * density).toInt()
        )
        assertNotNull(
            "background layer is missing",
            ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
        )

        val adaptive = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertNotNull("R.mipmap.ic_launcher is missing", adaptive)
        assertTrue(
            "R.mipmap.ic_launcher should be an adaptive icon on API 26+ (was ${adaptive!!::class.java.simpleName})",
            adaptive is AdaptiveIconDrawable
        )
    }

    // ---- geometry -------------------------------------------------------------------------------

    /** Minimal reader for the M / L / h / v / z subset the artwork uses. */
    private fun subpaths(pathData: String): List<List<Pair<Double, Double>>> {
        val out = mutableListOf<List<Pair<Double, Double>>>()
        var points = mutableListOf<Pair<Double, Double>>()
        var x = 0.0
        var y = 0.0
        val tokens = Regex("""([MLHVZmlhvz])|(-?\d*\.?\d+)""").findAll(pathData).map { it.value }.toList()
        var i = 0
        fun num(): Double = tokens[i++].toDouble()
        while (i < tokens.size) {
            when (val cmd = tokens[i++]) {
                "M", "L" -> { x = num(); y = num() }
                "m", "l" -> { x += num(); y += num() }
                "H" -> x = num()
                "h" -> x += num()
                "V" -> y = num()
                "v" -> y += num()
                "Z", "z" -> {
                    if (points.isNotEmpty()) out.add(points)
                    points = mutableListOf()
                    continue
                }
                else -> throw AssertionError("unexpected path command '$cmd' in $pathData")
            }
            points.add(x to y)
        }
        if (points.isNotEmpty()) out.add(points)
        return out
    }

    private fun artwork(): Pair<List<Pair<Double, Double>>, List<Pair<Double, Double>>> {
        assertTrue("icon artwork source not found at ${source.absolutePath}", source.isFile)
        val xml = source.readText()
        val fills = Regex("""android:fillColor="#FFFFFF"\s+android:pathData="([^"]+)"""")
            .findAll(xml).map { it.groupValues[1] }.toList()
        val strokes = Regex(
            """android:strokeColor="#FFFFFF"\s+android:strokeWidth="([\d.]+)"\s+android:strokeLineCap="\w+"\s+android:pathData="([^"]+)""""
        ).findAll(xml).map { it.groupValues[1] to it.groupValues[2] }.toList()

        assertTrue("no filled path found in the artwork", fills.isNotEmpty())
        assertTrue("no stroked path found in the artwork (the V / R diagonals)", strokes.isNotEmpty())
        assertTrue("expected a single filled path, found ${fills.size}", fills.size == 1)

        val fillPoints = subpaths(fills.single()).flatten()
        val strokePoints = strokes.flatMap { subpaths(it.second) }.flatten()
        return fillPoints to strokePoints
    }

    @Test
    fun everyMarkStaysInsideTheSafeCircle() {
        val (fillPoints, strokePoints) = artwork()
        val strokeSlack = 1.0      // half of the 2.0 stroke width

        var worst = 0.0
        var where = ""
        (fillPoints.map { 0.0 to it } + strokePoints.map { strokeSlack to it }).forEach { (slack, p) ->
            val d = hypot(p.first - centre, p.second - centre) - slack
            if (d > worst) {
                worst = d
                where = "(${p.first}, ${p.second})"
            }
        }
        println("icon geometry: worst mark point $where, %.2f from the centre".format(worst))
        assertTrue(
            "mark point $where reaches %.2f from the centre, the safe circle is %.0f".format(worst, safeRadius),
            worst <= safeRadius
        )
    }

    @Test
    fun artworkIsCentredAndCarriesBothMarks() {
        val (fillPoints, strokePoints) = artwork()
        val all = fillPoints + strokePoints
        val minX = all.minOf { it.first }
        val maxX = all.maxOf { it.first }
        val minY = all.minOf { it.second }
        val maxY = all.maxOf { it.second }
        println("icon geometry: box x[$minX..$maxX] y[$minY..$maxY]")

        assertTrue("artwork is not centred horizontally: $minX / $maxX",
            abs((minX - centre) + (maxX - centre)) <= 2.0)
        assertTrue("artwork is not centred vertically: $minY / $maxY",
            abs((minY - centre) + (maxY - centre)) <= 6.0)

        // Two stacked marks with a real gap: the "24" block above y = 60, the SOLVER wordmark below it.
        val digits = fillPoints.filter { it.second < 60.0 }
        val word = fillPoints.filter { it.second > 60.0 }
        assertTrue("the upper mark (24) is missing", digits.isNotEmpty())
        assertTrue("the lower mark (SOLVER) is missing", word.isNotEmpty())

        val gap = word.minOf { it.second } - digits.maxOf { it.second }
        val digitWidth = digits.maxOf { it.first } - digits.minOf { it.first }
        val wordWidth = word.maxOf { it.first } - word.minOf { it.first }
        println("icon geometry: gap %.1f, digits %.1f wide, wordmark %.1f wide".format(gap, digitWidth, wordWidth))

        assertTrue("the two marks are not separated (gap $gap)", gap > 3.0)
        assertTrue("the SOLVER wordmark should be the wider mark ($wordWidth vs $digitWidth)",
            wordWidth > digitWidth)
    }
}

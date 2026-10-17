package com.a24solver.wakusei.help

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tiny Markdown reader the help page uses: `# ` headings, blank-line separated paragraphs,
 * `**bold**`. Everything else is literal text, on purpose — a general Markdown engine would bring a
 * dependency and a renderer for constructs the documents do not use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpMarkdownTest {

    @Test
    fun aHeadingBecomesATitleBlock() {
        val blocks = HelpMarkdown.parse("# Help - How to Use 24 Solver?\n\nA paragraph.")

        assertEquals(
            listOf(
                HelpBlock(HelpBlock.Kind.TITLE, "Help - How to Use 24 Solver?"),
                HelpBlock(HelpBlock.Kind.PARAGRAPH, "A paragraph.")
            ),
            blocks
        )
    }

    @Test
    fun blankLinesSeparateParagraphsAndSingleNewlinesDoNot() {
        val blocks = HelpMarkdown.parse(
            """
            First line
            still the first paragraph.

            Second paragraph.
            """.trimIndent()
        )

        assertEquals(2, blocks.size)
        assertEquals("First line\nstill the first paragraph.", blocks[0].text)
        assertEquals("Second paragraph.", blocks[1].text)
    }

    @Test
    fun aDocumentWithoutABlankLineStillParses() {
        val blocks = HelpMarkdown.parse("# Title\nA paragraph right after the title.")

        assertEquals(HelpBlock.Kind.TITLE, blocks[0].kind)
        assertEquals("A paragraph right after the title.", blocks[1].text)
    }

    @Test
    fun manyBlankLinesCollapseAndWindowsLineEndingsAreFine() {
        val blocks = HelpMarkdown.parse("# Title\r\n\r\n\r\nBody\r\n\r\n")

        assertEquals(2, blocks.size)
        assertEquals("Body", blocks[1].text)
    }

    @Test
    fun anEmptyDocumentHasNoBlocks() {
        assertEquals(emptyList<HelpBlock>(), HelpMarkdown.parse(""))
        assertEquals(emptyList<HelpBlock>(), HelpMarkdown.parse("\n\n  \n"))
    }

    @Test
    fun hashesThatAreNotHeadingsStayText() {
        val blocks = HelpMarkdown.parse("Press [#] to comment\nnot # a heading")

        assertEquals(1, blocks.size)
        assertEquals(HelpBlock.Kind.PARAGRAPH, blocks[0].kind)
        assertEquals("Press [#] to comment\nnot # a heading", blocks[0].text)
    }

    // ------------------------------------------------------------------ bold

    @Test
    fun doubleAsterisksBecomeBoldSpans() {
        val spanned = HelpMarkdown.toSpanned("**How to use**: type a number.") as Spanned

        assertEquals("How to use: type a number.", spanned.toString())
        val bold = spanned.getSpans(0, spanned.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
        assertEquals(1, bold.size)
        assertEquals(0, spanned.getSpanStart(bold.single()))
        assertEquals("How to use".length, spanned.getSpanEnd(bold.single()))
    }

    @Test
    fun textWithoutMarkersIsReturnedAsItIs() {
        assertEquals("plain text", HelpMarkdown.toSpanned("plain text").toString())
        assertTrue(HelpMarkdown.toSpanned("plain text") !is Spanned)
    }

    @Test
    fun severalBoldRunsAreEachSpanned() {
        val spanned = HelpMarkdown.toSpanned("**a** normal **b**") as Spanned

        assertEquals("a normal b", spanned.toString())
        assertEquals(2, spanned.getSpans(0, spanned.length, StyleSpan::class.java).size)
    }

    @Test
    fun anUnclosedMarkerKeepsTheText() {
        val spanned = HelpMarkdown.toSpanned("**unclosed bold")

        assertEquals(
            "the text must survive a missing marker",
            "unclosed bold",
            spanned.toString()
        )
    }
}

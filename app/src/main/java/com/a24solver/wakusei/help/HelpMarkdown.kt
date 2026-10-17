package com.a24solver.wakusei.help

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface

/** One block of a help page: the document's title, or a paragraph. */
data class HelpBlock(val kind: Kind, val text: String) {

    enum class Kind { TITLE, PARAGRAPH }
}

/**
 * The slice of Markdown the help documents actually use: `# ` headings, blank-line separated
 * paragraphs, and `**bold**`.
 *
 * The app bundles the documents themselves (see `docs/help`) instead of storing the text in string
 * resources, so a paragraph reads the same in the file, in the help screen and in the archive. That
 * only works with a parser small enough to trust — hence one that understands exactly these three
 * constructs and treats everything else as literal text, rather than a general Markdown engine that
 * would bring a dependency, a renderer and a new class of surprises with it.
 *
 * A line break *inside* a paragraph is kept: the documents use it to put a bold label on its own line
 * ("**How to use**:") before the explanation.
 */
object HelpMarkdown {

    private const val TITLE_PREFIX = "# "
    private const val BOLD = "**"

    /** Splits [markdown] into blocks, in document order. Empty documents give an empty list. */
    fun parse(markdown: String): List<HelpBlock> {
        val blocks = mutableListOf<HelpBlock>()
        val paragraph = StringBuilder()

        fun flush() {
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) blocks += HelpBlock(HelpBlock.Kind.PARAGRAPH, text)
            paragraph.setLength(0)
        }

        for (rawLine in markdown.lines()) {
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith(TITLE_PREFIX) -> {
                    flush()
                    blocks += HelpBlock(HelpBlock.Kind.TITLE, line.removePrefix(TITLE_PREFIX).trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return blocks
    }

    /**
     * Renders the inline markup of [raw] — `**bold**` — as bold spans.
     *
     * An odd number of markers leaves the tail bold instead of dropping the text: a missing asterisk
     * should look slightly wrong, never lose a sentence.
     */
    fun toSpanned(raw: String): CharSequence {
        if (!raw.contains(BOLD)) return raw

        val builder = SpannableStringBuilder()
        var bold = false
        for (part in raw.split(BOLD)) {
            if (part.isNotEmpty()) {
                val start = builder.length
                builder.append(part)
                if (bold) {
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start,
                        builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            bold = !bold
        }
        return builder
    }
}

package com.a24solver.wakusei

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import com.a24solver.wakusei.databinding.FragmentHelpBinding
import com.a24solver.wakusei.help.HelpBlock
import com.a24solver.wakusei.help.HelpMarkdown
import com.a24solver.wakusei.help.HelpText

/**
 * The help page: the text of `docs/help/help_doc*.md` for the language the app is running in.
 *
 * It is opened automatically the first time the app starts, and afterwards from the settings screen.
 * The text is rendered block by block rather than dropped into one `TextView`, so a title can be a
 * title and a paragraph can have real spacing — and so the page keeps looking like a document when
 * the user enlarges the system font.
 *
 * Nothing here is hard-coded: the paragraphs, the wording and the button names all come from the
 * document, which is also the file kept in `docs/help`. Translating the help means editing one
 * Markdown file.
 */
class HelpFragment : Fragment() {

    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render(HelpMarkdown.parse(HelpText.forCurrentLocale(requireContext())))
    }

    private fun render(blocks: List<HelpBlock>) {
        binding.helpContent.removeAllViews()

        if (blocks.isEmpty()) {
            // the asset is missing or empty — say so instead of showing a blank page
            binding.helpContent.addView(textView(getString(R.string.help_unavailable), isTitle = false))
            return
        }

        blocks.forEachIndexed { index, block ->
            val isTitle = block.kind == HelpBlock.Kind.TITLE
            binding.helpContent.addView(
                textView(HelpMarkdown.toSpanned(block.text), isTitle),
                blockParams(index, isTitle)
            )
        }
    }

    private fun textView(text: CharSequence, isTitle: Boolean): TextView {
        val view = TextView(requireContext())
        view.setTextAppearanceResource(
            if (isTitle) R.style.TextAppearance_App_HelpTitle else R.style.TextAppearance_App_HelpBody
        )
        view.text = text
        return view
    }

    /**
     * The title sits at the top with room beneath it; every block after the first is pushed down so
     * consecutive paragraphs never touch.
     */
    private fun blockParams(index: Int, isTitle: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = when {
                index == 0 -> 0
                isTitle -> dp(24)
                else -> dp(16)
            }
        }

    private fun dp(value: Int): Int =
        (value * requireContext().resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** `TextView.setTextAppearance(int)` is deprecated; this is the one that works on every API level. */
private fun TextView.setTextAppearanceResource(resourceId: Int) {
    TextViewCompat.setTextAppearance(this, resourceId)
}

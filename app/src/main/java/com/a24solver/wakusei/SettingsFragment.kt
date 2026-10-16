package com.a24solver.wakusei

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.widget.doAfterTextChanged
import com.a24solver.wakusei.databinding.FragmentSettingsBinding
import com.a24solver.wakusei.settings.AppLanguage
import com.a24solver.wakusei.settings.DefaultTargetPreset
import com.a24solver.wakusei.settings.Settings
import java.util.Locale

/**
 * Settings screen: language, the default target, and whether the keypad makes a sound.
 *
 * The default target is a slider with fixed stops ([DefaultTargetPreset]) plus a number field. The
 * stops cover the values people actually want, and — because two of them are negative — they also
 * answer "can the target be negative?" without the user having to find out by accident. The
 * `CUSTOMIZE` stop is the way into the number field, which is otherwise disabled.
 *
 * Nothing here touches the target that is currently in use: the setting only decides what a fresh
 * input screen starts with and what CLEAR long-press returns to.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val settings by lazy { Settings(requireContext()) }

    /** Guards the programmatic widget updates below from re-entering their listeners. */
    private var syncing = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpLanguage()
        setUpDefaultTarget()
        setUpSound()
        render()
    }

    // -------------------------------------------------------------- language

    private fun setUpLanguage() {
        val languages = AppLanguage.entries
        binding.dropdownLanguage.setSimpleItems(languages.map { getString(it.labelRes) }.toTypedArray())
        binding.dropdownLanguage.setOnItemClickListener { _, _, position, _ ->
            val chosen = languages[position]
            val wasPending = settings.requestedLanguage != settings.activeLanguage
            // Only records the choice: the switch happens at the next start (see WakuseiApplication)
            settings.requestLanguage(chosen)
            renderLanguage()
            if (!wasPending && settings.languageChangeIsPending) {
                // the first time this becomes a pending change, say so; a Toast rather than a dialog,
                // because nothing is being taken away from the user
                Toast.makeText(
                    requireContext(),
                    getString(R.string.msg_language_pending),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Shows which language is on screen and, when they differ, which one is waiting for a restart.
     */
    private fun renderLanguage() {
        val active = getString(settings.activeLanguage.labelRes)
        val requested = getString(settings.requestedLanguage.labelRes)

        binding.dropdownLanguage.setText(requested, false)
        binding.textLanguageStatus.text = if (settings.languageChangeIsPending) {
            getString(R.string.settings_language_active, active) + "\n" +
                getString(R.string.settings_language_pending, requested)
        } else {
            getString(R.string.settings_language_active, active)
        }
    }

    // -------------------------------------------------------- default target

    private fun setUpDefaultTarget() {
        val presets = DefaultTargetPreset.entries
        binding.sliderDefaultTarget.valueTo = (presets.size - 1).toFloat()
        // the drag tooltip names the stop the user is on, e.g. "-10" or "CUSTOMIZE"
        binding.sliderDefaultTarget.setLabelFormatter { value: Float ->
            nodeLabel(DefaultTargetPreset.forIndex(value.toInt()))
        }
        binding.sliderDefaultTarget.addOnChangeListener { _, value, _ ->
            // `syncing` marks the programmatic updates made by render(); everything else is either
            // the user dragging the slider or a test setting the value
            if (syncing) return@addOnChangeListener
            val preset = DefaultTargetPreset.forIndex(value.toInt())
            settings.defaultTargetPreset = preset
            preset.presetValue?.let { settings.customDefaultTarget = it }
            render()
        }

        binding.inputDefaultTarget.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) renderDefaultTarget(showErrors = true)
        }
        binding.inputDefaultTarget.doAfterTextChanged {
            if (syncing) return@doAfterTextChanged
            renderDefaultTarget(showErrors = false)
        }
    }

    /**
     * Reads the number field and, when it holds a legal value, stores it. Returns whether it did —
     * an illegal value is reported on the field and leaves the stored setting alone.
     */
    private fun renderDefaultTarget(showErrors: Boolean): Boolean {
        val layout = binding.inputDefaultTargetLayout
        val text = binding.inputDefaultTarget.text?.toString().orEmpty().trim()
        val parsed = text.toIntOrNull()

        // an empty or half-typed field is not an error while the user is still editing it
        layout.error = null
        return when {
            text.isEmpty() -> {
                if (showErrors) layout.error = getString(R.string.err_default_target_not_a_number)
                false
            }
            parsed == null -> {
                layout.error = getString(R.string.err_default_target_not_a_number)
                false
            }
            !Settings.isValidTarget(parsed) -> {
                layout.error = getString(R.string.err_default_target_range, Settings.MAX_ABS_TARGET)
                false
            }
            else -> {
                settings.customDefaultTarget = parsed
                updateCurrentLabel()
                true
            }
        }
    }

    // ----------------------------------------------------------------- sound

    private fun setUpSound() {
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            if (syncing) return@setOnCheckedChangeListener
            settings.soundEnabled = isChecked
        }
    }

    // --------------------------------------------------------------- render

    private fun render() {
        val preset = settings.defaultTargetPreset
        val custom = preset.isCustom

        syncing = true
        renderLanguage()
        binding.sliderDefaultTarget.value = preset.ordinal.toFloat()
        // Locale.ROOT on purpose: this text goes straight back through toIntOrNull(), so it has to
        // stay ASCII digits even in a locale that would otherwise render its own numerals
        binding.inputDefaultTarget.setText(
            String.format(Locale.ROOT, "%d", preset.presetValue ?: settings.customDefaultTarget)
        )
        binding.inputDefaultTarget.isEnabled = custom
        binding.inputDefaultTargetLayout.isEnabled = custom
        binding.inputDefaultTargetLayout.error = null
        binding.inputDefaultTargetLayout.helperText = custom
            .takeIf { it }
            ?.let { getString(R.string.settings_default_target_helper, Settings.MAX_ABS_TARGET) }
        binding.switchSound.isChecked = settings.soundEnabled
        syncing = false

        updateCurrentLabel()
    }

    /**
     * Shows which slider node is in force and what it means: the value for a preset node, and
     * `CUSTOMIZE · <value>` for the node that hands over to the number field — otherwise a user on
     * CUSTOMIZE would have no way of telling the node apart from the value.
     */
    private fun updateCurrentLabel() {
        val node = settings.defaultTargetPreset
        val description = if (node.isCustom) {
            "${getString(R.string.default_target_customize)} · ${settings.defaultTarget}"
        } else {
            settings.defaultTarget.toString()
        }
        binding.textDefaultTarget.text =
            getString(R.string.settings_default_target_current, description)
    }

    private fun nodeLabel(preset: DefaultTargetPreset): String =
        preset.presetValue?.let { getString(R.string.settings_default_target_value, it) }
            ?: getString(R.string.default_target_customize)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        // Leaving this screen is when the language choice takes effect — the screen the user is
        // standing on is the one that must not be rebuilt under their finger.
        if (LanguageApplyRule.shouldApply(
                isChangingConfigurations = requireActivity().isChangingConfigurations,
                isPending = settings.languageChangeIsPending
            )
        ) {
            settings.applyRequestedLanguage()
        }
    }
}

/**
 * When a language choice is put into effect.
 *
 * Separated out because the interesting part is a single exception: a configuration change also
 * destroys this screen's view, and a rotation is not the user leaving — switching the language there
 * would rebuild the screen mid-rotation, which is exactly the surprise the deferred switch exists to
 * avoid.
 */
internal object LanguageApplyRule {

    fun shouldApply(isChangingConfigurations: Boolean, isPending: Boolean): Boolean =
        isPending && !isChangingConfigurations
}

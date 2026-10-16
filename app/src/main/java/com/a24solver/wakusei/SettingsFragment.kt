package com.a24solver.wakusei

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            // persists the choice and switches the running app over to it
            settings.applyLanguage(languages[position])
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
        val language = settings.language
        val preset = settings.defaultTargetPreset
        val custom = preset.isCustom

        syncing = true
        binding.dropdownLanguage.setText(getString(language.labelRes), false)
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
    }
}

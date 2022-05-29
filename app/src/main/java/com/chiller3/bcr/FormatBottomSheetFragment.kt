package com.chiller3.bcr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.chiller3.bcr.databinding.FormatBottomSheetBinding
import com.chiller3.bcr.databinding.FormatBottomSheetButtonBinding
import com.chiller3.bcr.format.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

class FormatBottomSheetFragment : BottomSheetDialogFragment(),
    MaterialButtonToggleGroup.OnButtonCheckedListener, LabelFormatter, Slider.OnChangeListener,
    View.OnClickListener {
    private var _binding: FormatBottomSheetBinding? = null
    private val binding
        get() = _binding!!

    private val buttonIdToFormat = HashMap<Int, Format>()
    private val formatToButtonId = HashMap<Format, Int>()
    private lateinit var formatParamInfo: FormatParamInfo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FormatBottomSheetBinding.inflate(inflater, container, false)

        binding.paramSlider.setLabelFormatter(this)
        binding.paramSlider.addOnChangeListener(this)

        binding.reset.setOnClickListener(this)

        for (format in Formats.all) {
            if (!format.supported) {
                continue
            }

            val buttonBinding = FormatBottomSheetButtonBinding.inflate(
                inflater, binding.nameGroup, false)
            val id = ViewCompat.generateViewId()
            buttonBinding.root.id = id
            buttonBinding.root.text = format.name
            binding.nameGroup.addView(buttonBinding.root)
            buttonIdToFormat[id] = format
            formatToButtonId[format] = id
        }

        binding.nameGroup.addOnButtonCheckedListener(this)

        refreshFormat()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Update UI based on currently selected format in the preferences.
     *
     * Calls [refreshParam] via [onButtonChecked].
     */
    private fun refreshFormat() {
        val (format, _) = Formats.fromPreferences(requireContext())
        binding.nameGroup.check(formatToButtonId[format]!!)
    }

    /**
     * Update parameter title and slider to match format parameter specifications.
     */
    private fun refreshParam() {
        val (format, param) = Formats.fromPreferences(requireContext())
        formatParamInfo = format.paramInfo

        when (val info = format.paramInfo) {
            is RangedParamInfo -> {
                binding.paramGroup.isVisible = true

                binding.paramTitle.setText(when (info.type) {
                    RangedParamType.CompressionLevel -> R.string.bottom_sheet_compression_level
                    RangedParamType.Bitrate -> R.string.bottom_sheet_bitrate
                })

                binding.paramSlider.valueFrom = info.range.first.toFloat()
                binding.paramSlider.valueTo = info.range.last.toFloat()
                binding.paramSlider.stepSize = info.stepSize.toFloat()
                binding.paramSlider.value = (param ?: info.default).toFloat()
            }
            NoParamInfo -> {
                binding.paramGroup.isVisible = false

                // Needed due to a bug in the material3 library where the slider label does not disappear
                // when the slider visibility is set to View.GONE
                // https://github.com/material-components/material-components-android/issues/2726
                val ensureLabelsRemoved = binding.paramSlider.javaClass.superclass
                    .getDeclaredMethod("ensureLabelsRemoved")
                ensureLabelsRemoved.isAccessible = true
                ensureLabelsRemoved.invoke(binding.paramSlider)
            }
        }
    }

    override fun onButtonChecked(
        group: MaterialButtonToggleGroup?,
        checkedId: Int,
        isChecked: Boolean
    ) {
        if (isChecked) {
            Preferences.setFormatName(requireContext(), buttonIdToFormat[checkedId]!!.name)
            refreshParam()
        }
    }

    override fun getFormattedValue(value: Float): String =
        formatParamInfo.format(value.toUInt())

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            binding.paramSlider -> {
                val format = buttonIdToFormat[binding.nameGroup.checkedButtonId]!!
                Preferences.setFormatParam(requireContext(), format.name, value.toUInt())
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.reset -> {
                Preferences.resetAllFormats(requireContext())
                refreshFormat()
                // Need to explicitly refresh the parameter when the default format is already chosen
                refreshParam()
            }
        }
    }

    companion object {
        val TAG: String = FormatBottomSheetFragment::class.java.simpleName
    }
}
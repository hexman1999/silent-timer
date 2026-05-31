package com.silentapp

import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class PresetEditDialog : DialogFragment() {

    private var preset: Preset? = null
    private var onSave: ((Preset) -> Unit)? = null
    private var onDelete: ((Preset) -> Unit)? = null

    private var totalSeconds = 0
    private var selectedMode = MODE_SILENT

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_preset_edit, container, false)

        preset = arguments?.getSerializable(ARG_PRESET) as? Preset

        val nameInput = view.findViewById<TextInputEditText>(R.id.presetNameInput)
        val timeBtn = view.findViewById<MaterialButton>(R.id.presetTimeBtn)
        val secDisplay = view.findViewById<TextView>(R.id.presetSecDisplay)
        val secInc = view.findViewById<View>(R.id.presetSecInc)
        val secDec = view.findViewById<View>(R.id.presetSecDec)
        val modeGroup = view.findViewById<RadioGroup>(R.id.presetModeGroup)
        val saveBtn = view.findViewById<MaterialButton>(R.id.presetSaveBtn)
        val deleteBtn = view.findViewById<MaterialButton>(R.id.presetDeleteBtn)

        val p = preset
        if (p != null) {
            nameInput.setText(p.label)
            totalSeconds = p.totalSeconds
            selectedMode = p.mode
            when (p.mode) {
                MODE_SILENT -> modeGroup.check(R.id.presetModeSilent)
                MODE_VIBRATE -> modeGroup.check(R.id.presetModeVibrate)
                MODE_DND -> modeGroup.check(R.id.presetModeDnd)
            }
            deleteBtn.visibility = View.VISIBLE
        } else {
            totalSeconds = 1800
            selectedMode = MODE_VIBRATE
            modeGroup.check(R.id.presetModeVibrate)
            deleteBtn.visibility = View.GONE
        }
        updateTimeButton(timeBtn)
        updateSecDisplay(secDisplay)

        timeBtn.setOnClickListener {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h)
                .setMinute(m)
                .setTitleText("Select duration")
                .build()
            picker.addOnPositiveButtonClickListener {
                totalSeconds = (picker.hour % 24) * 3600 + (picker.minute % 60) * 60 + (totalSeconds % 60)
                updateTimeButton(timeBtn)
                updateSecDisplay(secDisplay)
            }
            picker.show(childFragmentManager, "editTimePicker")
        }

        secInc.setOnClickListener {
            totalSeconds = (totalSeconds + 10).coerceAtMost(35999)
            updateTimeButton(timeBtn)
            updateSecDisplay(secDisplay)
        }

        secDec.setOnClickListener {
            totalSeconds = (totalSeconds - 10).coerceAtLeast(0)
            updateTimeButton(timeBtn)
            updateSecDisplay(secDisplay)
        }

        modeGroup.setOnCheckedChangeListener { _, id ->
            selectedMode = when (id) {
                R.id.presetModeSilent -> MODE_SILENT
                R.id.presetModeVibrate -> MODE_VIBRATE
                R.id.presetModeDnd -> MODE_DND
                else -> MODE_SILENT
            }
        }

        saveBtn.setOnClickListener {
            val label = nameInput.text?.toString()?.trim()?.ifEmpty {
                "${modeLabel(selectedMode)} ${formatDuration(totalSeconds)}"
            } ?: "${modeLabel(selectedMode)} ${formatDuration(totalSeconds)}"
            if (totalSeconds <= 0) {
                return@setOnClickListener
            }
            val newPreset = Preset(
                id = p?.id ?: java.util.UUID.randomUUID().toString(),
                label = label,
                totalSeconds = totalSeconds,
                mode = selectedMode
            )
            onSave?.invoke(newPreset)
            dismiss()
        }

        deleteBtn.setOnClickListener {
            p?.let {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete preset")
                    .setMessage("Delete \"${it.label}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        onDelete?.invoke(it)
                        dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        return view
    }

    private fun updateTimeButton(btn: MaterialButton) {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        btn.text = String.format("%02d:%02d", h, m)
    }

    private fun updateSecDisplay(tv: TextView) {
        val s = totalSeconds % 60
        tv.text = String.format("%02d", s)
    }

    fun setOnSave(listener: (Preset) -> Unit) {
        onSave = listener
    }

    fun setOnDelete(listener: (Preset) -> Unit) {
        onDelete = listener
    }

    companion object {
        private const val ARG_PRESET = "preset"

        fun newInstance(preset: Preset? = null): PresetEditDialog {
            return PresetEditDialog().apply {
                arguments = Bundle().apply {
                    if (preset != null) putSerializable(ARG_PRESET, preset)
                }
            }
        }
    }
}

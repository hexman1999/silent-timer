package com.silentapp

import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
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

    private var selectedMinutes = 0
    private var selectedMode = AudioManager.RINGER_MODE_SILENT

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_preset_edit, container, false)

        preset = arguments?.getSerializable(ARG_PRESET) as? Preset

        val nameInput = view.findViewById<TextInputEditText>(R.id.presetNameInput)
        val timeBtn = view.findViewById<MaterialButton>(R.id.presetTimeBtn)
        val modeGroup = view.findViewById<RadioGroup>(R.id.presetModeGroup)
        val saveBtn = view.findViewById<MaterialButton>(R.id.presetSaveBtn)
        val deleteBtn = view.findViewById<MaterialButton>(R.id.presetDeleteBtn)

        val p = preset
        if (p != null) {
            nameInput.setText(p.label)
            selectedMinutes = p.minutes
            selectedMode = p.mode
            modeGroup.check(
                if (p.mode == AudioManager.RINGER_MODE_SILENT) R.id.presetModeSilent
                else R.id.presetModeVibrate
            )
            deleteBtn.visibility = View.VISIBLE
        } else {
            selectedMinutes = 30
            selectedMode = AudioManager.RINGER_MODE_SILENT
            modeGroup.check(R.id.presetModeSilent)
            deleteBtn.visibility = View.GONE
        }
        updateTimeButton(timeBtn)

        timeBtn.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(selectedMinutes / 60)
                .setMinute(selectedMinutes % 60)
                .setTitleText("Select duration")
                .build()
            picker.addOnPositiveButtonClickListener {
                selectedMinutes = picker.hour * 60 + picker.minute
                updateTimeButton(timeBtn)
            }
            picker.show(childFragmentManager, "editTimePicker")
        }

        modeGroup.setOnCheckedChangeListener { _, id ->
            selectedMode = if (id == R.id.presetModeSilent)
                AudioManager.RINGER_MODE_SILENT
            else
                AudioManager.RINGER_MODE_VIBRATE
        }

        saveBtn.setOnClickListener {
            val label = nameInput.text?.toString()?.trim() ?: ""
            if (label.isEmpty()) {
                nameInput.error = "Name is required"
                return@setOnClickListener
            }
            if (selectedMinutes <= 0) {
                return@setOnClickListener
            }
            val newPreset = Preset(
                id = p?.id ?: java.util.UUID.randomUUID().toString(),
                label = label,
                minutes = selectedMinutes,
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
        val h = selectedMinutes / 60
        val m = selectedMinutes % 60
        btn.text = String.format("%02d:%02d", h, m)
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

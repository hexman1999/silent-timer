package com.silentapp

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class CustomTimerDialog : DialogFragment() {

    private var hours = 0
    private var minutes = 0
    private var seconds = 0
    private var isUntilMode = false
    private var untilHour = 0
    private var untilMinute = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_custom_timer, null)
        setupCounter(view)
        setupActions(view)
        return AlertDialog.Builder(requireActivity())
            .setView(view)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun setupCounter(view: View) {
        val txtHours = view.findViewById<TextView>(R.id.txtHours)
        val txtMinutes = view.findViewById<TextView>(R.id.txtMinutes)
        val txtSeconds = view.findViewById<TextView>(R.id.txtSeconds)

        fun updateDisplay() {
            txtHours.text = String.format("%02d", hours)
            txtMinutes.text = String.format("%02d", minutes)
            txtSeconds.text = String.format("%02d", seconds)
        }

        view.findViewById<View>(R.id.btnHourUp).setOnClickListener {
            hours = (hours + 1).coerceAtMost(99); updateDisplay()
        }
        view.findViewById<View>(R.id.btnHourDown).setOnClickListener {
            hours = (hours - 1).coerceAtLeast(0); updateDisplay()
        }
        view.findViewById<View>(R.id.btnMinUp).setOnClickListener {
            minutes = (minutes + 1).coerceAtMost(59); updateDisplay()
        }
        view.findViewById<View>(R.id.btnMinDown).setOnClickListener {
            minutes = (minutes - 1).coerceAtLeast(0); updateDisplay()
        }
        view.findViewById<View>(R.id.btnSecUp).setOnClickListener {
            seconds = (seconds + 10).coerceAtMost(59); updateDisplay()
        }
        view.findViewById<View>(R.id.btnSecDown).setOnClickListener {
            seconds = (seconds - 10).coerceAtLeast(0); updateDisplay()
        }

        fun TextView.enableDrag(
            step: Int,
            max: Int,
            getter: () -> Int,
            setter: (Int) -> Unit
        ) {
            var lastY = 0f
            var accumulated = 0f
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        lastY = event.y
                        accumulated = 0f
                        v.parent.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val delta = lastY - event.y
                        accumulated += delta
                        val threshold = 20 * resources.displayMetrics.density
                        if (kotlin.math.abs(accumulated) >= threshold) {
                            val dir = if (accumulated > 0) 1 else -1
                            val newVal = (getter() + dir * step).coerceIn(0, max)
                            setter(newVal)
                            updateDisplay()
                            accumulated = 0f
                        }
                        lastY = event.y
                        true
                    }
                    else -> false
                }
            }
        }

        txtHours.enableDrag(step = 1, max = 99, getter = { hours }, setter = { hours = it })
        txtMinutes.enableDrag(step = 1, max = 59, getter = { minutes }, setter = { minutes = it })
        txtSeconds.enableDrag(step = 10, max = 59, getter = { seconds }, setter = { seconds = it })
    }

    private fun setupActions(view: View) {
        view.findViewById<View>(R.id.btnPickTime).setOnClickListener {
            val h = hours.coerceAtMost(23)
            val m = minutes.coerceAtMost(59)
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h)
                .setMinute(m)
                .setTitleText(R.string.select_time)
                .build()
            picker.addOnPositiveButtonClickListener {
                hours = (picker.hour % 24).coerceAtMost(99)
                minutes = (picker.minute % 60).coerceAtMost(59)
                view.findViewById<TextView>(R.id.txtHours).text = String.format("%02d", hours)
                view.findViewById<TextView>(R.id.txtMinutes).text = String.format("%02d", minutes)
            }
            picker.show(parentFragmentManager, "timePicker")
        }

        view.findViewById<View>(R.id.btnUntilDialog).setOnClickListener {
            val now = Calendar.getInstance()
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .setTitleText(R.string.until)
                .build()
            picker.addOnPositiveButtonClickListener {
                isUntilMode = true
                untilHour = picker.hour % 24
                untilMinute = picker.minute % 60
                val totalSecs = computeUntilSeconds(untilHour, untilMinute)
                hours = totalSecs / 3600
                minutes = (totalSecs % 3600) / 60
                seconds = totalSecs % 60
                view.findViewById<TextView>(R.id.txtHours).text = String.format("%02d", hours)
                view.findViewById<TextView>(R.id.txtMinutes).text = String.format("%02d", minutes)
                view.findViewById<TextView>(R.id.txtSeconds).text = String.format("%02d", seconds)
            }
            picker.addOnDismissListener {
                if (isUntilMode) {
                    isUntilMode = false
                }
            }
            picker.show(parentFragmentManager, "untilPicker")
        }

        fun startTimer(mode: Int) {
            val totalSeconds = if (isUntilMode) {
                computeUntilSeconds(untilHour, untilMinute)
            } else {
                hours * 3600 + minutes * 60 + seconds
            }
            if (totalSeconds <= 0) return
            val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
            if (mode == MODE_DND && !nm.isNotificationPolicyAccessGranted) {
                RingerModeManager.requestPolicyPermission(requireContext())
                return
            }
            val intent = Intent(requireContext(), SilentTimerService::class.java).apply {
                action = SilentTimerService.ACTION_START
                putExtra(SilentTimerService.EXTRA_SECONDS, totalSeconds)
                putExtra(SilentTimerService.EXTRA_MODE, mode)
            }
            ContextCompat.startForegroundService(requireContext(), intent)
            dismiss()
        }

        view.findViewById<View>(R.id.btnSetSilent).setOnClickListener { startTimer(MODE_SILENT) }
        view.findViewById<View>(R.id.btnSetVibrate).setOnClickListener { startTimer(MODE_VIBRATE) }
        view.findViewById<View>(R.id.btnSetDnd).setOnClickListener { startTimer(MODE_DND) }
    }

    private fun computeUntilSeconds(hour: Int, minute: Int): Int {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target <= now) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return ((target.timeInMillis - now.timeInMillis) / 1000).toInt()
    }
}

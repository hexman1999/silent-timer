package com.silentapp

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var presetManager: PresetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        presetManager = PresetManager(this)

        val viewStyleGroup = findViewById<RadioGroup>(R.id.viewStyleGroup)
        val columnsGroup = findViewById<RadioGroup>(R.id.gridColumnsGroup)
        val columnsLabel = findViewById<TextView>(R.id.gridColumnsLabel)

        val currentStyle = presetManager.getViewStyle()
        val currentColumns = presetManager.getGridColumns()

        if (currentStyle == "grid") {
            viewStyleGroup.check(R.id.radioGrid)
        } else {
            viewStyleGroup.check(R.id.radioList)
        }

        when (currentColumns) {
            2 -> columnsGroup.check(R.id.radioCol2)
            4 -> columnsGroup.check(R.id.radioCol4)
            else -> columnsGroup.check(R.id.radioCol3)
        }

        updateColumnsVisibility(currentStyle == "grid", columnsLabel, columnsGroup)

        viewStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            val isGrid = checkedId == R.id.radioGrid
            presetManager.setViewStyle(if (isGrid) "grid" else "list")
            updateColumnsVisibility(isGrid, columnsLabel, columnsGroup)
        }

        columnsGroup.setOnCheckedChangeListener { _, checkedId ->
            val cols = when (checkedId) {
                R.id.radioCol2 -> 2
                R.id.radioCol4 -> 4
                else -> 3
            }
            presetManager.setGridColumns(cols)
        }
    }

    private fun updateColumnsVisibility(
        visible: Boolean,
        label: TextView,
        group: RadioGroup
    ) {
        val v = if (visible) View.VISIBLE else View.GONE
        label.visibility = v
        group.visibility = v
    }
}

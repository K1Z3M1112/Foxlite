package com.nativewinruntime

import android.app.Activity
import android.os.Bundle
import android.widget.*

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }

        val gpuSpinner = findViewById<Spinner>(R.id.spinner_gpu_driver)
        gpuSpinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.gpu_drivers, android.R.layout.simple_spinner_dropdown_item
        )
        gpuSpinner.setSelection(prefs.gpuDriverIndex)
        gpuSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                prefs.gpuDriverIndex = pos
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val dxSpinner = findViewById<Spinner>(R.id.spinner_dxwrapper)
        dxSpinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.dx_wrappers, android.R.layout.simple_spinner_dropdown_item
        )
        dxSpinner.setSelection(prefs.dxWrapperIndex)
        dxSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                prefs.dxWrapperIndex = pos
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val dynarecSwitch = findViewById<Switch>(R.id.switch_dynarec)
        dynarecSwitch.isChecked = prefs.dynarecEnabled
        dynarecSwitch.setOnCheckedChangeListener { _, checked -> prefs.dynarecEnabled = checked }

        val resSeek = findViewById<SeekBar>(R.id.seek_resolution)
        val resLabel = findViewById<TextView>(R.id.label_resolution)
        // SeekBar range 0-100 maps to 50%-150% resolution scale.
        resSeek.progress = prefs.resolutionScale - 50
        resLabel.text = "${prefs.resolutionScale}%"
        resSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = progress + 50
                resLabel.text = "$pct%"
                prefs.resolutionScale = pct
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val consoleSwitch = findViewById<Switch>(R.id.switch_console)
        consoleSwitch.isChecked = prefs.showConsole
        consoleSwitch.setOnCheckedChangeListener { _, checked -> prefs.showConsole = checked }

        val rotationSwitch = findViewById<Switch>(R.id.switch_rotation)
        rotationSwitch.isChecked = prefs.allowRotation
        rotationSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.allowRotation = checked
            requestedOrientation = if (checked)
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }
}

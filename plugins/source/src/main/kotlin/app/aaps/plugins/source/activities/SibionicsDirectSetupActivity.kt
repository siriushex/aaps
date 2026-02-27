package app.aaps.plugins.source.activities

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.source.R
import app.aaps.plugins.source.keys.SibionicsDirectBooleanKey
import app.aaps.plugins.source.keys.SibionicsDirectIntKey
import app.aaps.plugins.source.keys.SibionicsDirectStringKey
import app.aaps.plugins.source.sibionics.SibionicsDirectCodec
import java.util.Locale
import javax.inject.Inject

class SibionicsDirectSetupActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sibionics_direct_setup)

        val enabled = findViewById<Switch>(R.id.sibionicsDirectEnabled)
        val subtype = findViewById<Spinner>(R.id.sibionicsDirectSubtype)
        val deviceName = findViewById<EditText>(R.id.sibionicsDirectDeviceName)
        val deviceAddress = findViewById<EditText>(R.id.sibionicsDirectDeviceAddress)
        val pollInterval = findViewById<EditText>(R.id.sibionicsDirectPollInterval)
        val nextIndex = findViewById<EditText>(R.id.sibionicsDirectNextIndex)
        val transmitterCode = findViewById<EditText>(R.id.sibionicsDirectTransmitterCode)
        val resetRequested = findViewById<CheckBox>(R.id.sibionicsDirectResetRequested)
        val status = findViewById<TextView>(R.id.sibionicsDirectStatus)
        val parse = findViewById<Button>(R.id.sibionicsDirectParseCode)
        val save = findViewById<Button>(R.id.sibionicsDirectSave)
        val cancel = findViewById<Button>(R.id.sibionicsDirectCancel)

        val subtypeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.sibionics_direct_subtypes,
            android.R.layout.simple_spinner_item
        )
        subtypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subtype.adapter = subtypeAdapter

        enabled.isChecked = preferences.get(SibionicsDirectBooleanKey.Enabled)
        subtype.setSelection(preferences.get(SibionicsDirectIntKey.Subtype).coerceIn(0, 3), false)
        deviceName.setText(preferences.get(SibionicsDirectStringKey.DeviceName))
        deviceAddress.setText(preferences.get(SibionicsDirectStringKey.DeviceAddress))
        pollInterval.setText(preferences.get(SibionicsDirectIntKey.PollIntervalSeconds).toString())
        nextIndex.setText(preferences.get(SibionicsDirectIntKey.NextIndex).toString())
        resetRequested.isChecked = preferences.get(SibionicsDirectBooleanKey.ResetRequested)

        val updateStatus: () -> Unit = {
            val mode = if (enabled.isChecked) getString(R.string.sibionics_direct_enabled_status) else getString(R.string.sibionics_direct_disabled_status)
            status.text = getString(
                R.string.sibionics_direct_status_template,
                mode,
                subtype.selectedItem.toString()
            )
        }
        updateStatus()

        parse.setOnClickListener {
            val scanned = transmitterCode.text.toString().trim()
            val parsedName = SibionicsDirectCodec.extractTransmitterName(scanned)
            if (parsedName == null) {
                Toast.makeText(this, R.string.sibionics_direct_invalid_code, Toast.LENGTH_SHORT).show()
            } else {
                deviceName.setText(parsedName)
                Toast.makeText(this, getString(R.string.sibionics_direct_code_applied, parsedName), Toast.LENGTH_SHORT).show()
            }
        }

        save.setOnClickListener {
            val poll = pollInterval.text.toString().toIntOrNull()?.coerceIn(15, 300) ?: 60
            val index = nextIndex.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0

            preferences.put(SibionicsDirectBooleanKey.Enabled, enabled.isChecked)
            preferences.put(SibionicsDirectIntKey.Subtype, subtype.selectedItemPosition.coerceIn(0, 3))
            preferences.put(SibionicsDirectStringKey.DeviceName, deviceName.text.toString().trim())
            preferences.put(SibionicsDirectStringKey.DeviceAddress, deviceAddress.text.toString().trim().uppercase(Locale.US))
            preferences.put(SibionicsDirectIntKey.PollIntervalSeconds, poll)
            preferences.put(SibionicsDirectIntKey.NextIndex, index)
            preferences.put(SibionicsDirectBooleanKey.ResetRequested, resetRequested.isChecked)

            Toast.makeText(this, R.string.sibionics_direct_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        cancel.setOnClickListener { finish() }
    }
}

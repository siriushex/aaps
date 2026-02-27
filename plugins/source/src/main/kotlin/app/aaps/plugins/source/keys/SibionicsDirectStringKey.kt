package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class SibionicsDirectStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    DeviceName("sibionics_direct_device_name", ""),
    DeviceAddress("sibionics_direct_device_address", "")
}

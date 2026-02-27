package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

enum class SibionicsDirectBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanNonPreferenceKey {

    Enabled("sibionics_direct_enabled", false),
    ResetRequested("sibionics_direct_reset_requested", false)
}

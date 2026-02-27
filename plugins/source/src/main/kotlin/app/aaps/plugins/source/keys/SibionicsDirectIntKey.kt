package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class SibionicsDirectIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true
) : IntNonPreferenceKey {

    Subtype("sibionics_direct_subtype", 3),
    PollIntervalSeconds("sibionics_direct_poll_interval_seconds", 60),
    NextIndex("sibionics_direct_next_index", 0)
}

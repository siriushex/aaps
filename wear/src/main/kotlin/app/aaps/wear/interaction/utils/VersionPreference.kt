package app.aaps.wear.interaction.utils

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import app.aaps.wear.BuildConfig
import app.aaps.wear.preference.WearListPreference

/**
 * Preference for displaying the app version
 */
@Suppress("unused")
class VersionPreference(context: Context, attrs: AttributeSet?) : WearListPreference(context, attrs) {

    override fun getSummaryText(context: Context): CharSequence {
        return BuildConfig.BUILDVERSION
    }

    override fun onPreferenceClick(context: Context) {
        Toast.makeText(context, "Build version:" + BuildConfig.BUILDVERSION, Toast.LENGTH_LONG).show()
    }

    init {
        setEntries(arrayOf<CharSequence>(BuildConfig.BUILDVERSION))
        setEntryValues(arrayOf<CharSequence>(BuildConfig.BUILDVERSION))
    }
}

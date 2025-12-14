/*
 * Simplified replacement for denley/WearPreferenceActivity WearListPreference
 * Original: https://github.com/denley/WearPreferenceActivity
 * License: Apache 2.0
 *
 * Adapted for AndroidAPS - minimal implementation with only used functionality
 */

package app.aaps.wear.preference

import android.content.Context
import android.preference.ListPreference
import android.util.AttributeSet

/**
 * Simplified WearListPreference for Wear OS
 * Uses deprecated ListPreference to match WearPreferenceActivity
 * Only implements the methods and properties that are actually used in the codebase
 */
@Suppress("DEPRECATION")
abstract class WearListPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null
) : ListPreference(context, attrs) {

    /**
     * Get the summary text for this preference
     * Subclasses override this to provide custom summary text
     */
    abstract fun getSummary(context: Context): CharSequence

    /**
     * Called when the preference is clicked
     * Subclasses override this to handle clicks
     */
    abstract fun onPreferenceClick(context: Context)

    init {
        // Update summary
        summary = context?.let { getSummary(it) }
    }

    override fun onClick() {
        context?.let { onPreferenceClick(it) }
    }
}

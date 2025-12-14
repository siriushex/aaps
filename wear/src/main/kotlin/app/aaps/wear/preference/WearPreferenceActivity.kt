/*
 * Simplified replacement for denley/WearPreferenceActivity
 * Original: https://github.com/denley/WearPreferenceActivity
 * License: Apache 2.0
 *
 * Adapted for AndroidAPS - minimal implementation with only used functionality
 */

package app.aaps.wear.preference

import android.preference.PreferenceActivity

/**
 * Simplified WearPreferenceActivity for Wear OS
 * Uses deprecated PreferenceActivity to avoid AppCompat theme requirements on Wear OS
 * The addPreferencesFromResource() method is inherited from PreferenceActivity
 */
@Suppress("DEPRECATION")
abstract class WearPreferenceActivity : PreferenceActivity()

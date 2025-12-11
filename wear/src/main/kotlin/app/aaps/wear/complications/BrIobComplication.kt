@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.interaction.utils.DisplayFormat
import app.aaps.wear.interaction.utils.SmallestDoubleString
import dagger.android.AndroidInjection

/**
 * Basal Rate + IOB Complication
 *
 * Shows insulin on board (IOB) and basal rate
 * Text: IOB value (minimized to fit)
 * Title: Basal rate with symbol
 *
 */
class BrIobComplication : ModernBaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(
        dataType: Int,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        val statusData = data.statusData

        return when (dataType) {
            ComplicationData.TYPE_SHORT_TEXT -> {
                val iob = SmallestDoubleString(statusData.iobSum, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MIN_FIELD_LEN_IOB)

                ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(iob))
                    .setShortTitle(ComplicationText.plainText(displayFormat.basalRateSymbol() + statusData.currentBasal))
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = BrIobComplication::class.java.canonicalName!!
}
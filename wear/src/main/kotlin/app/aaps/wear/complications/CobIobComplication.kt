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
 * COB+IOB Complication
 *
 * Shows both carbs on board (COB) and insulin on board (IOB)
 * Text: COB value
 * Title: IOB value (minimized to fit)
 *
 */
class CobIobComplication : ModernBaseComplicationProviderService() {

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
                val cob = statusData.cob
                val iob = SmallestDoubleString(statusData.iobSum, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MAX_FIELD_LEN_SHORT)

                ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(cob))
                    .setShortTitle(ComplicationText.plainText(iob))
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = CobIobComplication::class.java.canonicalName!!
}

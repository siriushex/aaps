@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection

/**
 * SGV (Sensor Glucose Value) Complication
 *
 * Shows current blood glucose with arrow and delta/time
 *
 */
class SgvComplication : ModernBaseComplicationProviderService() {

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
        // Use dataset 0 (primary)
        val bgData = data.bgData
        aapsLogger.debug(LTag.WEAR, "SgvComplication building: dataset=0 sgv=${bgData.sgvString} arrow=${bgData.slopeArrow}")

        return when (dataType) {
            ComplicationData.TYPE_SHORT_TEXT -> {
                val shortText = bgData.sgvString + bgData.slopeArrow + "\uFE0E"

                val shortTitle = ComplicationText.TimeDifferenceBuilder()
                    .setReferencePeriodStart(bgData.timeStamp)
                    .setReferencePeriodEnd(bgData.timeStamp + 60000)
                    .setMinimumUnit(java.util.concurrent.TimeUnit.MINUTES)
                    .setStyle(ComplicationText.DIFFERENCE_STYLE_STOPWATCH)
                    .setShowNowText(false)
                    .build()

                ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(shortText))
                    .setShortTitle(shortTitle)
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "SgvComplication unexpected type: $dataType")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = SgvComplication::class.java.canonicalName!!
}
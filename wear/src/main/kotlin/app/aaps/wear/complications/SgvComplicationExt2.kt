@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection

/**
 * SGV Extended Complication 2
 *
 * Shows glucose data from AAPSClient2 (dataset 2)
 * Used in follower/caregiver mode to monitor a third patient
 *
 */
class SgvComplicationExt2 : ModernBaseComplicationProviderService() {

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
        // Use dataset 2 (AAPSClient2)
        val bgData2 = data.bgData2
        aapsLogger.debug(LTag.WEAR, "SgvComplicationExt2 building: dataset=2 sgv=${bgData2.sgvString} arrow=${bgData2.slopeArrow}")

        return when (dataType) {
            ComplicationData.TYPE_SHORT_TEXT -> {
                val shortText = bgData2.sgvString + bgData2.slopeArrow + "\uFE0E"

                val shortTitle = ComplicationText.TimeDifferenceBuilder()
                    .setReferencePeriodStart(bgData2.timeStamp)
                    .setReferencePeriodEnd(bgData2.timeStamp + 60000)
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
                aapsLogger.warn(LTag.WEAR, "SgvComplicationExt2 unexpected type: $dataType")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = SgvComplicationExt2::class.java.canonicalName!!
}
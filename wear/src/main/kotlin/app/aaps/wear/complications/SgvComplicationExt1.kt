@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection

/**
 * SGV Extended Complication 1
 *
 * Shows glucose data from AAPSClient1 (dataset 1)
 * Used in follower/caregiver mode to monitor a second patient
 *
 */
class SgvComplicationExt1 : ModernBaseComplicationProviderService() {

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
        // Use dataset 1 (AAPSClient1)
        val bgData1 = data.bgData1
        aapsLogger.debug(LTag.WEAR, "SgvComplicationExt1 building: dataset=1 sgv=${bgData1.sgvString} arrow=${bgData1.slopeArrow}")

        return when (dataType) {
            ComplicationData.TYPE_SHORT_TEXT -> {
                val shortText = bgData1.sgvString + bgData1.slopeArrow + "\uFE0E"

                val shortTitle = ComplicationText.TimeDifferenceBuilder()
                    .setReferencePeriodStart(bgData1.timeStamp)
                    .setReferencePeriodEnd(bgData1.timeStamp + 60000)
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
                aapsLogger.warn(LTag.WEAR, "SgvComplicationExt1 unexpected type: $dataType")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = SgvComplicationExt1::class.java.canonicalName!!
}
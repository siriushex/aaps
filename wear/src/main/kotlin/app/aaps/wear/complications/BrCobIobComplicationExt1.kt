@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.interaction.utils.DisplayFormat
import app.aaps.wear.interaction.utils.SmallestDoubleString
import dagger.android.AndroidInjection
import kotlin.math.max

/**
 * Basal Rate + COB + IOB Extended Complication 1
 *
 * Shows basal rate, carbs on board (COB), and insulin on board (IOB) from AAPSClient1 (dataset 1)
 * Used in follower/caregiver mode to monitor a second patient
 * Text: Basal rate with symbol
 * Title: COB and IOB values (both minimized to fit)
 *
 */
class BrCobIobComplicationExt1 : ModernBaseComplicationProviderService() {

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
        val statusData1 = data.statusData1

        return when (dataType) {
            ComplicationData.TYPE_SHORT_TEXT -> {
                val cob = SmallestDoubleString(statusData1.cob, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MIN_FIELD_LEN_COB)
                val iob = SmallestDoubleString(statusData1.iobSum, SmallestDoubleString.Units.USE).minimise(max(DisplayFormat.MIN_FIELD_LEN_IOB, DisplayFormat.MAX_FIELD_LEN_SHORT - 1 - cob.length))

                ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(displayFormat.basalRateSymbol() + statusData1.currentBasal))
                    .setShortTitle(ComplicationText.plainText("$cob $iob"))
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = BrCobIobComplicationExt1::class.java.canonicalName!!
}

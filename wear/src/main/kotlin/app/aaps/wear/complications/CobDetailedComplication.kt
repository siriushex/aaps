@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.data.RawDisplayData
import dagger.android.AndroidInjection

/**
 * COB Detailed Complication
 *
 * Shows detailed carbs on board (COB) information
 * Displays both total COB and additional detail if space permits
 * Tap action opens carb/wizard dialog
 *
 */
class CobDetailedComplication : ModernBaseComplicationProviderService() {

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
                // Create RawDisplayData for compatibility with existing displayFormat methods
                val raw = RawDisplayData()
                raw.status[0] = EventData.Status(
                    dataset = 0,
                    externalStatus = statusData.externalStatus,
                    iobSum = statusData.iobSum,
                    iobDetail = statusData.iobDetail,
                    cob = statusData.cob,
                    currentBasal = statusData.currentBasal,
                    battery = statusData.battery,
                    rigBattery = statusData.rigBattery,
                    openApsStatus = statusData.openApsStatus,
                    bgi = statusData.bgi,
                    batteryLevel = statusData.batteryLevel,
                    patientName = statusData.patientName,
                    tempTarget = statusData.tempTarget,
                    tempTargetLevel = statusData.tempTargetLevel,
                    reservoirString = statusData.reservoirString,
                    reservoir = statusData.reservoir,
                    reservoirLevel = statusData.reservoirLevel
                )

                val cob = displayFormat.detailedCob(raw, 0)
                val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(cob.first))
                    .setTapAction(complicationPendingIntent)
                if (cob.second.isNotEmpty()) {
                    builder.setShortTitle(ComplicationText.plainText(cob.second))
                }
                builder.build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = CobDetailedComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.WIZARD
}
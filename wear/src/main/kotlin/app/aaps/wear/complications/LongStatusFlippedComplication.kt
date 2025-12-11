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
 * Long Status Flipped Complication
 *
 * Shows comprehensive glucose and status information in long text format (flipped layout)
 * Title: COB, IOB, and basal rate
 * Text: Glucose value, arrow, delta, and time
 *
 */
class LongStatusFlippedComplication : ModernBaseComplicationProviderService() {

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
        val bgData = data.bgData
        val statusData = data.statusData

        return when (dataType) {
            ComplicationData.TYPE_LONG_TEXT -> {
                // Create RawDisplayData for compatibility with existing displayFormat methods
                val raw = RawDisplayData()
                raw.singleBg[0] = EventData.SingleBg(
                    dataset = 0,
                    timeStamp = bgData.timeStamp,
                    sgvString = bgData.sgvString,
                    glucoseUnits = bgData.glucoseUnits,
                    slopeArrow = bgData.slopeArrow,
                    delta = bgData.delta,
                    deltaDetailed = bgData.deltaDetailed,
                    avgDelta = bgData.avgDelta,
                    avgDeltaDetailed = bgData.avgDeltaDetailed,
                    sgvLevel = bgData.sgvLevel,
                    sgv = bgData.sgv,
                    high = bgData.high,
                    low = bgData.low,
                    color = bgData.color,
                    deltaMgdl = null,
                    avgDeltaMgdl = null
                )
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

                val glucoseLine = displayFormat.longGlucoseLine(raw, 0)
                val detailsLine = displayFormat.longDetailsLine(raw, 0)

                ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                    .setLongTitle(ComplicationText.plainText(detailsLine))
                    .setLongText(ComplicationText.plainText(glucoseLine))
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                            -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = LongStatusFlippedComplication::class.java.canonicalName!!
}
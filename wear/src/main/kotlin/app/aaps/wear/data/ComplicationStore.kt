package app.aaps.wear.data

import kotlinx.serialization.Serializable

/**
 * Data models for DataStore-based persistence
 * Using Kotlin Serialization with ProtoBuf format for efficient storage
 *
 */

@Serializable
data class ComplicationData(
    val bgData: BgData = BgData(),           // Dataset 0 - Primary
    val bgData1: BgData = BgData(),          // Dataset 1 - AAPSClient1
    val bgData2: BgData = BgData(),          // Dataset 2 - AAPSClient2
    val statusData: StatusData = StatusData(),     // Dataset 0 - Primary
    val statusData1: StatusData = StatusData(),    // Dataset 1 - AAPSClient1
    val statusData2: StatusData = StatusData(),    // Dataset 2 - AAPSClient2
    val lastUpdateTimestamp: Long = 0L
)

@Serializable
data class BgData(
    val timeStamp: Long = 0L,
    val sgvString: String = "---",
    val glucoseUnits: String = "-",
    val slopeArrow: String = "--",
    val delta: String = "--",
    val deltaDetailed: String = "--",
    val avgDelta: String = "--",
    val avgDeltaDetailed: String = "--",
    val sgvLevel: Long = 0L,
    val sgv: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val color: Int = 0
)

@Serializable
data class StatusData(
    val externalStatus: String = "no status",
    val iobSum: String = "IOB",
    val iobDetail: String = "-.--",
    val cob: String = "--g",
    val currentBasal: String = "-.--U/h",
    val battery: String = "--",
    val rigBattery: String = "--",
    val openApsStatus: Long = -1L,
    val bgi: String = "--",
    val batteryLevel: Int = 1,
    val patientName: String = "",
    val tempTarget: String = "--",
    val tempTargetLevel: Int = 0,
    val reservoirString: String = "--",
    val reservoir: Double = 0.0,
    val reservoirLevel: Int = 0
)

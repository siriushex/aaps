package app.aaps.plugins.source.sibionics

import java.util.UUID
import kotlin.math.roundToInt

internal object SibionicsDirectCodec {

    val serviceUuid: UUID = UUID.fromString("0000ff30-0000-1000-8000-00805f9b34fb")
    val notifyCharacteristicUuid: UUID = UUID.fromString("0000ff31-0000-1000-8000-00805f9b34fb")
    val writeCharacteristicUuid: UUID = UUID.fromString("0000ff32-0000-1000-8000-00805f9b34fb")
    val notificationDescriptorUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private const val sibionicsRecognition = "0697283164"
    private val authChallenge = byteArrayOf(0x23, 0xF7.toByte(), 0x6F, 0xD9.toByte(), 0xF4.toByte())

    enum class Status {
        NONE,
        DATA,
        AUTH_REQUIRED,
        RETRY,
        INVALID_PACKET
    }

    data class GlucoseRecord(
        val index: Int,
        val timestampMillis: Long,
        val valueMgdl: Double,
        val trendRate: Double
    )

    data class ParseResult(
        val status: Status,
        val records: List<GlucoseRecord> = emptyList()
    )

    fun isLikelyTransmitterCode(code: String): Boolean =
        code.length == 59 && code.contains(sibionicsRecognition)

    fun extractTransmitterName(code: String): String? =
        if (isLikelyTransmitterCode(code)) code.takeLast(10) else null

    fun isAuthChallenge(payload: ByteArray): Boolean = payload.contentEquals(authChallenge)

    fun buildAskDataCommand(index: Int, macAddress: String): ByteArray? {
        val reversedAddress = reverseMac(macAddress) ?: return null
        val payload = ByteArray(20)
        payload[0] = 0xAA.toByte()
        payload[1] = 0x55
        payload[2] = 0x07
        payload[3] = (index and 0xFF).toByte()
        payload[4] = ((index ushr 8) and 0xFF).toByte()
        reversedAddress.copyInto(payload, destinationOffset = 5)
        // bytes [11..18] stay 0
        payload[19] = checksum(payload, payload.size - 1)
        return payload
    }

    fun parseChineseNotification(
        payload: ByteArray,
        nowSeconds: Long
    ): ParseResult {
        if (isAuthChallenge(payload)) return ParseResult(status = Status.AUTH_REQUIRED)
        if (payload.size < 5) return ParseResult(status = Status.INVALID_PACKET)
        if (payload[0] != 0xAA.toByte() || payload[1] != 0x55.toByte() || payload[2].toInt() != 9) {
            return ParseResult(status = Status.INVALID_PACKET)
        }

        val expectedChecksum = checksum(payload, payload.size - 1)
        if (expectedChecksum != payload.last()) return ParseResult(status = Status.INVALID_PACKET)

        val groups = payload[3].toInt() and 0xFF
        val requiredSize = 4 + groups * 14 + 1
        if (groups <= 0 || payload.size < requiredSize) return ParseResult(status = Status.RETRY)

        val records = ArrayList<GlucoseRecord>(groups)
        var offset = 4
        repeat(groups) {
            val index = readU16BE(payload, offset)
            val glucoseMmol = readU16BE(payload, offset + 6) / 10.0
            val numUnreceived = readU16BE(payload, offset + 10)
            val addTime = readU16BE(payload, offset + 12)

            val offsetSeconds = addTime - (numUnreceived * 60)
            val eventSeconds = if (offsetSeconds > 0) nowSeconds else nowSeconds + offsetSeconds
            if (glucoseMmol in 1.8..30.0) {
                records += GlucoseRecord(
                    index = index,
                    timestampMillis = eventSeconds * 1000L,
                    valueMgdl = (glucoseMmol * 18.0 * 10.0).roundToInt() / 10.0,
                    trendRate = 0.0
                )
            }
            offset += 14
        }

        return if (records.isEmpty()) ParseResult(Status.NONE) else ParseResult(Status.DATA, records)
    }

    private fun readU16BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun reverseMac(macAddress: String): ByteArray? {
        val parts = macAddress.split(":")
        if (parts.size != 6) return null
        return try {
            ByteArray(6).also { out ->
                for (i in 0..5) out[i] = parts[5 - i].toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun checksum(data: ByteArray, length: Int): Byte {
        var sum = 0
        for (i in 0 until length) sum += data[i].toInt() and 0xFF
        return (((sum and 0xFF).inv() + 1) and 0xFF).toByte()
    }
}

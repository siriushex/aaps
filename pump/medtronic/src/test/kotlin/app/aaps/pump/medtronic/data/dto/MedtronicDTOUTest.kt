package app.aaps.pump.medtronic.data.dto

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.defs.BatteryType
import app.aaps.pump.medtronic.defs.PumpBolusType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

/**
 * Tests for Medtronic DTO classes
 */
class MedtronicDTOUTest : MedtronicTestBase() {

    @Mock
    lateinit var aapsLogger: AAPSLogger

    @BeforeEach
    fun setup() {
        super.initializeCommonMocks()
    }

    // BolusDTO Tests
    @Test
    fun `test BolusDTO with normal bolus`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 5.0,
            deliveredAmount = 5.0,
            duration = 0
        )
        bolusDTO.bolusType = PumpBolusType.Normal

        assertThat(bolusDTO.requestedAmount).isWithin(0.01).of(5.0)
        assertThat(bolusDTO.deliveredAmount).isWithin(0.01).of(5.0)
        assertThat(bolusDTO.duration).isEqualTo(0)
        assertThat(bolusDTO.bolusType).isEqualTo(PumpBolusType.Normal)
        assertThat(bolusDTO.value).isEqualTo("5.00")
    }

    @Test
    fun `test BolusDTO with extended bolus`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 8.0,
            deliveredAmount = 8.0,
            duration = 90 // 90 minutes = 1:30
        )
        bolusDTO.bolusType = PumpBolusType.Extended

        assertThat(bolusDTO.bolusType).isEqualTo(PumpBolusType.Extended)
        assertThat(bolusDTO.value).contains("AMOUNT_SQUARE=8.00")
        assertThat(bolusDTO.value).contains("DURATION=01:30")
    }

    @Test
    fun `test BolusDTO with multiwave bolus`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 10.0,
            deliveredAmount = 6.0,
            duration = 120 // 2 hours
        )
        bolusDTO.bolusType = PumpBolusType.Multiwave
        bolusDTO.immediateAmount = 4.0

        assertThat(bolusDTO.immediateAmount).isWithin(0.01).of(4.0)
        assertThat(bolusDTO.value).contains("AMOUNT=4.00")
        assertThat(bolusDTO.value).contains("AMOUNT_SQUARE=6.00")
        assertThat(bolusDTO.value).contains("DURATION=02:00")
    }

    @Test
    fun `test BolusDTO displayable value formatting`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 8.0,
            deliveredAmount = 8.0,
            duration = 60
        )
        bolusDTO.bolusType = PumpBolusType.Extended

        val displayable = bolusDTO.displayableValue

        assertThat(displayable).contains("Amount Square:")
        assertThat(displayable).contains("Duration:")
        assertThat(displayable).doesNotContain("AMOUNT_SQUARE=")
        assertThat(displayable).doesNotContain("DURATION=")
    }

    @Test
    fun `test BolusDTO toString`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 5.0,
            deliveredAmount = 5.0
        )
        bolusDTO.bolusType = PumpBolusType.Audio

        assertThat(bolusDTO.toString()).contains("BolusDTO")
        assertThat(bolusDTO.toString()).contains("Audio")
    }

    // TempBasalPair Tests
    @Test
    fun `test TempBasalPair with absolute rate from single byte`() {
        // Rate byte 0x50 = 80, 80 * 0.025 = 2.0 U/hr
        // Time byte 0x04 = 4, 4 * 30 = 120 minutes
        val tempBasal = TempBasalPair(0x50.toByte(), 4, isPercent = false)

        assertThat(tempBasal.insulinRate).isWithin(0.01).of(2.0)
        assertThat(tempBasal.durationMinutes).isEqualTo(120)
        assertThat(tempBasal.isPercent).isFalse()
        assertThat(tempBasal.description).contains("Rate: 2.000 U")
        assertThat(tempBasal.description).contains("Duration: 120 min")
    }

    @Test
    fun `test TempBasalPair with percent rate`() {
        // Rate byte 0x96 = 150%
        // Time byte 0x02 = 2, 2 * 30 = 60 minutes
        val tempBasal = TempBasalPair(0x96.toByte(), 2, isPercent = true)

        assertThat(tempBasal.insulinRate).isWithin(0.01).of(150.0)
        assertThat(tempBasal.durationMinutes).isEqualTo(60)
        assertThat(tempBasal.isPercent).isTrue()
        assertThat(tempBasal.description).contains("Rate: 150%")
    }

    @Test
    fun `test TempBasalPair with two byte rate`() {
        // For 40-stroke pumps: rate from two bytes
        val tempBasal = TempBasalPair(0x32.toByte(), 0x00.toByte(), 6, isPercent = false)

        assertThat(tempBasal.insulinRate).isWithin(0.01).of(1.25) // 0x0032 = 50, 50 * 0.025 = 1.25
        assertThat(tempBasal.durationMinutes).isEqualTo(180) // 6 * 30 = 180 minutes
        assertThat(tempBasal.isPercent).isFalse()
    }

    @Test
    fun `test TempBasalPair from pump response with absolute rate`() {
        // Response: [type, percent_value, stroke_hi, stroke_lo, duration_hi, duration_lo]
        // Type 0x00 = absolute, strokes 0x0050 = 80, 80/40 = 2.0 U/hr, duration 0x0078 = 120 min
        val response = ByteUtil.createByteArrayFromString("00 00 00 50 00 78")

        val tempBasal = TempBasalPair(aapsLogger, response)

        assertThat(tempBasal.isPercent).isFalse()
        assertThat(tempBasal.insulinRate).isWithin(0.01).of(2.0)
        assertThat(tempBasal.durationMinutes).isEqualTo(120)
    }

    @Test
    fun `test TempBasalPair from pump response with percent rate`() {
        // Response: [type, percent_value, stroke_hi, stroke_lo, duration]
        // Type 0x01 = percent, value 200%, duration 60 min
        val response = ByteUtil.createByteArrayFromString("01 C8 00 00 3C")

        val tempBasal = TempBasalPair(aapsLogger, response)

        assertThat(tempBasal.isPercent).isTrue()
        assertThat(tempBasal.insulinRate).isWithin(0.01).of(200.0)
        assertThat(tempBasal.durationMinutes).isEqualTo(60)
    }

    @Test
    fun `test TempBasalPair cancel TBR detection`() {
        val cancelTBR = TempBasalPair(0.0, false, 0)

        assertThat(cancelTBR.isCancelTBR).isTrue()
        assertThat(cancelTBR.isZeroTBR).isFalse()
        assertThat(cancelTBR.description).isEqualTo("Cancel TBR")
    }

    @Test
    fun `test TempBasalPair zero TBR detection`() {
        val zeroTBR = TempBasalPair(0.0, false, 30)

        assertThat(zeroTBR.isZeroTBR).isTrue()
        assertThat(zeroTBR.isCancelTBR).isFalse()
    }

    @Test
    fun `test TempBasalPair toString`() {
        val tempBasal = TempBasalPair(1.5, false, 90)

        val str = tempBasal.toString()

        assertThat(str).contains("TempBasalPair")
        assertThat(str).contains("Rate=1.5")
        assertThat(str).contains("DurationMinutes=90")
        assertThat(str).contains("IsPercent=false")
    }

    // BatteryStatusDTO Tests
    @Test
    fun `test BatteryStatusDTO with normal status and voltage`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal
        batteryStatus.voltage = 1.5
        batteryStatus.extendedDataReceived = true

        assertThat(batteryStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Normal)
        assertThat(batteryStatus.voltage).isWithin(0.01).of(1.5)
        assertThat(batteryStatus.extendedDataReceived).isTrue()
    }

    @Test
    fun `test BatteryStatusDTO calculated percent for alkaline battery`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = 1.35 // Mid-range for alkaline
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val percent = batteryStatus.getCalculatedPercent(BatteryType.Alkaline)

        // Alkaline: lowVoltage=1.20, highVoltage=1.50
        // Expected: (1.35 - 1.20) / (1.50 - 1.20) = 0.15 / 0.30 = 0.5 = 50%
        assertThat(percent).isEqualTo(50)
    }

    @Test
    fun `test BatteryStatusDTO calculated percent for lithium battery`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = 1.65
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val percent = batteryStatus.getCalculatedPercent(BatteryType.Lithium)

        // Lithium: lowVoltage=1.30, highVoltage=1.70
        // Expected: (1.65 - 1.30) / (1.70 - 1.30) = 0.35 / 0.40 = 0.875 = 87%
        assertThat(percent).isAtLeast(85)
        assertThat(percent).isAtMost(90)
    }

    @Test
    fun `test BatteryStatusDTO percent clamped to 1-100 range`() {
        val batteryStatus = BatteryStatusDTO()

        // Test voltage below range (should return 1%)
        batteryStatus.voltage = 0.5
        assertThat(batteryStatus.getCalculatedPercent(BatteryType.Alkaline)).isEqualTo(1)

        // Test voltage above range (should return 100%)
        batteryStatus.voltage = 2.0
        assertThat(batteryStatus.getCalculatedPercent(BatteryType.Alkaline)).isEqualTo(100)
    }

    @Test
    fun `test BatteryStatusDTO with null voltage returns default percent`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = null
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val percent = batteryStatus.getCalculatedPercent(BatteryType.Alkaline)

        assertThat(percent).isEqualTo(70) // Default for Normal status
    }

    @Test
    fun `test BatteryStatusDTO with low status and null voltage`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = null
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Low

        val percent = batteryStatus.getCalculatedPercent(BatteryType.Alkaline)

        assertThat(percent).isEqualTo(18) // Default for Low status
    }

    @Test
    fun `test BatteryStatusDTO with None battery type returns default`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = 1.5
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val percent = batteryStatus.getCalculatedPercent(BatteryType.None)

        assertThat(percent).isEqualTo(70) // Default when battery type is None
    }

    @Test
    fun `test BatteryStatusDTO toString includes all battery types`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = 1.4
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val str = batteryStatus.toString()

        assertThat(str).contains("BatteryStatusDTO")
        assertThat(str).contains("voltage=1.40")
        assertThat(str).contains("alkaline=")
        assertThat(str).contains("lithium=")
        assertThat(str).contains("niZn=")
        assertThat(str).contains("nimh=")
    }

    @Test
    fun `test BatteryStatusDTO with all battery status types`() {
        val normalStatus = BatteryStatusDTO()
        normalStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal
        assertThat(normalStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Normal)

        val lowStatus = BatteryStatusDTO()
        lowStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Low
        assertThat(lowStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Low)

        val unknownStatus = BatteryStatusDTO()
        unknownStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Unknown
        assertThat(unknownStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Unknown)
    }
}

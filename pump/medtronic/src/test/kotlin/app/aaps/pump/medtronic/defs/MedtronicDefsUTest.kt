package app.aaps.pump.medtronic.defs

import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.comm.message.PacketType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for Medtronic enum and definition classes
 */
class MedtronicDefsUTest : MedtronicTestBase() {

    // MedtronicDeviceType Tests
    @Test
    fun `test MedtronicDeviceType getByDescription with valid model`() {
        val device522 = MedtronicDeviceType.getByDescription("522")
        val device722 = MedtronicDeviceType.getByDescription("722")
        val device554 = MedtronicDeviceType.getByDescription("554")

        assertThat(device522).isEqualTo(MedtronicDeviceType.Medtronic_522)
        assertThat(device722).isEqualTo(MedtronicDeviceType.Medtronic_722)
        assertThat(device554).isEqualTo(MedtronicDeviceType.Medtronic_554_Veo)
    }

    @Test
    fun `test MedtronicDeviceType getByDescription with unknown model`() {
        val unknownDevice = MedtronicDeviceType.getByDescription("999")

        assertThat(unknownDevice).isEqualTo(MedtronicDeviceType.Unknown_Device)
    }

    @Test
    fun `test MedtronicDeviceType isSameDevice with single device`() {
        val result = MedtronicDeviceType.isSameDevice(
            MedtronicDeviceType.Medtronic_522,
            MedtronicDeviceType.Medtronic_522
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `test MedtronicDeviceType isSameDevice with family match`() {
        val result = MedtronicDeviceType.isSameDevice(
            MedtronicDeviceType.Medtronic_522,
            MedtronicDeviceType.Medtronic_522_722
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `test MedtronicDeviceType isSameDevice with family no match`() {
        val result = MedtronicDeviceType.isSameDevice(
            MedtronicDeviceType.Medtronic_511,
            MedtronicDeviceType.Medtronic_522_722
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `test MedtronicDeviceType isSameDevice with 523andHigher family`() {
        assertThat(
            MedtronicDeviceType.isSameDevice(
                MedtronicDeviceType.Medtronic_523_Revel,
                MedtronicDeviceType.Medtronic_523andHigher
            )
        ).isTrue()

        assertThat(
            MedtronicDeviceType.isSameDevice(
                MedtronicDeviceType.Medtronic_554_Veo,
                MedtronicDeviceType.Medtronic_523andHigher
            )
        ).isTrue()

        assertThat(
            MedtronicDeviceType.isSameDevice(
                MedtronicDeviceType.Medtronic_522,
                MedtronicDeviceType.Medtronic_523andHigher
            )
        ).isFalse()
    }

    @Test
    fun `test MedtronicDeviceType bolusStrokes for 523 and higher`() {
        assertThat(MedtronicDeviceType.Medtronic_523_Revel.bolusStrokes).isEqualTo(40)
        assertThat(MedtronicDeviceType.Medtronic_554_Veo.bolusStrokes).isEqualTo(40)
        assertThat(MedtronicDeviceType.Medtronic_754_Veo.bolusStrokes).isEqualTo(40)
    }

    @Test
    fun `test MedtronicDeviceType bolusStrokes for 522 and lower`() {
        assertThat(MedtronicDeviceType.Medtronic_522.bolusStrokes).isEqualTo(10)
        assertThat(MedtronicDeviceType.Medtronic_515.bolusStrokes).isEqualTo(10)
        assertThat(MedtronicDeviceType.Medtronic_512.bolusStrokes).isEqualTo(10)
    }

    @Test
    fun `test MedtronicDeviceType isMedtronic_523orHigher`() {
        assertThat(MedtronicDeviceType.Medtronic_523_Revel.isMedtronic_523orHigher).isTrue()
        assertThat(MedtronicDeviceType.Medtronic_554_Veo.isMedtronic_523orHigher).isTrue()
        assertThat(MedtronicDeviceType.Medtronic_522.isMedtronic_523orHigher).isFalse()
        assertThat(MedtronicDeviceType.Medtronic_515.isMedtronic_523orHigher).isFalse()
    }

    @Test
    fun `test MedtronicDeviceType family property`() {
        assertThat(MedtronicDeviceType.Medtronic_522.isFamily).isFalse()
        assertThat(MedtronicDeviceType.Medtronic_522_722.isFamily).isTrue()
        assertThat(MedtronicDeviceType.Medtronic_523andHigher.isFamily).isTrue()
    }

    @Test
    fun `test MedtronicDeviceType pumpModel property`() {
        assertThat(MedtronicDeviceType.Medtronic_522.pumpModel).isEqualTo("522")
        assertThat(MedtronicDeviceType.Medtronic_554_Veo.pumpModel).isEqualTo("554")
        assertThat(MedtronicDeviceType.Medtronic_522_722.pumpModel).isNull()
    }

    @Test
    fun `test MedtronicDeviceType family members`() {
        val family = MedtronicDeviceType.Medtronic_522_722.familyMembers

        assertThat(family).isNotNull()
        assertThat(family).contains(MedtronicDeviceType.Medtronic_522)
        assertThat(family).contains(MedtronicDeviceType.Medtronic_722)
        assertThat(family).hasSize(2)
    }

    // PacketType Tests
    @Test
    fun `test PacketType getByValue with valid values`() {
        assertThat(PacketType.getByValue(0xa2.toShort())).isEqualTo(PacketType.MySentry)
        assertThat(PacketType.getByValue(0xa5.toShort())).isEqualTo(PacketType.Meter)
        assertThat(PacketType.getByValue(0xa7.toShort())).isEqualTo(PacketType.Carelink)
        assertThat(PacketType.getByValue(0xa8.toShort())).isEqualTo(PacketType.Sensor)
    }

    @Test
    fun `test PacketType getByValue with invalid value`() {
        val packetType = PacketType.getByValue(0xFF.toShort())

        assertThat(packetType).isEqualTo(PacketType.Invalid)
    }

    @Test
    fun `test PacketType value property`() {
        assertThat(PacketType.Carelink.value).isEqualTo(0xa7.toByte())
        assertThat(PacketType.MySentry.value).isEqualTo(0xa2.toByte())
        assertThat(PacketType.Meter.value).isEqualTo(0xa5.toByte())
        assertThat(PacketType.Sensor.value).isEqualTo(0xa8.toByte())
        assertThat(PacketType.Invalid.value).isEqualTo(0x00.toByte())
    }

    // BatteryType Tests
    @Test
    fun `test BatteryType voltage ranges for Alkaline`() {
        assertThat(BatteryType.Alkaline.lowVoltage).isWithin(0.01).of(1.20)
        assertThat(BatteryType.Alkaline.highVoltage).isWithin(0.01).of(1.47)
    }

    @Test
    fun `test BatteryType voltage ranges for Lithium`() {
        assertThat(BatteryType.Lithium.lowVoltage).isWithin(0.01).of(1.22)
        assertThat(BatteryType.Lithium.highVoltage).isWithin(0.01).of(1.64)
    }

    @Test
    fun `test BatteryType voltage ranges for NiZn`() {
        assertThat(BatteryType.NiZn.lowVoltage).isWithin(0.01).of(1.40)
        assertThat(BatteryType.NiZn.highVoltage).isWithin(0.01).of(1.70)
    }

    @Test
    fun `test BatteryType voltage ranges for NiMH`() {
        assertThat(BatteryType.NiMH.lowVoltage).isWithin(0.01).of(1.10)
        assertThat(BatteryType.NiMH.highVoltage).isWithin(0.01).of(1.40)
    }

    @Test
    fun `test BatteryType None has zero voltage range`() {
        assertThat(BatteryType.None.lowVoltage).isWithin(0.01).of(0.0)
        assertThat(BatteryType.None.highVoltage).isWithin(0.01).of(0.0)
    }

    @Test
    fun `test BatteryType all types have description`() {
        assertThat(BatteryType.None.description).isNotEqualTo(0)
        assertThat(BatteryType.Alkaline.description).isNotEqualTo(0)
        assertThat(BatteryType.Lithium.description).isNotEqualTo(0)
        assertThat(BatteryType.NiZn.description).isNotEqualTo(0)
        assertThat(BatteryType.NiMH.description).isNotEqualTo(0)
    }

    // PumpBolusType Tests
    @Test
    fun `test PumpBolusType enum values`() {
        val types = PumpBolusType.entries

        assertThat(types).contains(PumpBolusType.Normal)
        assertThat(types).contains(PumpBolusType.Audio)
        assertThat(types).contains(PumpBolusType.Extended)
        assertThat(types).contains(PumpBolusType.Multiwave)
    }

    // BasalProfileStatus Tests
    @Test
    fun `test BasalProfileStatus enum values exist`() {
        val statuses = BasalProfileStatus.entries

        assertThat(statuses).isNotEmpty()
        assertThat(statuses).contains(BasalProfileStatus.NotInitialized)
        assertThat(statuses).contains(BasalProfileStatus.ProfileOK)
    }

    // MedtronicStatusRefreshType Tests
    @Test
    fun `test MedtronicStatusRefreshType enum values exist`() {
        val types = MedtronicStatusRefreshType.entries

        assertThat(types).isNotEmpty()
        assertThat(types).contains(MedtronicStatusRefreshType.PumpHistory)
        assertThat(types).contains(MedtronicStatusRefreshType.PumpTime)
        assertThat(types).contains(MedtronicStatusRefreshType.BatteryStatus)
        assertThat(types).contains(MedtronicStatusRefreshType.RemainingInsulin)
    }

    // MedtronicNotificationType Tests
    @Test
    fun `test MedtronicNotificationType enum values exist`() {
        val types = MedtronicNotificationType.entries

        assertThat(types).isNotEmpty()
    }

    // PumpConfigurationGroup Tests
    @Test
    fun `test PumpConfigurationGroup enum values exist`() {
        val groups = PumpConfigurationGroup.entries

        assertThat(groups).isNotEmpty()
        assertThat(groups).contains(PumpConfigurationGroup.General)
        assertThat(groups).contains(PumpConfigurationGroup.Bolus)
        assertThat(groups).contains(PumpConfigurationGroup.Basal)
        assertThat(groups).contains(PumpConfigurationGroup.Insulin)
        assertThat(groups).contains(PumpConfigurationGroup.Sound)
        assertThat(groups).contains(PumpConfigurationGroup.Other)
    }
}

package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaRsMessageHashTableTest : DanaRSTestBase() {

    @Mock lateinit var pumpSync: PumpSync

    lateinit var packetList: Set<DanaRSPacket>
    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
        }
    }

    @BeforeEach
    fun setupMock() {
        packetList = setOf(
            DanaRSPacketNotifyAlarm(packetInjector, rh, pumpSync, danaPump),
            DanaRSPacketNotifyDeliveryComplete(packetInjector, rh, rxBus, danaPump),
            DanaRSPacketNotifyDeliveryRateDisplay(packetInjector, rh, rxBus, danaPump),
            DanaRSPacketNotifyMissedBolusAlarm(packetInjector)
        )
    }

    @Test
    fun findMessage() {
        val danaRSMessageHashTable = DanaRSMessageHashTable(packetList)
        val command = DanaRSPacketNotifyMissedBolusAlarm(packetInjector).command
        Assertions.assertTrue(danaRSMessageHashTable.findMessage(command) is DanaRSPacketNotifyMissedBolusAlarm)
    }

    @Test
    fun throwErrorForUnknownMessage() {
        val danaRSMessageHashTable = DanaRSMessageHashTable(packetList)
        val command = DanaRSPacket(packetInjector).command
        assertThrows(Exception::class.java) {
            danaRSMessageHashTable.findMessage(command)
        }
    }
}
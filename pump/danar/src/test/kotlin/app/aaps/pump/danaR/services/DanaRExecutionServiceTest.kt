package app.aaps.pump.danaR.services

import android.bluetooth.BluetoothSocket
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.SerialIOThread
import app.aaps.pump.danar.comm.MessageHashTableR
import app.aaps.pump.danar.comm.MsgCheckValue
import app.aaps.pump.danar.comm.MsgSetBasalProfile
import app.aaps.pump.danar.comm.MsgSetTempBasalStart
import app.aaps.pump.danar.comm.MsgSetTempBasalStop
import app.aaps.pump.danar.comm.MsgStatus
import app.aaps.pump.danar.comm.MsgStatusBasic
import app.aaps.pump.danar.comm.MsgStatusBolusExtended
import app.aaps.pump.danar.comm.MsgStatusTempBasal
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRExecutionServiceTest : TestBaseWithProfile() {

    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var messageHashTableR: MessageHashTableR
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var serialIOThread: SerialIOThread
    @Mock lateinit var rfcommSocket: BluetoothSocket
    @Mock lateinit var profile: Profile
    @Mock lateinit var pumpEnactResult: PumpEnactResult

    private lateinit var danaRExecutionService: DanaRExecutionService

    @BeforeEach
    fun setup() {
        danaRExecutionService = DanaRExecutionService()
        danaRExecutionService.aapsLogger = aapsLogger
        danaRExecutionService.rxBus = rxBus
        danaRExecutionService.preferences = preferences
        danaRExecutionService.context = context
        danaRExecutionService.rh = rh
        danaRExecutionService.danaPump = danaPump
        danaRExecutionService.fabricPrivacy = fabricPrivacy
        danaRExecutionService.dateUtil = dateUtil
        danaRExecutionService.aapsSchedulers = aapsSchedulers
        danaRExecutionService.pumpSync = pumpSync
        danaRExecutionService.activePlugin = activePlugin
        danaRExecutionService.uiInteraction = uiInteraction
        danaRExecutionService.instantiator = instantiator
        danaRExecutionService.danaRPlugin = danaRPlugin
        danaRExecutionService.danaRKoreanPlugin = danaRKoreanPlugin
        danaRExecutionService.commandQueue = commandQueue
        danaRExecutionService.messageHashTableR = messageHashTableR
        danaRExecutionService.profileFunction = profileFunction

        `when`(instantiator.providePumpEnactResult()).thenReturn(pumpEnactResult)
        `when`(pumpEnactResult.success(any())).thenReturn(pumpEnactResult)
        `when`(pumpEnactResult.comment(any())).thenReturn(pumpEnactResult)
        `when`(rh.gs(anyInt())).thenReturn("test")
        `when`(rh.gs(anyInt(), any())).thenReturn("test")
        `when`(danaRPlugin.pumpDescription).thenReturn(mockPumpDescription())
    }

    @Test
    fun testLoadEvents() {
        val result = danaRExecutionService.loadEvents()

        assertThat(result).isNotNull()
        assertThat(result.success).isTrue()
    }

    @Test
    fun testTempBasal_notConnected() {
        val result = danaRExecutionService.tempBasal(120, 1)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalStop_notConnected() {
        val result = danaRExecutionService.tempBasalStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolus_notConnected() {
        val result = danaRExecutionService.extendedBolus(2.0, 2)

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolusStop_notConnected() {
        val result = danaRExecutionService.extendedBolusStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testBolus_notConnected() {
        val treatment = EventOverviewBolusProgress.Treatment(0.0, 0, false, 0)
        val result = danaRExecutionService.bolus(5.0, 0, 0L, treatment)

        assertThat(result).isFalse()
    }

    @Test
    fun testHighTempBasal() {
        val result = danaRExecutionService.highTempBasal(150, 30)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration() {
        val result = danaRExecutionService.tempBasalShortDuration(150, 15)

        assertThat(result).isFalse()
    }

    @Test
    fun testUpdateBasalsInPump_notConnected() {
        `when`(profileFunction.getProfile()).thenReturn(profile)
        `when`(profile.getBasal()).thenReturn(1.0)

        val result = danaRExecutionService.updateBasalsInPump(profile)

        assertThat(result).isFalse()
    }

    @Test
    fun testSetUserOptions_notConnected() {
        val result = danaRExecutionService.setUserOptions()

        assertThat(result).isNotNull()
        assertThat(result.success).isFalse()
    }

    @Test
    fun testConnect_alreadyConnecting() {
        danaRExecutionService.isConnecting = true

        danaRExecutionService.connect()

        // Should return early without doing anything
        assertThat(danaRExecutionService.isConnecting).isTrue()
    }

    private fun mockPumpDescription(): app.aaps.core.data.pump.defs.PumpDescription {
        return app.aaps.core.data.pump.defs.PumpDescription().apply {
            basalStep = 0.01
        }
    }
}

package app.aaps.pump.danaR.services

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasal
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import javax.inject.Provider
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danar.SerialIOThread
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MessageHashTableBase
import app.aaps.pump.danar.comm.MsgHistoryBolus
import app.aaps.pump.danar.services.AbstractDanaRExecutionService
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any as kAny
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class AbstractDanaRExecutionServiceTest : TestBase() {

    @Mock lateinit var injector: HasAndroidInjector
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var context: Context
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>
    @Mock lateinit var messageHashTable: MessageHashTableBase
    @Mock lateinit var bluetoothManager: BluetoothManager
    @Mock lateinit var bluetoothAdapter: BluetoothAdapter
    @Mock lateinit var bluetoothDevice: BluetoothDevice
    @Mock lateinit var bluetoothSocket: BluetoothSocket
    @Mock lateinit var profile: Profile
    @Mock lateinit var pumpEnactResult: PumpEnactResult

    private lateinit var testService: TestDanaRExecutionService

    inner class TestDanaRExecutionService : AbstractDanaRExecutionService() {
        override fun messageHashTable(): MessageHashTableBase = messageHashTable
        override fun updateBasalsInPump(profile: Profile): Boolean = true
        override fun getPumpStatus() {}
        override fun loadEvents(): PumpEnactResult? = pumpEnactResult
        override fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean = true
        override fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean = false
        override fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean = false
        override fun tempBasal(percent: Int, durationInHours: Int): Boolean = true
        override fun tempBasalStop(): Boolean = true
        override fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean = true
        override fun extendedBolusStop(): Boolean = true
        override fun setUserOptions(): PumpEnactResult? = pumpEnactResult
    }

    @BeforeEach
    fun setup() {
        `when`(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        `when`(pumpEnactResultProvider.get()).thenReturn(pumpEnactResult)
        `when`(pumpEnactResult.success(anyBoolean())).thenReturn(pumpEnactResult)
        `when`(pumpEnactResult.comment(anyString())).thenReturn(pumpEnactResult)
        `when`(rh.gs(anyInt())).thenReturn("test string")
        `when`(rh.gs(anyInt(), any())).thenReturn("test string")

        testService = TestDanaRExecutionService()
        testService.aapsLogger = aapsLogger
        testService.rxBus = rxBus
        testService.preferences = preferences
        testService.context = context
        testService.rh = rh
        testService.danaPump = danaPump
        testService.fabricPrivacy = fabricPrivacy
        testService.dateUtil = dateUtil
        testService.aapsSchedulers = aapsSchedulers
        testService.pumpSync = pumpSync
        testService.activePlugin = activePlugin
        testService.uiInteraction = uiInteraction
        testService.pumpEnactResultProvider = pumpEnactResultProvider
        testService.injector = injector

        `when`(injector.androidInjector()).thenReturn(AndroidInjector { })
    }

    @Test
    fun testIsConnected() {
        assertThat(testService.isConnected).isFalse()
    }

    @Test
    fun testIsHandshakeInProgress() {
        assertThat(testService.isHandshakeInProgress).isFalse()
    }

    @Test
    fun testFinishHandshaking() {
        testService.finishHandshaking()
        verify(rxBus).send(any(EventPumpStatusChanged::class.java))
    }

    @Test
    fun testDisconnect() {
        testService.disconnect("test")
        // Verify no crash when mSerialIOThread is null
    }

    @Test
    fun testStopConnecting() {
        testService.stopConnecting()
        // Verify no crash when mSerialIOThread is null
    }

    @Test
    fun testBolusStop_notConnected() {
        `when`(danaPump.bolusingTreatment).thenReturn(null)

        testService.bolusStop()

        verify(danaPump).bolusStopForced = true
        verify(danaPump).bolusStopped = true
    }

    @Test
    fun testLoadHistory_notConnected() {
        val result = testService.loadHistory(RecordTypes.RECORD_TYPE_BOLUS)

        assertThat(result).isNotNull()
    }

    @Test
    fun testLoadHistory_withDifferentTypes() {
        // Test all record types
        val types = arrayOf(
            RecordTypes.RECORD_TYPE_ALARM,
            RecordTypes.RECORD_TYPE_BASALHOUR,
            RecordTypes.RECORD_TYPE_BOLUS,
            RecordTypes.RECORD_TYPE_CARBO,
            RecordTypes.RECORD_TYPE_DAILY,
            RecordTypes.RECORD_TYPE_ERROR,
            RecordTypes.RECORD_TYPE_GLUCOSE,
            RecordTypes.RECORD_TYPE_REFILL,
            RecordTypes.RECORD_TYPE_SUSPEND
        )

        types.forEach { type ->
            val result = testService.loadHistory(type)
            assertThat(result).isNotNull()
        }
    }

    @Test
    fun testWaitForWholeMinute() {
        val now = 1000000L
        `when`(dateUtil.now()).thenReturn(now)

        // This should not block indefinitely in test
        testService.waitForWholeMinute()
    }

    @Test
    fun testDoSanityCheck_temporaryBasalMismatch() {
        val temporaryBasal = mock(TemporaryBasal::class.java)
        val pumpState = mock(PumpSync.PumpState::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.Pump::class.java)

        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)
        `when`(pumpState.temporaryBasal).thenReturn(temporaryBasal)
        `when`(pumpState.extendedBolus).thenReturn(null)
        `when`(temporaryBasal.rate).thenReturn(150.0)
        `when`(temporaryBasal.timestamp).thenReturn(1000000L)
        `when`(danaPump.isTempBasalInProgress).thenReturn(true)
        `when`(danaPump.tempBasalPercent).thenReturn(100)
        `when`(danaPump.tempBasalStart).thenReturn(1000000L)
        `when`(danaPump.tempBasalDuration).thenReturn(30)
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")
        `when`(dateUtil.now()).thenReturn(2000000L)

        testService.doSanityCheck()

        // Verify that synchronization was attempted
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testDoSanityCheck_noTemporaryBasalInAAPSButInPump() {
        val pumpState = mock(PumpSync.PumpState::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.Pump::class.java)

        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)
        `when`(pumpState.temporaryBasal).thenReturn(null)
        `when`(pumpState.extendedBolus).thenReturn(null)
        `when`(danaPump.isTempBasalInProgress).thenReturn(true)
        `when`(danaPump.tempBasalPercent).thenReturn(120)
        `when`(danaPump.tempBasalStart).thenReturn(1000000L)
        `when`(danaPump.tempBasalDuration).thenReturn(30)
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")

        testService.doSanityCheck()

        // Verify notification was added
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testDoSanityCheck_temporaryBasalInAAPSButNotInPump() {
        val temporaryBasal = mock(TemporaryBasal::class.java)
        val pumpState = mock(PumpSync.PumpState::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.Pump::class.java)

        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)
        `when`(pumpState.temporaryBasal).thenReturn(temporaryBasal)
        `when`(pumpState.extendedBolus).thenReturn(null)
        `when`(temporaryBasal.rate).thenReturn(150.0)
        `when`(temporaryBasal.timestamp).thenReturn(1000000L)
        `when`(danaPump.isTempBasalInProgress).thenReturn(false)
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")
        `when`(dateUtil.now()).thenReturn(2000000L)

        testService.doSanityCheck()

        // Verify synchronization
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testDoSanityCheck_extendedBolusMismatch() {
        val extendedBolus = mock(app.aaps.core.interfaces.pump.ExtendedBolus::class.java)
        val pumpState = mock(PumpSync.PumpState::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.Pump::class.java)

        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)
        `when`(pumpState.temporaryBasal).thenReturn(null)
        `when`(pumpState.extendedBolus).thenReturn(extendedBolus)
        `when`(extendedBolus.rate).thenReturn(1.5)
        `when`(extendedBolus.timestamp).thenReturn(1000000L)
        `when`(danaPump.isExtendedInProgress).thenReturn(true)
        `when`(danaPump.extendedBolusAbsoluteRate).thenReturn(2.0)
        `when`(danaPump.extendedBolusStart).thenReturn(1000000L)
        `when`(danaPump.extendedBolusAmount).thenReturn(3.0)
        `when`(danaPump.extendedBolusDuration).thenReturn(120)
        `when`(danaPump.tempBasalStart).thenReturn(1000000L)
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")
        `when`(activePump.isFakingTempsByExtendedBoluses).thenReturn(false)

        testService.doSanityCheck()

        // Verify notification
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testDoSanityCheck_allMatch() {
        val pumpState = mock(PumpSync.PumpState::class.java)

        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)
        `when`(pumpState.temporaryBasal).thenReturn(null)
        `when`(pumpState.extendedBolus).thenReturn(null)
        `when`(danaPump.isTempBasalInProgress).thenReturn(false)
        `when`(danaPump.isExtendedInProgress).thenReturn(false)

        testService.doSanityCheck()

        // Verify no notifications when everything matches
        verify(uiInteraction, never()).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testGetBTSocketForSelectedPump_noBluetoothAdapter() {
        `when`(preferences.get(DanaStringKey.DanaRName)).thenReturn("TestPump")
        `when`(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(bluetoothManager)
        `when`(bluetoothManager.adapter).thenReturn(null)

        testService.getBTSocketForSelectedPump()

        // Should handle null adapter gracefully
    }
}

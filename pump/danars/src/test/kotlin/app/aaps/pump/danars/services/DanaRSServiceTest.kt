package app.aaps.pump.danars.services

import android.content.Context
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.Preferences
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRSServiceTest : TestBase() {

    @Mock lateinit var injector: dagger.android.HasAndroidInjector
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var context: Context
    @Mock lateinit var danaRSPlugin: DanaRSPlugin
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var bleComm: BLEComm
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var instantiator: Instantiator
    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var profile: Profile
    @Mock lateinit var pumpEnactResult: PumpEnactResult

    private lateinit var danaRSService: DanaRSService

    @BeforeEach
    fun setup() {
        danaRSService = DanaRSService()
        danaRSService.injector = injector
        danaRSService.aapsLogger = aapsLogger
        danaRSService.aapsSchedulers = aapsSchedulers
        danaRSService.rxBus = rxBus
        danaRSService.preferences = preferences
        danaRSService.rh = rh
        danaRSService.profileFunction = profileFunction
        danaRSService.commandQueue = commandQueue
        danaRSService.context = context
        danaRSService.danaRSPlugin = danaRSPlugin
        danaRSService.danaPump = danaPump
        danaRSService.activePlugin = activePlugin
        danaRSService.constraintChecker = constraintChecker
        danaRSService.uiInteraction = uiInteraction
        danaRSService.bleComm = bleComm
        danaRSService.fabricPrivacy = fabricPrivacy
        danaRSService.pumpSync = pumpSync
        danaRSService.dateUtil = dateUtil
        danaRSService.instantiator = instantiator

        `when`(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        `when`(instantiator.providePumpEnactResult()).thenReturn(pumpEnactResult)
        `when`(pumpEnactResult.success(anyBoolean())).thenReturn(pumpEnactResult)
        `when`(pumpEnactResult.comment(anyString())).thenReturn(pumpEnactResult)
        `when`(rh.gs(anyInt())).thenReturn("test string")
        `when`(rh.gs(anyInt(), any())).thenReturn("test string")
        `when`(activePlugin.activePump).thenReturn(danaRSPlugin)
        `when`(danaRSPlugin.pumpDescription).thenReturn(mockPumpDescription())
    }

    @Test
    fun testIsConnected() {
        `when`(bleComm.isConnected).thenReturn(false)
        assertThat(danaRSService.isConnected).isFalse()

        `when`(bleComm.isConnected).thenReturn(true)
        assertThat(danaRSService.isConnected).isTrue()
    }

    @Test
    fun testIsConnecting() {
        `when`(bleComm.isConnecting).thenReturn(false)
        assertThat(danaRSService.isConnecting).isFalse()

        `when`(bleComm.isConnecting).thenReturn(true)
        assertThat(danaRSService.isConnecting).isTrue()
    }

    @Test
    fun testConnect() {
        `when`(bleComm.connect(anyString(), anyString())).thenReturn(true)

        val result = danaRSService.connect("test", "00:11:22:33:44:55")

        assertThat(result).isTrue()
    }

    @Test
    fun testDisconnect() {
        danaRSService.disconnect("test")
        // Should not throw exception
    }

    @Test
    fun testStopConnecting() {
        danaRSService.stopConnecting()
        // Should not throw exception
    }

    @Test
    fun testLoadEvents_notInitialized() {
        `when`(danaRSPlugin.isInitialized()).thenReturn(false)

        val result = danaRSService.loadEvents()

        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo("pump not initialized")
    }

    @Test
    fun testSetUserSettings() {
        val result = danaRSService.setUserSettings()

        assertThat(result).isNotNull()
    }

    @Test
    fun testBolus_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)
        val treatment = EventOverviewBolusProgress.Treatment(0.0, 0, false, 0)

        val result = danaRSService.bolus(5.0, 0, 0L, treatment)

        assertThat(result).isFalse()
    }

    @Test
    fun testBolusStop() {
        `when`(bleComm.isConnected).thenReturn(false)
        `when`(danaPump.bolusingTreatment).thenReturn(null)

        danaRSService.bolusStop()

        // Should not throw exception
    }

    @Test
    fun testTempBasal_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.tempBasal(120, 1)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalStop_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.tempBasalStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration_invalidDuration() {
        val result = danaRSService.tempBasalShortDuration(120, 20)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration_validDuration15() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.tempBasalShortDuration(120, 15)

        // Returns false because not connected
        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration_validDuration30() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.tempBasalShortDuration(120, 30)

        // Returns false because not connected
        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolus_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.extendedBolus(2.0, 2)

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolusStop_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.extendedBolusStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testUpdateBasalsInPump_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)
        `when`(profileFunction.getProfile()).thenReturn(profile)

        val result = danaRSService.updateBasalsInPump(profile)

        assertThat(result).isFalse()
    }

    @Test
    fun testLoadHistory_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.loadHistory(RecordTypes.RECORD_TYPE_BOLUS)

        assertThat(result).isNotNull()
        assertThat(result.success).isFalse()
    }

    @Test
    fun testLoadHistory_allTypes() {
        `when`(bleComm.isConnected).thenReturn(false)

        val types = arrayOf(
            RecordTypes.RECORD_TYPE_ALARM,
            RecordTypes.RECORD_TYPE_PRIME,
            RecordTypes.RECORD_TYPE_BASALHOUR,
            RecordTypes.RECORD_TYPE_BOLUS,
            RecordTypes.RECORD_TYPE_CARBO,
            RecordTypes.RECORD_TYPE_DAILY,
            RecordTypes.RECORD_TYPE_GLUCOSE,
            RecordTypes.RECORD_TYPE_REFILL,
            RecordTypes.RECORD_TYPE_SUSPEND
        )

        types.forEach { type ->
            val result = danaRSService.loadHistory(type)
            assertThat(result).isNotNull()
        }
    }

    @Test
    fun testHighTempBasal() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.highTempBasal(150)

        // Returns false when status fails (not connected)
        assertThat(result).isFalse()
    }

    private fun mockPumpDescription(): app.aaps.core.data.pump.defs.PumpDescription {
        return app.aaps.core.data.pump.defs.PumpDescription().apply {
            basalStep = 0.01
        }
    }
}

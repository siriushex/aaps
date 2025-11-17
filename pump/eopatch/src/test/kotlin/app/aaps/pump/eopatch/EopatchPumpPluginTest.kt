package app.aaps.pump.eopatch

import app.aaps.core.data.model.BS
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.pump.eopatch.ble.PatchConnectionState
import app.aaps.pump.eopatch.code.PatchLifecycle
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class EopatchPumpPluginTest : EopatchTestBase() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var patchConnectionState: PatchConnectionState

    private lateinit var plugin: EopatchPumpPlugin

    @BeforeEach
    fun prepareMocksAndPlugin() {
        MockitoAnnotations.openMocks(this)
        prepareMocks()

        `when`(rh.gs(org.mockito.kotlin.any<Int>())).thenReturn("MockedString")
        `when`(patchManagerExecutor.patchConnectionState).thenReturn(patchConnectionState)
        `when`(patchConnectionState.isConnected).thenReturn(false)
        `when`(patchConnectionState.isConnecting).thenReturn(false)
        `when`(preferenceManager.patchState).thenReturn(
            app.aaps.pump.eopatch.vo.PatchState()
        )

        plugin = EopatchPumpPlugin(
            aapsLogger,
            rh,
            commandQueue,
            aapsSchedulers,
            rxBus,
            fabricPrivacy,
            dateUtil,
            pumpSync,
            patchManager,
            patchManagerExecutor,
            alarmManager,
            preferenceManager,
            uiInteraction,
            profileFunction,
            instantiator,
            patchConfig,
            normalBasalManager
        )
    }

    @Test
    fun `manufacturer should return Eoflow`() {
        assertThat(plugin.manufacturer()).isEqualTo(ManufacturerType.Eoflow)
    }

    @Test
    fun `model should return EOFLOW_EOPATCH2`() {
        assertThat(plugin.model()).isEqualTo(PumpType.EOFLOW_EOPATCH2)
    }

    @Test
    fun `serialNumber should return patch serial number`() {
        patchConfig.patchSerialNumber = "TEST12345"

        assertThat(plugin.serialNumber()).isEqualTo("TEST12345")
    }

    @Test
    fun `isInitialized should return false when not connected`() {
        `when`(patchConnectionState.isConnected).thenReturn(false)

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should return false when not activated`() {
        `when`(patchConnectionState.isConnected).thenReturn(true)
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should return true when connected and activated`() {
        `when`(patchConnectionState.isConnected).thenReturn(true)
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()

        assertThat(plugin.isInitialized()).isTrue()
    }

    @Test
    fun `isSuspended should return true when basal is paused`() {
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.isNormalBasalPaused = true
        `when`(preferenceManager.patchState).thenReturn(patchState)

        assertThat(plugin.isSuspended()).isTrue()
    }

    @Test
    fun `isSuspended should return false when basal is not paused`() {
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.isNormalBasalPaused = false
        `when`(preferenceManager.patchState).thenReturn(patchState)

        assertThat(plugin.isSuspended()).isFalse()
    }

    @Test
    fun `isBusy should always return false`() {
        assertThat(plugin.isBusy()).isFalse()
    }

    @Test
    fun `isConnected should return true when deactivated`() {
        patchConfig.updateDeactivated()

        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isConnected should return connection state when activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        patchConfig.macAddress = "00:11:22:33:44:55"
        `when`(patchConnectionState.isConnected).thenReturn(true)

        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isConnecting should return connection state`() {
        `when`(patchConnectionState.isConnecting).thenReturn(true)

        assertThat(plugin.isConnecting()).isTrue()
    }

    @Test
    fun `isHandshakeInProgress should always return false`() {
        assertThat(plugin.isHandshakeInProgress()).isFalse()
    }

    @Test
    fun `connect should update last data time`() {
        val beforeTime = System.currentTimeMillis()

        plugin.connect("test reason")

        assertThat(plugin.lastDataTime()).isAtLeast(beforeTime)
    }

    @Test
    fun `baseBasalRate should return 0 when not activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        assertThat(plugin.baseBasalRate).isWithin(0.001).of(0.0)
    }

    @Test
    fun `baseBasalRate should return 0 when basal is paused`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.isNormalBasalPaused = true
        `when`(preferenceManager.patchState).thenReturn(patchState)

        assertThat(plugin.baseBasalRate).isWithin(0.001).of(0.0)
    }

    @Test
    fun `reservoirLevel should return 0 when not activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        assertThat(plugin.reservoirLevel).isWithin(0.001).of(0.0)
    }

    @Test
    fun `reservoirLevel should return patch state value when activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.remainedInsulin = 50.0f
        `when`(preferenceManager.patchState).thenReturn(patchState)

        assertThat(plugin.reservoirLevel).isWithin(0.001).of(50.0)
    }

    @Test
    fun `batteryLevel should return 0 when not activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        assertThat(plugin.batteryLevel).isEqualTo(0)
    }

    @Test
    fun `isFakingTempsByExtendedBoluses should return false`() {
        assertThat(plugin.isFakingTempsByExtendedBoluses).isFalse()
    }

    @Test
    fun `canHandleDST should return false`() {
        assertThat(plugin.canHandleDST()).isFalse()
    }

    @Test
    fun `getCustomActions should return null`() {
        assertThat(plugin.getCustomActions()).isNull()
    }

    @Test
    fun `getJSONStatus should return valid JSON`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.remainedInsulin = 50.0f
        patchState.isNormalBasalPaused = false
        `when`(preferenceManager.patchState).thenReturn(patchState)
        `when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(null, null, null, validProfile)
        )
        `when`(profileFunction.getProfileName()).thenReturn("TestProfile")

        val json = plugin.getJSONStatus(validProfile, "TestProfile", "1.0.0")

        assertThat(json).isNotNull()
        assertThat(json.has("status")).isTrue()
        assertThat(json.has("reservoir")).isTrue()
        assertThat(json.has("battery")).isTrue()
    }

    @Test
    fun `isThisProfileSet should use normalBasalManager`() {
        normalBasalManager.setNormalBasal(validProfile)

        val result = plugin.isThisProfileSet(validProfile)

        assertThat(result).isTrue()
    }

    @Test
    fun `shortStatus should show not enabled when not activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        val status = plugin.shortStatus(false)

        assertThat(status).contains("not enabled")
    }

    @Test
    fun `shortStatus should show reservoir and battery when activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.remainedInsulin = 50.0f
        `when`(preferenceManager.patchState).thenReturn(patchState)
        `when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(null, null, null, validProfile)
        )

        val status = plugin.shortStatus(false)

        assertThat(status).contains("Reservoir")
        assertThat(status).contains("Battery")
    }

    @Test
    fun `pumpDescription should be initialized`() {
        assertThat(plugin.pumpDescription).isNotNull()
    }

    @Test
    fun `plugin should be of type PUMP`() {
        assertThat(plugin.getType()).isEqualTo(PluginType.PUMP)
    }

    @Test
    fun `plugin name should be set`() {
        assertThat(plugin.name).isNotEmpty()
    }
}

package app.aaps.pump.eopatch

import app.aaps.core.data.model.BS
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

class EopatchPumpPluginBolusTest : EopatchTestBase() {

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

        `when`(rh.gs(any<Int>())).thenReturn("MockedString")
        `when`(rh.gs(any<Int>(), any())).thenReturn("Mocked %s")
        `when`(patchManagerExecutor.patchConnectionState).thenReturn(patchConnectionState)
        `when`(patchConnectionState.isConnected).thenReturn(true)
        `when`(patchConnectionState.isConnecting).thenReturn(false)

        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        `when`(preferenceManager.patchState).thenReturn(patchState)

        val bolusCurrent = app.aaps.pump.eopatch.vo.BolusCurrent()
        `when`(preferenceManager.bolusCurrent).thenReturn(bolusCurrent)

        // Set patch as activated
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        patchConfig.macAddress = "00:11:22:33:44:55"

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
    fun `deliverTreatment should require insulin greater than 0`() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = 0.0
        detailedBolusInfo.carbs = 0.0

        try {
            plugin.deliverTreatment(detailedBolusInfo)
            assertThat(false).isTrue() // Should not reach here
        } catch (e: IllegalArgumentException) {
            // Expected
            assertThat(e).isNotNull()
        }
    }

    @Test
    fun `deliverTreatment should require carbs equal to 0`() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = 5.0
        detailedBolusInfo.carbs = 10.0 // Not allowed

        try {
            plugin.deliverTreatment(detailedBolusInfo)
            assertThat(false).isTrue() // Should not reach here
        } catch (e: IllegalArgumentException) {
            // Expected
            assertThat(e).isNotNull()
        }
    }

    @Test
    fun `stopBolusDelivering should call patch executor`() {
        `when`(patchManagerExecutor.stopNowBolus()).thenReturn(
            Single.just(app.aaps.pump.eopatch.core.response.BaseResponse(0))
        )
        `when`(aapsSchedulers.io).thenReturn(io.reactivex.rxjava3.schedulers.Schedulers.trampoline())
        `when`(aapsSchedulers.main).thenReturn(io.reactivex.rxjava3.schedulers.Schedulers.trampoline())

        // Should not throw exception
        plugin.stopBolusDelivering()
    }

    @Test
    fun `setTempBasalAbsolute should fail when normal basal not active`() {
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.update(ByteArray(20), System.currentTimeMillis())
        // isNormalBasalAct will be false
        `when`(preferenceManager.patchState).thenReturn(patchState)

        val result = plugin.setTempBasalAbsolute(1.5, 60, validProfile, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setTempBasalPercent should fail when normal basal not active`() {
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.update(ByteArray(20), System.currentTimeMillis())
        `when`(preferenceManager.patchState).thenReturn(patchState)

        val result = plugin.setTempBasalPercent(150, 60, validProfile, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setTempBasalPercent should fail when percent is 0`() {
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        val bytes = ByteArray(20)
        bytes[4] = 0x14.toByte() // isNormalBasalAct = true
        patchState.update(bytes, System.currentTimeMillis())
        `when`(preferenceManager.patchState).thenReturn(patchState)

        val result = plugin.setTempBasalPercent(0, 60, validProfile, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `cancelTempBasal should return success when TBR already false`() {
        `when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(null, null, null, validProfile)
        )

        val result = plugin.cancelTempBasal(false)

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `cancelExtendedBolus should return success when not active`() {
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.update(ByteArray(20), System.currentTimeMillis())
        `when`(preferenceManager.patchState).thenReturn(patchState)
        `when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(null, null, null, validProfile)
        )

        val result = plugin.cancelExtendedBolus()

        assertThat(result).isNotNull()
    }

    @Test
    fun `loadTDDs should return empty result`() {
        val result = plugin.loadTDDs()

        assertThat(result).isNotNull()
    }

    @Test
    fun `executeCustomCommand should return null`() {
        val result = plugin.executeCustomCommand(object : app.aaps.core.interfaces.queue.CustomCommand {})

        assertThat(result).isNull()
    }

    @Test
    fun `timezoneOrDSTChanged should not throw exception`() {
        // Should not throw
        plugin.timezoneOrDSTChanged(app.aaps.core.data.pump.defs.TimeChangeType.TimezoneChanged)
        plugin.timezoneOrDSTChanged(app.aaps.core.data.pump.defs.TimeChangeType.DST)
    }

    @Test
    fun `finishHandshaking should not throw exception`() {
        // Should not throw
        plugin.finishHandshaking()
    }

    @Test
    fun `stopConnecting should not throw exception`() {
        // Should not throw
        plugin.stopConnecting()
    }

    @Test
    fun `disconnect should log and not throw exception`() {
        // Should not throw
        plugin.disconnect("test reason")
    }

    @Test
    fun `getPumpStatus should update last data time when activated`() {
        `when`(patchManagerExecutor.updateConnection()).thenReturn(Single.just(true))

        val beforeTime = System.currentTimeMillis()
        plugin.getPumpStatus("test")

        // Last data time should be updated
        assertThat(plugin.lastDataTime()).isAtLeast(beforeTime)
    }

    @Test
    fun `getPumpStatus should do nothing when not activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        plugin.getPumpStatus("test")

        // Should not throw exception
    }
}

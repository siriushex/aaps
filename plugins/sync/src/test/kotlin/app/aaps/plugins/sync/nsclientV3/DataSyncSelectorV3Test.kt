package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.keys.BooleanKey
import app.aaps.plugins.sync.nsShared.StoreDataForDbImpl
import app.aaps.plugins.sync.nsclientV3.keys.NsclientLongKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.internal.verification.Times
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DataSyncSelectorV3Test : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var virtualPump: VirtualPump
    @Mock lateinit var nsClientSource: NSClientSource

    private lateinit var storeDataForDb: StoreDataForDb
    private lateinit var sut: DataSyncSelectorV3

    @BeforeEach
    fun setUp() {
        storeDataForDb = StoreDataForDbImpl(aapsLogger, rxBus, persistenceLayer, preferences, config, nsClientSource, virtualPump)
        sut = DataSyncSelectorV3(preferences, aapsLogger, dateUtil, profileFunction, activePlugin, persistenceLayer, rxBus, storeDataForDb, config)
    }

    @Test
    fun bgUploadEnabledTest() {

        class NSClientSourcePlugin() : NSClientSource, BgSource {

            override fun isEnabled(): Boolean = true
            override fun detectSource(glucoseValue: GV) {}
        }
        val nsClientSourcePlugin = NSClientSourcePlugin()

        class AnotherSourcePlugin() : BgSource
        val anotherSourcePlugin = AnotherSourcePlugin()

        whenever(preferences.get(BooleanKey.BgSourceUploadToNs)).thenReturn(false)
        whenever(activePlugin.activeBgSource).thenReturn(nsClientSourcePlugin)
        assertThat(sut.bgUploadEnabled).isFalse()

        whenever(preferences.get(BooleanKey.BgSourceUploadToNs)).thenReturn(true)
        whenever(activePlugin.activeBgSource).thenReturn(nsClientSourcePlugin)
        assertThat(sut.bgUploadEnabled).isFalse()

        whenever(preferences.get(BooleanKey.BgSourceUploadToNs)).thenReturn(true)
        whenever(activePlugin.activeBgSource).thenReturn(anotherSourcePlugin)
        assertThat(sut.bgUploadEnabled).isTrue()
    }

    @Test
    fun resetToNextFullSyncTest() {
        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(1)
        sut.resetToNextFullSync()
        verify(preferences, Times(1)).remove(NsclientLongKey.GlucoseValueLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.TemporaryBasalLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.TemporaryTargetLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.ExtendedBolusLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.FoodLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.BolusLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.CarbsLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.BolusCalculatorLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.TherapyEventLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.ProfileSwitchLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.RunningModeLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.ProfileStoreLastSyncedId)
        verify(preferences, Times(1)).put(NsclientLongKey.DeviceStatusLastSyncedId, 1)

        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(null)
        sut.resetToNextFullSync()
        verify(preferences, Times(1)).remove(NsclientLongKey.DeviceStatusLastSyncedId)
    }

    @Test
    fun confirmLastTest() {
        // Bolus
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(2)
        sut.confirmLastBolusIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.BolusLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(1)
        sut.confirmLastBolusIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.BolusLastSyncedId, 2)
        // Carbs
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(2)
        sut.confirmLastCarbsIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.CarbsLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(1)
        sut.confirmLastCarbsIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.CarbsLastSyncedId, 2)
        // BolusCalculatorResults
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(2)
        sut.confirmLastBolusCalculatorResultsIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.BolusCalculatorLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(1)
        sut.confirmLastBolusCalculatorResultsIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.BolusCalculatorLastSyncedId, 2)
        // TempTargets
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(2)
        sut.confirmLastTempTargetsIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.TemporaryTargetLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(1)
        sut.confirmLastTempTargetsIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.TemporaryTargetLastSyncedId, 2)
        // Food
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(2)
        sut.confirmLastFoodIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.FoodLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(1)
        sut.confirmLastFoodIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.FoodLastSyncedId, 2)
        // GlucoseValue
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(2)
        sut.confirmLastGlucoseValueIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.GlucoseValueLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(1)
        sut.confirmLastGlucoseValueIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.GlucoseValueLastSyncedId, 2)
        // TherapyEvent
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(2)
        sut.confirmLastTherapyEventIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.TherapyEventLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(1)
        sut.confirmLastTherapyEventIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.TherapyEventLastSyncedId, 2)
        // DeviceStatus
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(2)
        sut.confirmLastDeviceStatusIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.DeviceStatusLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(1)
        sut.confirmLastDeviceStatusIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.DeviceStatusLastSyncedId, 2)
        // TemporaryBasal
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(2)
        sut.confirmLastTemporaryBasalIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.TemporaryBasalLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(1)
        sut.confirmLastTemporaryBasalIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.TemporaryBasalLastSyncedId, 2)
        // ExtendedBolus
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(2)
        sut.confirmLastExtendedBolusIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.ExtendedBolusLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(1)
        sut.confirmLastExtendedBolusIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.ExtendedBolusLastSyncedId, 2)
        // ProfileSwitch
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(2)
        sut.confirmLastProfileSwitchIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.ProfileSwitchLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(1)
        sut.confirmLastProfileSwitchIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.ProfileSwitchLastSyncedId, 2)
        // EffectiveProfileSwitch
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(2)
        sut.confirmLastEffectiveProfileSwitchIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.EffectiveProfileSwitchLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(1)
        sut.confirmLastEffectiveProfileSwitchIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.EffectiveProfileSwitchLastSyncedId, 2)
        // OfflineEvent
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(2)
        sut.confirmLastRunningModeIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.RunningModeLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(1)
        sut.confirmLastRunningModeIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.RunningModeLastSyncedId, 2)
        // ProfileStore
        sut.confirmLastProfileStore(2)
        verify(preferences, Times(1)).put(NsclientLongKey.ProfileStoreLastSyncedId, 2)
    }

    @Test
    fun processChangedBolusesAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastBolusId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementBolus(0)).thenReturn(Maybe.empty())
        sut.processChangedBoluses()
        verify(preferences, Times(1)).put(NsclientLongKey.BolusLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        clearInvocations(preferences, activePlugin)
    }
}
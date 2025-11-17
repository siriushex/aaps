package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.keys.BooleanKey
import app.aaps.plugins.sync.nsShared.StoreDataForDbImpl
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
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

    @Test
    fun processChangedBolusesWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L)

        sut.processChangedBoluses()

        // Should not call getNextSyncElementBolus when paused
        verify(persistenceLayer, Times(0)).getNextSyncElementBolus(org.mockito.kotlin.any())
    }

    @Test
    fun processChangedBolusesWithEmptyQueueTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(5L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L)
        whenever(persistenceLayer.getNextSyncElementBolus(5L)).thenReturn(Maybe.empty())

        sut.processChangedBoluses()

        // Should call getNextSyncElementBolus once and then stop
        verify(persistenceLayer, Times(1)).getNextSyncElementBolus(5L)
        verify(activePlugin, Times(0)).activeNsClient
    }

    @Test
    fun queueSizeTest() {
        // All counters initialized to -1, so total should be -13 (13 fields)
        assertThat(sut.queueSize()).isEqualTo(-13)
    }

    @Test
    fun doUploadWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true)

        sut.doUpload()

        // Should not calculate queue counters when paused
        verify(persistenceLayer, Times(0)).getLastBolusId()
        verify(persistenceLayer, Times(0)).getLastCarbsId()
        Unit
    }

    @Test
    fun doUploadWhenUploadDisabledTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false)

        sut.doUpload()

        // Should not process when upload is disabled
        verify(persistenceLayer, Times(0)).getLastBolusId()
        Unit
    }

    @Test
    fun doUploadCalculatesQueueCountersTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true)

        // Mock all the getLastId methods
        whenever(persistenceLayer.getLastBolusId()).thenReturn(100L)
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(200L)
        whenever(persistenceLayer.getLastBolusCalculatorResultId()).thenReturn(50L)
        whenever(persistenceLayer.getLastTemporaryTargetId()).thenReturn(75L)
        whenever(persistenceLayer.getLastFoodId()).thenReturn(25L)
        whenever(persistenceLayer.getLastGlucoseValueId()).thenReturn(500L)
        whenever(persistenceLayer.getLastTherapyEventId()).thenReturn(30L)
        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(10L)
        whenever(persistenceLayer.getLastTemporaryBasalId()).thenReturn(40L)
        whenever(persistenceLayer.getLastExtendedBolusId()).thenReturn(20L)
        whenever(persistenceLayer.getLastProfileSwitchId()).thenReturn(15L)
        whenever(persistenceLayer.getLastEffectiveProfileSwitchId()).thenReturn(60L)
        whenever(persistenceLayer.getLastRunningModeId()).thenReturn(5L)

        // Mock all the sync preferences to 0
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(0L)

        // Mock all the getNextSyncElement methods to return empty
        whenever(persistenceLayer.getNextSyncElementBolus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementCarbs(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementBolusCalculatorResult(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTemporaryTarget(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementFood(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementGlucoseValue(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTherapyEvent(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementDeviceStatus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTemporaryBasal(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementExtendedBolus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementProfileSwitch(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementEffectiveProfileSwitch(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementRunningMode(0)).thenReturn(Maybe.empty())

        sut.doUpload()

        // Queue counters should be calculated
        assertThat(sut.queueSize()).isEqualTo(1130L) // Sum of all differences
    }

    @Test
    fun doUploadWithPartialSyncTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true)

        whenever(persistenceLayer.getLastBolusId()).thenReturn(100L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(50L)

        // Mock other IDs to 0 or empty
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(0L)
        whenever(persistenceLayer.getLastBolusCalculatorResultId()).thenReturn(0L)
        whenever(persistenceLayer.getLastTemporaryTargetId()).thenReturn(0L)
        whenever(persistenceLayer.getLastFoodId()).thenReturn(0L)
        whenever(persistenceLayer.getLastGlucoseValueId()).thenReturn(0L)
        whenever(persistenceLayer.getLastTherapyEventId()).thenReturn(0L)
        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(0L)
        whenever(persistenceLayer.getLastTemporaryBasalId()).thenReturn(0L)
        whenever(persistenceLayer.getLastExtendedBolusId()).thenReturn(0L)
        whenever(persistenceLayer.getLastProfileSwitchId()).thenReturn(0L)
        whenever(persistenceLayer.getLastEffectiveProfileSwitchId()).thenReturn(0L)
        whenever(persistenceLayer.getLastRunningModeId()).thenReturn(0L)

        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(0L)

        whenever(persistenceLayer.getNextSyncElementBolus(50)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementCarbs(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementBolusCalculatorResult(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTemporaryTarget(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementFood(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementGlucoseValue(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTherapyEvent(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementDeviceStatus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTemporaryBasal(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementExtendedBolus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementProfileSwitch(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementEffectiveProfileSwitch(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementRunningMode(0)).thenReturn(Maybe.empty())

        sut.doUpload()

        // Only boluses should have remaining items (100 - 50 = 50)
        assertThat(sut.queueSize()).isEqualTo(50L)
    }

    @Test
    fun bgUploadEnabledWhenBothConditionsFalseTest() {
        whenever(preferences.get(BooleanKey.BgSourceUploadToNs)).thenReturn(false)
        val nonNsClientSource = object : BgSource {}
        whenever(activePlugin.activeBgSource).thenReturn(nonNsClientSource)

        assertThat(sut.bgUploadEnabled).isFalse()
    }

    @Test
    fun confirmMethodsDoNotUpdateWhenIdIsEqualTest() {
        // Test that confirm methods don't update when new ID equals current ID
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L)
        sut.confirmLastBolusIdIfGreater(5)
        verify(preferences, Times(0)).put(NsclientLongKey.BolusLastSyncedId, 5)

        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(10L)
        sut.confirmLastCarbsIdIfGreater(10)
        verify(preferences, Times(0)).put(NsclientLongKey.CarbsLastSyncedId, 10)
    }

    @Test
    fun confirmMethodsDoNotUpdateWhenIdIsLessTest() {
        // Test that confirm methods don't update when new ID is less than current ID
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(10L)
        sut.confirmLastBolusIdIfGreater(5)
        verify(preferences, Times(0)).put(NsclientLongKey.BolusLastSyncedId, 5)

        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(100L)
        sut.confirmLastGlucoseValueIdIfGreater(50)
        verify(preferences, Times(0)).put(NsclientLongKey.GlucoseValueLastSyncedId, 50)
    }

    @Test
    fun confirmProfileStoreAlwaysUpdatesTest() {
        // ProfileStore always updates regardless of current value
        sut.confirmLastProfileStore(42)
        verify(preferences, Times(1)).put(NsclientLongKey.ProfileStoreLastSyncedId, 42)

        sut.confirmLastProfileStore(10)
        verify(preferences, Times(1)).put(NsclientLongKey.ProfileStoreLastSyncedId, 10)
    }
}
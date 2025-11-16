package app.aaps.implementation.alerts

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.PumpDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.alerts.keys.LocalAlertLongKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.never

class LocalAlertUtilsImplTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var config: Config
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var pump: Pump
    @Mock lateinit var pumpDescription: PumpDescription

    private lateinit var localAlertUtils: LocalAlertUtilsImpl

    private val now = 100000000L

    @BeforeEach
    fun setup() {
        localAlertUtils = LocalAlertUtilsImpl(
            aapsLogger,
            preferences,
            rxBus,
            rh,
            activePlugin,
            profileFunction,
            smsCommunicator,
            config,
            persistenceLayer,
            dateUtil
        )
        `when`(dateUtil.now()).thenReturn(now)
        `when`(activePlugin.activePump).thenReturn(pump)
        `when`(pump.pumpDescription).thenReturn(pumpDescription)
        `when`(pumpDescription.hasCustomUnreachableAlertCheck).thenReturn(false)
        `when`(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), any(), any()))
            .thenReturn(Single.just(true))
    }

    @Test
    fun `preSnoozeAlarms sets next missed readings alarm when expired`() {
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now - 1000)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + 5 * 60 * 1000)
    }

    @Test
    fun `preSnoozeAlarms does not update next missed readings alarm when not expired`() {
        val futureTime = now + 10000
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(futureTime)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences, never()).put(LocalAlertLongKey.NextMissedReadingsAlarm, any())
    }

    @Test
    fun `preSnoozeAlarms sets next pump disconnected alarm when expired`() {
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + 1000)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + 5 * 60 * 1000)
    }

    @Test
    fun `preSnoozeAlarms does not update pump disconnected alarm when not expired`() {
        val futureTime = now + 10000
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(futureTime)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(futureTime)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences, never()).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, any())
    }

    @Test
    fun `preSnoozeAlarms updates both alarms when both expired`() {
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now - 1000)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 2000)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + 5 * 60 * 1000)
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + 5 * 60 * 1000)
    }

    @Test
    fun `shortenSnoozeInterval limits missed readings alarm to threshold`() {
        val thresholdMinutes = 30
        val farFutureAlarm = now + T.hours(5).msecs()

        `when`(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(thresholdMinutes)
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(farFutureAlarm)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now + 1000)
        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(30)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
    }

    @Test
    fun `shortenSnoozeInterval does not change alarm if already within threshold`() {
        val thresholdMinutes = 30
        val alarmTime = now + T.mins(10).msecs()

        `when`(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(thresholdMinutes)
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(alarmTime)
        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(30)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(alarmTime)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, alarmTime)
    }

    @Test
    fun `shortenSnoozeInterval limits pump disconnected alarm to threshold`() {
        val thresholdMinutes = 20
        val farFutureAlarm = now + T.hours(10).msecs()

        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        `when`(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + 1000)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(farFutureAlarm)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
    }

    @Test
    fun `shortenSnoozeInterval handles both alarms correctly`() {
        val missedReadingsThreshold = 25
        val pumpUnreachableThreshold = 35

        `when`(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(missedReadingsThreshold)
        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(pumpUnreachableThreshold)
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + T.hours(1).msecs())
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now + T.hours(2).msecs())

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + T.mins(missedReadingsThreshold.toLong()).msecs())
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + T.mins(pumpUnreachableThreshold.toLong()).msecs())
    }

    @Test
    fun `reportPumpStatusRead updates alarm when profile is available`() {
        val lastDataTime = now - T.mins(5).msecs()
        val thresholdMinutes = 30
        val profile = org.mockito.kotlin.mock<app.aaps.core.interfaces.profile.Profile>()

        `when`(profileFunction.getProfile()).thenReturn(profile)
        `when`(pump.lastDataTime).thenReturn(lastDataTime)
        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)

        localAlertUtils.reportPumpStatusRead()

        val expectedAlarmTime = lastDataTime + T.mins(thresholdMinutes.toLong()).msecs()
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, expectedAlarmTime)
    }

    @Test
    fun `reportPumpStatusRead does not update alarm when profile is null`() {
        `when`(profileFunction.getProfile()).thenReturn(null)

        localAlertUtils.reportPumpStatusRead()

        verify(preferences, never()).put(any<LocalAlertLongKey>(), any<Long>())
    }

    @Test
    fun `reportPumpStatusRead does not decrease alarm time`() {
        val lastDataTime = now - T.mins(5).msecs()
        val thresholdMinutes = 30
        val futureAlarmTime = now + T.hours(2).msecs()
        val profile = org.mockito.kotlin.mock<app.aaps.core.interfaces.profile.Profile>()

        `when`(profileFunction.getProfile()).thenReturn(profile)
        `when`(pump.lastDataTime).thenReturn(lastDataTime)
        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(futureAlarmTime)

        localAlertUtils.reportPumpStatusRead()

        // Should not update because futureAlarmTime is already later than earliestAlarmTime
        verify(preferences, never()).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, any())
    }

    @Test
    fun `preSnoozeAlarms uses exact 5 minute snooze`() {
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now - 1000)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)

        localAlertUtils.preSnoozeAlarms()

        val expectedSnooze = now + 5 * 60 * 1000
        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, expectedSnooze)
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, expectedSnooze)
    }

    @Test
    fun `shortenSnoozeInterval handles edge case with zero threshold`() {
        `when`(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(0)
        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(0)
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + 1000)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now + 1000)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now)
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now)
    }

    @Test
    fun `shortenSnoozeInterval uses minimum of current alarm and threshold`() {
        val thresholdMinutes = 30
        val currentAlarmClose = now + T.mins(10).msecs()
        val currentAlarmFar = now + T.mins(50).msecs()

        `when`(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(thresholdMinutes)
        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)

        // Test with close alarm - should keep it
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(currentAlarmClose)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(currentAlarmClose)
        localAlertUtils.shortenSnoozeInterval()
        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, currentAlarmClose)
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, currentAlarmClose)

        // Test with far alarm - should shorten it
        `when`(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(currentAlarmFar)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(currentAlarmFar)
        localAlertUtils.shortenSnoozeInterval()
        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
    }

    @Test
    fun `reportPumpStatusRead calculates earliest alarm time correctly`() {
        val lastDataTime = now - T.mins(10).msecs()
        val thresholdMinutes = 40
        val currentAlarmTime = now + T.mins(5).msecs()
        val profile = org.mockito.kotlin.mock<app.aaps.core.interfaces.profile.Profile>()

        `when`(profileFunction.getProfile()).thenReturn(profile)
        `when`(pump.lastDataTime).thenReturn(lastDataTime)
        `when`(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        `when`(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(currentAlarmTime)

        localAlertUtils.reportPumpStatusRead()

        val expectedEarliestAlarm = lastDataTime + T.mins(thresholdMinutes.toLong()).msecs()
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, expectedEarliestAlarm)
    }
}

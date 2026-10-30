package app.aaps.database.persistence

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventTreatmentChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.database.AppRepository
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntry
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CompatDBHelperTest {

    private val repository: AppRepository = mock()
    private val bus: RxBus = mock()
    private val context: Context = mock()
    private val ui: UiInteraction = mock()
    private val logger: AAPSLogger = mock()
    private val changes = PublishSubject.create<List<DBEntry>>()
    private val events = mutableListOf<Event>()
    private val helper = CompatDBHelper(logger, repository, bus, context, ui)
    private lateinit var subscription: Disposable

    @BeforeEach
    fun setUp() {
        whenever(repository.changeObservable()).thenReturn(changes)
        doAnswer { events.add(it.getArgument(0)); null }.whenever(bus).send(any())
        subscription = helper.dbChangeDisposable()
    }

    @AfterEach
    fun tearDown() = subscription.dispose()

    @Test
    fun `startup still requests a glucose reload and updates widget`() {
        assertThat(events).hasSize(1)
        assertThat((events.single() as EventNewBG).glucoseValueTimestamp).isNull()
        verify(ui).updateWidget(context, "OnStart")
    }

    @Test
    fun `upload acknowledgement does not cancel calculation but remains observable to sync`() {
        val upstream = changes.test()
        val original = glucose(1, 1000)
        changes.onNext(listOf(original))
        assertGlucoseEvents(1000, 1000)
        events.clear()

        val acknowledgement = original.copy(version = 1, dateCreated = 2000,
            interfaceIDs_backing = InterfaceIDs(nightscoutId = "sync-entry-1"))
        changes.onNext(listOf(acknowledgement))

        assertThat(events).isEmpty()
        upstream.assertValueCount(2)
        assertThat(upstream.values().last().single()).isSameInstanceAs(acknowledgement)
        upstream.dispose()
    }

    @Test
    fun `real glucose correction and invalidation still trigger both events`() {
        val original = glucose(1, 1000)
        changes.onNext(listOf(original))
        events.clear()
        val correction = original.copy(version = 1, value = 95.0)
        changes.onNext(listOf(correction))
        assertGlucoseEvents(1000, 1000)
        events.clear()
        changes.onNext(listOf(correction.copy(version = 2, isValid = false)))
        assertGlucoseEvents(1000, 1000)
    }

    @Test
    fun `mixed batch uses clinical oldest and newest timestamps not acknowledgement timestamp`() {
        val oldest = glucose(1, 1000)
        val newest = glucose(2, 3000)
        changes.onNext(listOf(oldest, newest))
        events.clear()
        changes.onNext(listOf(newest.copy(version = 1), oldest.copy(version = 1, value = 90.0), glucose(3, 2000)))
        assertGlucoseEvents(1000, 2000)
    }

    @Test
    fun `metadata filtering does not suppress treatment history in same batch`() {
        val original = glucose(1, 1000)
        changes.onNext(listOf(original))
        events.clear()
        changes.onNext(listOf(original.copy(version = 1), Carbs(timestamp = 500, duration = 0, amount = 12.0)))
        assertThat(events.filterIsInstance<EventNewBG>()).isEmpty()
        assertThat(events.filterIsInstance<EventTreatmentChange>()).hasSize(1)
        val history = events.filterIsInstance<EventNewHistoryData>().single()
        assertThat(history.oldDataTimestamp).isEqualTo(500)
        assertThat(history.reloadBgData).isFalse()
    }

    @Test
    fun `new subscription does not reuse previous clinical cache`() {
        val original = glucose(1, 1000)
        changes.onNext(listOf(original))
        subscription.dispose()
        subscription = helper.dbChangeDisposable()
        events.clear()
        changes.onNext(listOf(original.copy(version = 1)))
        assertGlucoseEvents(1000, 1000)
    }

    private fun assertGlucoseEvents(oldest: Long, newest: Long) {
        val bg = events.filterIsInstance<EventNewBG>().filter { it.glucoseValueTimestamp != null }.single()
        val history = events.filterIsInstance<EventNewHistoryData>().single()
        assertThat(bg.glucoseValueTimestamp).isEqualTo(newest)
        assertThat(history.oldDataTimestamp).isEqualTo(oldest)
        assertThat(history.newestGlucoseValueTimestamp).isEqualTo(newest)
        assertThat(history.reloadBgData).isTrue()
    }

    private fun glucose(id: Long, time: Long) = GlucoseValue(
        id = id, timestamp = time, utcOffset = 0, raw = 100.0, value = 100.0,
        noise = 0.0, trendArrow = GlucoseValue.TrendArrow.FLAT,
        sourceSensor = GlucoseValue.SourceSensor.LIBRE_2
    )
}

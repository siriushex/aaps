package app.aaps.database.persistence

import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

class GlucoseChangeFilterTest {

    @TestFactory
    fun `every clinical field change is preserved`() = listOf<Pair<String, (GlucoseValue) -> GlucoseValue>>(
        "value" to { it.copy(value = 80.0) },
        "raw" to { it.copy(raw = 90.0) },
        "raw missing" to { it.copy(raw = null) },
        "validity" to { it.copy(isValid = false) },
        "timestamp" to { it.copy(timestamp = 999) },
        "offset" to { it.copy(utcOffset = 3600000) },
        "trend" to { it.copy(trendArrow = GlucoseValue.TrendArrow.SINGLE_DOWN) },
        "noise" to { it.copy(noise = 2.0) },
        "noise missing" to { it.copy(noise = null) },
        "sensor" to { it.copy(sourceSensor = GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE) },
        "reference" to { it.copy(referenceId = 7) }
    ).map { (name, edit) ->
        dynamicTest(name) {
            val filter = GlucoseChangeFilter()
            val original = glucose()
            assertThat(filter.isNewOrChanged(original)).isTrue()
            assertThat(filter.isNewOrChanged(edit(original).copy(version = 1))).isTrue()
        }
    }

    @Test
    fun `only metadata revision is ignored after clinical content is known`() {
        val filter = GlucoseChangeFilter()
        val original = glucose()
        assertThat(filter.isNewOrChanged(original)).isTrue()
        assertThat(filter.isNewOrChanged(original.copy(version = 1, dateCreated = 2000,
            interfaceIDs_backing = InterfaceIDs(nightscoutId = "sync-entry-1")))).isFalse()
        assertThat(filter.isNewOrChanged(original.copy(version = 2))).isFalse()
    }

    @Test
    fun `same value at a new time or new record is not suppressed`() {
        val filter = GlucoseChangeFilter()
        val original = glucose()
        filter.isNewOrChanged(original)
        assertThat(filter.isNewOrChanged(original.copy(id = 2))).isTrue()
        assertThat(filter.isNewOrChanged(original.copy(version = 1, timestamp = 2000))).isTrue()
    }

    @Test
    fun `unknown acknowledgement still triggers recalculation`() {
        assertThat(GlucoseChangeFilter().isNewOrChanged(glucose().copy(version = 8,
            interfaceIDs_backing = InterfaceIDs(nightscoutId = "sync-entry-1")))).isTrue()
    }

    @Test
    fun `caller mutation cannot alter cached clinical content`() {
        val filter = GlucoseChangeFilter()
        val row = glucose()
        filter.isNewOrChanged(row)
        row.value = 70.0
        row.version++
        assertThat(filter.isNewOrChanged(row)).isTrue()
        row.isValid = false
        row.version++
        assertThat(filter.isNewOrChanged(row)).isTrue()
    }

    @Test
    fun `older revisions pass through without overwriting latest snapshot`() {
        val filter = GlucoseChangeFilter()
        val latest = glucose().copy(version = 3, value = 80.0)
        filter.isNewOrChanged(latest)
        assertThat(filter.isNewOrChanged(glucose())).isTrue()
        assertThat(filter.isNewOrChanged(latest.copy(version = 4))).isFalse()
    }

    @Test
    fun `reverting a clinical correction is still a change`() {
        val filter = GlucoseChangeFilter()
        val original = glucose()
        filter.isNewOrChanged(original)
        filter.isNewOrChanged(original.copy(version = 1, value = 90.0))
        assertThat(filter.isNewOrChanged(original.copy(version = 2))).isTrue()
    }

    @Test
    fun `bounded cache passes evicted entries through`() {
        val filter = GlucoseChangeFilter(capacity = 2)
        filter.isNewOrChanged(glucose(1))
        filter.isNewOrChanged(glucose(2))
        assertThat(filter.isNewOrChanged(glucose(1).copy(version = 1))).isFalse()
        filter.isNewOrChanged(glucose(3))
        assertThat(filter.isNewOrChanged(glucose(2).copy(version = 1))).isTrue()
    }

    @Test
    fun `unpersisted records never share cache identity`() {
        val filter = GlucoseChangeFilter()
        assertThat(filter.isNewOrChanged(glucose(0))).isTrue()
        assertThat(filter.isNewOrChanged(glucose(0))).isTrue()
        assertThat(filter.isNewOrChanged(glucose(-1))).isTrue()
    }

    @Test
    fun `new filter starts without trusting previous lifetime`() {
        val row = glucose()
        GlucoseChangeFilter().isNewOrChanged(row)
        assertThat(GlucoseChangeFilter().isNewOrChanged(row.copy(version = 1))).isTrue()
    }

    @Test
    fun `invalid capacity is rejected`() {
        assertThrows<IllegalArgumentException> { GlucoseChangeFilter(0) }
    }

    private fun glucose(id: Long = 1) = GlucoseValue(
        id = id, timestamp = 1000, utcOffset = 0, raw = 100.0, value = 100.0,
        noise = 0.0, trendArrow = GlucoseValue.TrendArrow.FLAT,
        sourceSensor = GlucoseValue.SourceSensor.LIBRE_2
    )
}

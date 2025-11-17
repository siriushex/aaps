package app.aaps.implementation.utils

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class TrendCalculatorImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var autosensDataStore: AutosensDataStore

    private lateinit var trendCalculator: TrendCalculatorImpl

    @BeforeEach
    fun setup() {
        trendCalculator = TrendCalculatorImpl(rh)
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_double_down)).thenReturn("Double Down")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_single_down)).thenReturn("Single Down")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_forty_five_down)).thenReturn("Forty Five Down")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_flat)).thenReturn("Flat")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_forty_five_up)).thenReturn("Forty Five Up")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_single_up)).thenReturn("Single Up")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_double_up)).thenReturn("Double Up")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_none)).thenReturn("None")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_unknown)).thenReturn("Unknown")
    }

    @Test
    fun `getTrendArrow returns null when data is null`() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(null)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isNull()
    }

    @Test
    fun `getTrendArrow returns null when data is empty`() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(mutableListOf())
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isNull()
    }

    @Test
    fun `getTrendArrow returns NONE when only one reading`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.NONE)
    }

    @Test
    fun `getTrendArrow returns existing arrow when value not recalculated`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 100.0, trendArrow = TrendArrow.SINGLE_UP),
            createGlucoseValue(95.0, 700L)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `getTrendArrow calculates DOUBLE_DOWN for slope less than -3_5 per minute`() {
        // Slope = (95 - 100) / (1000 - 700) = -5 / 300 = -0.01666... per ms = -1000 per minute
        // We need slope <= -3.5 per minute, so difference must be at least 3.5 * 300 / 60000 = 0.0175 per ms
        // For 300ms, that's 3.5 * 300 / 60 = 17.5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 80.0),  // 20 mg/dL drop over 300ms = -4000 per minute
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.DOUBLE_DOWN)
    }

    @Test
    fun `getTrendArrow calculates SINGLE_DOWN for slope between -3_5 and -2 per minute`() {
        // For -3 per minute over 300ms: 3 * 300 / 60000 = 0.015 per ms = 4.5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 90.0),  // 10 mg/dL drop over 300ms = -2000 per minute
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_DOWN)
    }

    @Test
    fun `getTrendArrow calculates FORTY_FIVE_DOWN for slope between -2 and -1 per minute`() {
        // For -1.5 per minute over 300ms: 1.5 * 300 / 60000 = 0.0075 per ms = 2.25 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 97.0),  // 3 mg/dL drop over 300ms = -600 per minute
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FORTY_FIVE_DOWN)
    }

    @Test
    fun `getTrendArrow calculates FLAT for slope between -1 and 1 per minute`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 100.0),
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `getTrendArrow calculates FORTY_FIVE_UP for slope between 1 and 2 per minute`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 103.0),  // 3 mg/dL rise over 300ms = 600 per minute
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FORTY_FIVE_UP)
    }

    @Test
    fun `getTrendArrow calculates SINGLE_UP for slope between 2 and 3_5 per minute`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 110.0),  // 10 mg/dL rise over 300ms = 2000 per minute
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `getTrendArrow calculates DOUBLE_UP for slope between 3_5 and 40 per minute`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 120.0),  // 20 mg/dL rise over 300ms = 4000 per minute
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.DOUBLE_UP)
    }

    @Test
    fun `getTrendArrow returns NONE for slope greater than 40 per minute`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 300.0),  // 200 mg/dL rise over 300ms = 40000 per minute
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.NONE)
    }

    @Test
    fun `getTrendArrow handles same timestamp gracefully`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 110.0),
            createGlucoseValue(100.0, 1000L, recalculated = 100.0)  // Same timestamp
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `getTrendArrow recalculates when value differs from recalculated`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 110.0, trendArrow = TrendArrow.FLAT),  // Smoothed value differs
            createGlucoseValue(95.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        // Should recalculate and get SINGLE_UP (10 mg/dL rise over 300ms = 2000 per minute)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `getTrendDescription returns correct description for DOUBLE_DOWN`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 80.0),
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Double Down")
    }

    @Test
    fun `getTrendDescription returns correct description for SINGLE_DOWN`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 90.0),
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Single Down")
    }

    @Test
    fun `getTrendDescription returns correct description for FORTY_FIVE_DOWN`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 97.0),
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Forty Five Down")
    }

    @Test
    fun `getTrendDescription returns correct description for FLAT`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 100.0),
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Flat")
    }

    @Test
    fun `getTrendDescription returns correct description for FORTY_FIVE_UP`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 103.0),
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Forty Five Up")
    }

    @Test
    fun `getTrendDescription returns correct description for SINGLE_UP`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 110.0),
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Single Up")
    }

    @Test
    fun `getTrendDescription returns correct description for DOUBLE_UP`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 120.0),
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Double Up")
    }

    @Test
    fun `getTrendDescription returns correct description for NONE`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("None")
    }

    @Test
    fun `getTrendDescription handles null data`() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(null)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Unknown")
    }

    @Test
    fun `slope boundary test at exactly -3_5 per minute`() {
        // -3.5 per minute over 300ms = -3.5 * 300 / 60000 = -0.0175
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 98.25),  // -1.75 mg/dL over 300ms
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.DOUBLE_DOWN)
    }

    @Test
    fun `slope boundary test at exactly -2 per minute`() {
        // -2 per minute over 300ms = -2 * 300 / 60000 = -0.01
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 99.0),  // -1 mg/dL over 300ms
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_DOWN)
    }

    @Test
    fun `slope boundary test at exactly -1 per minute`() {
        // -1 per minute over 300ms = -1 * 300 / 60000 = -0.005
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 99.5),  // -0.5 mg/dL over 300ms
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FORTY_FIVE_DOWN)
    }

    @Test
    fun `slope boundary test at exactly 1 per minute`() {
        // 1 per minute over 300ms = 1 * 300 / 60000 = 0.005
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 100.5),  // 0.5 mg/dL over 300ms
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `slope boundary test at exactly 2 per minute`() {
        // 2 per minute over 300ms = 2 * 300 / 60000 = 0.01
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 101.0),  // 1 mg/dL over 300ms
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FORTY_FIVE_UP)
    }

    @Test
    fun `slope boundary test at exactly 3_5 per minute`() {
        // 3.5 per minute over 300ms = 3.5 * 300 / 60000 = 0.0175
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L, recalculated = 101.75),  // 1.75 mg/dL over 300ms
            createGlucoseValue(100.0, 700L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    private fun createGlucoseValue(
        value: Double,
        timestamp: Long,
        recalculated: Double = value,
        trendArrow: TrendArrow = TrendArrow.NONE
    ) = InMemoryGlucoseValue(
        timestamp = timestamp,
        value = value,
        trendArrow = trendArrow,
        smoothed = recalculated
    )
}

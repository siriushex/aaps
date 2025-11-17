package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EventTypeTest {

    @Test
    fun `should have all expected event types`() {
        val eventTypes = EventType.entries

        assertThat(eventTypes).isNotEmpty()
        assertThat(eventTypes).contains(EventType.ACTIVATION_EVENT)
        assertThat(eventTypes).contains(EventType.INSULIN_DELIVERY_EVENT)
    }

    @Test
    fun `ACTIVATION_EVENT should have correct value`() {
        assertThat(EventType.ACTIVATION_EVENT.value).isEqualTo(0)
    }

    @Test
    fun `INSULIN_DELIVERY_EVENT should have correct value`() {
        assertThat(EventType.INSULIN_DELIVERY_EVENT.value).isEqualTo(1)
    }

    @Test
    fun `DISCARDED_PATCH should have correct value`() {
        assertThat(EventType.DISCARDED_PATCH.value).isEqualTo(2)
    }

    @Test
    fun `all event types should have unique values`() {
        val values = EventType.entries.map { it.value }
        val uniqueValues = values.toSet()

        assertThat(uniqueValues.size).isEqualTo(values.size)
    }

    @Test
    fun `should support valueOf`() {
        assertThat(EventType.valueOf("ACTIVATION_EVENT")).isEqualTo(EventType.ACTIVATION_EVENT)
        assertThat(EventType.valueOf("INSULIN_DELIVERY_EVENT")).isEqualTo(EventType.INSULIN_DELIVERY_EVENT)
        assertThat(EventType.valueOf("DISCARDED_PATCH")).isEqualTo(EventType.DISCARDED_PATCH)
    }
}

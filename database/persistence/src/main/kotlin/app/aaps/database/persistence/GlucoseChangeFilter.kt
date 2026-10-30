package app.aaps.database.persistence

import app.aaps.database.entities.GlucoseValue

internal class GlucoseChangeFilter(private val capacity: Int = 256) {

    init {
        require(capacity > 0)
    }

    private val recent = object : LinkedHashMap<Long, GlucoseValue>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, GlucoseValue>?): Boolean = size > capacity
    }

    @Synchronized
    fun isNewOrChanged(value: GlucoseValue): Boolean {
        if (value.id <= 0) return true
        val previous = recent[value.id]
        // Unknown or out-of-order revisions must still invalidate calculations.
        if (previous != null && value.version < previous.version) return true
        val changed = previous == null || value.referenceId != previous.referenceId || !value.contentEqualsTo(previous)
        // Room entities are mutable; never retain the caller's instance.
        recent[value.id] = value.copy(interfaceIDs_backing = null)
        return changed
    }
}

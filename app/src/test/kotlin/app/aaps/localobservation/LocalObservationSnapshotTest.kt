package app.aaps.localobservation

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verifyNoMoreInteractions

class LocalObservationSnapshotTest {
    @Test
    fun onlyReadsBoundedHistoryAndRetainsPumpProvenanceWithoutPriming() = runTest {
        val now = 1_800_000_000_000L
        val from = now - 86_400_000L
        val db = mock<PersistenceLayer>()
        val config = ICfg("synthetic insulin", 18_000_000L, 4_500_000L)
        val basal = TB(timestamp = now - 600_000L, utcOffset = 0, type = TB.Type.NORMAL,
            isAbsolute = false, rate = 80.0, duration = 1_800_000L,
            ids = IDs(pumpType = PumpType.ACCU_CHEK_COMBO, pumpSerial = "synthetic", pumpId = 7))
        whenever(db.getBgReadingsDataFromTimeToTime(now - 30 * 60_000L, now, false)).thenReturn(emptyList())
        whenever(db.getBolusesFromTimeToTime(from, now, true)).thenReturn(listOf(
            BS(timestamp = now - 60_000L, amount = 0.5, type = BS.Type.PRIMING, iCfg = config)))
        whenever(db.getTemporaryBasalsStartingFromTimeToTime(from, now, true)).thenReturn(listOf(basal))
        whenever(db.getExtendedBolusesStartingFromTimeToTime(from, now, true)).thenReturn(emptyList())
        val dateUtil = mock<DateUtil>()
        val snapshot = Json.parseToJsonElement(readLocalObservation(db, dateUtil, now).decodeToString()).jsonObject
        val insulin = snapshot.getValue("insulin").jsonArray.single().jsonObject
        assertEquals("-20.0", insulin.getValue("percent").jsonPrimitive.content)
        assertEquals("7", insulin.getValue("pumpId").jsonPrimitive.content)
        assertFalse(snapshot.containsKey("profile"))
        assertFalse(insulin.containsKey("insulin"))
        verify(db).getBgReadingsDataFromTimeToTime(now - 30 * 60_000L, now, false)
        verify(db).getEffectiveProfileSwitchActiveAt(now)
        verify(db).getBolusesFromTimeToTime(from, now, true)
        verify(db).getTemporaryBasalsStartingFromTimeToTime(from, now, true)
        verify(db).getExtendedBolusesStartingFromTimeToTime(from, now, true)
        verifyNoMoreInteractions(db, dateUtil)
    }
}

package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.RunningModeDao
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertOrUpdateRunningModeTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var runningModeDao: RunningModeDao

    @BeforeEach
    fun setup() {
        runningModeDao = mock()
        database = mock()
        whenever(database.runningModeDao).thenReturn(runningModeDao)
    }

    @Test
    fun `inserts new running mode when id not found`() {
        val runningMode = createRunningMode(id = 1, mode = RunningMode.Mode.OPEN)

        whenever(runningModeDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateRunningMode(runningMode)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(runningMode)
        assertThat(result.updated).isEmpty()

        verify(runningModeDao).insertNewEntry(runningMode)
        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing running mode when id found`() {
        val runningMode = createRunningMode(id = 1, mode = RunningMode.Mode.CLOSED)
        val existing = createRunningMode(id = 1, mode = RunningMode.Mode.OPEN)

        whenever(runningModeDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateRunningMode(runningMode)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(runningMode)
        assertThat(result.inserted).isEmpty()

        verify(runningModeDao).updateExistingEntry(runningMode)
        verify(runningModeDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates running mode type`() {
        val existing = createRunningMode(id = 1, mode = RunningMode.Mode.OPEN)
        val updated = createRunningMode(id = 1, mode = RunningMode.Mode.CLOSED)

        whenever(runningModeDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateRunningMode(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].mode).isEqualTo(RunningMode.Mode.CLOSED)
    }

    @Test
    fun `inserts running mode with duration`() {
        val runningMode = createRunningMode(id = 1, mode = RunningMode.Mode.OPEN, duration = 60_000L)

        whenever(runningModeDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateRunningMode(runningMode)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].duration).isEqualTo(60_000L)
    }

    private fun createRunningMode(
        id: Long,
        mode: RunningMode.Mode,
        duration: Long = 0L
    ): RunningMode = RunningMode(
        timestamp = System.currentTimeMillis(),
        mode = mode,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

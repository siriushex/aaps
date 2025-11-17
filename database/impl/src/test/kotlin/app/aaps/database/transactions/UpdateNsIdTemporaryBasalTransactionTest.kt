package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class UpdateNsIdTemporaryBasalTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        database = mock()
        `when`(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
    }

    @Test
    fun `updates NS ID when different`() {
        val newNsId = "new-ns-123"
        val current = createTemporaryBasal(id = 1, nsId = "old-ns")
        val update = createTemporaryBasal(id = 1, nsId = newNsId)

        `when`(temporaryBasalDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdTemporaryBasalTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(temporaryBasalDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() {
        val sameNsId = "same-ns"
        val current = createTemporaryBasal(id = 1, nsId = sameNsId)
        val update = createTemporaryBasal(id = 1, nsId = sameNsId)

        `when`(temporaryBasalDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdTemporaryBasalTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when temporary basal not found`() {
        val update = createTemporaryBasal(id = 999, nsId = "new-ns")

        `when`(temporaryBasalDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdTemporaryBasalTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
    }

    private fun createTemporaryBasal(
        id: Long,
        nsId: String?
    ): TemporaryBasal = TemporaryBasal(
        timestamp = System.currentTimeMillis(),
        rate = 1.5,
        duration = 60_000L,
        type = TemporaryBasal.Type.NORMAL,
        isAbsolute = true,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

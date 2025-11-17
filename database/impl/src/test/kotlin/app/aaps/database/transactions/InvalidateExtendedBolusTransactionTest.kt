package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.ExtendedBolusDao
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class InvalidateExtendedBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var extendedBolusDao: ExtendedBolusDao

    @BeforeEach
    fun setup() {
        extendedBolusDao = mock()
        database = mock()
        `when`(database.extendedBolusDao).thenReturn(extendedBolusDao)
    }

    @Test
    fun `invalidates valid extended bolus`() {
        val eb = createExtendedBolus(id = 1, isValid = true)

        `when`(extendedBolusDao.findById(1)).thenReturn(eb)

        val transaction = InvalidateExtendedBolusTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(eb.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(extendedBolusDao).updateExistingEntry(eb)
    }

    @Test
    fun `does not update already invalid extended bolus`() {
        val eb = createExtendedBolus(id = 1, isValid = false)

        `when`(extendedBolusDao.findById(1)).thenReturn(eb)

        val transaction = InvalidateExtendedBolusTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(extendedBolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when extended bolus not found`() {
        `when`(extendedBolusDao.findById(999)).thenReturn(null)

        val transaction = InvalidateExtendedBolusTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such Extended Bolus with the specified ID.")
        }
    }

    private fun createExtendedBolus(
        id: Long,
        isValid: Boolean
    ): ExtendedBolus = ExtendedBolus(
        timestamp = System.currentTimeMillis(),
        amount = 5.0,
        duration = 120_000L,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

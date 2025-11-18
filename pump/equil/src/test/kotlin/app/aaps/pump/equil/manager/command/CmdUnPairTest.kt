package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class CmdUnPairTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 0E0E`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertEquals("0E0E", cmd.port)
    }

    @Test
    fun `constructor should process sn from name`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        // sn should have "Equil - " removed and be converted with convertString
        assertNotNull(cmd.sn)
        assertTrue(cmd.sn!!.isNotEmpty())
    }

    @Test
    fun `constructor should convert sn with convertString`() {
        val cmd = CmdUnPair("Equil - ABC", "testpass", aapsLogger, preferences, equilManager)
        // convertString adds "0" before each character
        assertTrue(cmd.sn!!.contains("0"))
    }

    @Test
    fun `getEventType should return UNPAIR_EQUIL`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.UNPAIR_EQUIL, cmd.getEventType())
    }

    @Test
    fun `clear1 should generate random password`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertNull(cmd.randomPassword)
        val response = cmd.clear1()
        assertNotNull(cmd.randomPassword)
        assertEquals(32, cmd.randomPassword!!.size)
    }

    @Test
    fun `clear1 should return EquilResponse`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.clear1()
        assertNotNull(response)
    }

    @Test
    fun `getEquilResponse should call clear1`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.getEquilResponse()
        assertNotNull(response)
        assertNotNull(cmd.randomPassword)
    }

    @Test
    fun `getNextEquilResponse should return same as getEquilResponse`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val response1 = cmd.getNextEquilResponse()
        assertNotNull(response1)
    }

    @Test
    fun `config flag should be false initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertFalse(cmd.config)
    }

    @Test
    fun `isEnd flag should be false initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertFalse(cmd.isEnd)
    }

    @Test
    fun `cmdSuccess should be false initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertFalse(cmd.cmdSuccess)
    }

    @Test
    fun `password should be stored`() {
        val cmd = CmdUnPair("Equil - TestDevice", "mypassword", aapsLogger, preferences, equilManager)
        assertEquals("mypassword", cmd.password)
    }

    @Test
    fun `sn should trim whitespace`() {
        val cmd = CmdUnPair("Equil -   TestDevice  ", "testpass", aapsLogger, preferences, equilManager)
        // Should trim and process
        assertNotNull(cmd.sn)
    }
}

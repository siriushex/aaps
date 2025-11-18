package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class BaseSettingTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    private lateinit var testSetting: TestBaseSetting

    // Concrete implementation for testing
    private inner class TestBaseSetting(createTime: Long = System.currentTimeMillis()) :
        BaseSetting(createTime, aapsLogger, preferences, equilManager) {

        var firstDataCalled = false
        var nextDataCalled = false
        var decodeConfirmDataCalled = false

        override fun getFirstData(): ByteArray {
            firstDataCalled = true
            return byteArrayOf(0x01, 0x02, 0x03, 0x04)
        }

        override fun getNextData(): ByteArray {
            nextDataCalled = true
            return byteArrayOf(0x05, 0x06, 0x07, 0x08)
        }

        override fun decodeConfirmData(data: ByteArray) {
            decodeConfirmDataCalled = true
        }

        override fun getEventType(): EquilHistoryRecord.EventType? {
            return EquilHistoryRecord.EventType.SET_BASAL_PROFILE
        }
    }

    @BeforeEach
    fun setUp() {
<<<<<<< HEAD
        MockitoAnnotations.openMocks(this)

        // Mock preferences to return test values
        `when`(preferences.get(any<StringPreferenceKey>())).thenReturn("")

=======
>>>>>>> a801cb123 (Refactor all Equil unit tests to extend TestBase/TestBaseWithProfile)
        testSetting = TestBaseSetting()
    }

    @Test
    fun `getReqData should increment pumpReqIndex`() {
        val initialIndex = BaseCmd.pumpReqIndex
        testSetting.getReqData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getReqData should return byte array with correct structure`() {
        val result = testSetting.getReqData()

        assertNotNull(result)
        // Should contain index bytes (4 bytes) + device bytes
        assertTrue(result.size >= 4)
    }

    @Test
    fun `getFirstData should be called by subclass`() {
        assertFalse(testSetting.firstDataCalled)
        testSetting.getFirstData()
        assertTrue(testSetting.firstDataCalled)
    }

    @Test
    fun `getNextData should be called by subclass`() {
        assertFalse(testSetting.nextDataCalled)
        testSetting.getNextData()
        assertTrue(testSetting.nextDataCalled)
    }

    @Test
    fun `decodeConfirmData should be called by subclass`() {
        assertFalse(testSetting.decodeConfirmDataCalled)
        testSetting.decodeConfirmData(byteArrayOf(0x00))
        assertTrue(testSetting.decodeConfirmDataCalled)
    }

    @Test
    fun `getEventType should return correct event type`() {
        assertEquals(EquilHistoryRecord.EventType.SET_BASAL_PROFILE, testSetting.getEventType())
    }

    @Test
    fun `config flag should be false initially`() {
        assertFalse(testSetting.config)
    }

    @Test
    fun `isEnd flag should be false initially`() {
        assertFalse(testSetting.isEnd)
    }

    @Test
    fun `cmdSuccess should be true initially (enacted default)`() {
        // enacted defaults to true in BaseCmd
        assertTrue(testSetting.enacted)
    }

    @Test
    fun `createTime should be set from constructor`() {
        val testTime = 123456789L
        val setting = TestBaseSetting(testTime)
        assertEquals(testTime, setting.createTime)
    }

    @Test
    fun `port should have default value`() {
        assertEquals("0404", testSetting.port)
    }

    @Test
    fun `response should be null initially`() {
        assertNull(testSetting.response)
    }

    @Test
    fun `runPwd should be null initially`() {
        assertNull(testSetting.runPwd)
    }

    @Test
    fun `runCode should be null initially`() {
        assertNull(testSetting.runCode)
    }

    @Test
    fun `timeout values should be set`() {
        assertEquals(22000, testSetting.timeOut)
        assertEquals(15000, testSetting.connectTimeOut)
    }

    // Helper method for mockito any() matcher
    private fun <T> any(): T {
        org.mockito.Mockito.any<T>()
        return null as T
    }
}

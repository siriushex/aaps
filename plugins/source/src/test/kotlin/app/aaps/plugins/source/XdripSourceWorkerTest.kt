package app.aaps.plugins.source

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.keys.BooleanKey
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.shared.tests.BundleMock
import app.aaps.shared.tests.TestBaseWithProfile
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class XdripSourceWorkerTest : TestBaseWithProfile() {

    private lateinit var worker: XdripSourcePlugin.XdripSourceWorker
    @Mock lateinit var xdripSourcePlugin: XdripSourcePlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var workerParameters: WorkerParameters
    @Mock lateinit var dataWorkerStorage: DataWorkerStorage

    init {
        addInjector { injector ->
            if (injector is XdripSourcePlugin.XdripSourceWorker) {
                injector.aapsLogger = aapsLogger
                injector.xdripSourcePlugin = xdripSourcePlugin
                injector.persistenceLayer = persistenceLayer
                injector.dataWorkerStorage = dataWorkerStorage
                injector.dateUtil = dateUtil
                injector.preferences = preferences
            }
        }
    }

    @BeforeEach
    fun setupMock() {
        whenever(workerParameters.inputData).thenReturn(workDataOf(DataWorkerStorage.STORE_KEY to 1L))
        worker = XdripSourcePlugin.XdripSourceWorker(context, workerParameters)
    }

    @Test
    fun `When plugin disabled then return success`() {
        runBlocking {
            whenever(xdripSourcePlugin.isEnabled()).thenReturn(false)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(workDataOf("Result" to "Plugin not enabled")), result)
            verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), any())
        }
    }

    @Test
    fun `When plugin enabled then insert G6 data`() {
        val timestamp = now - 60000
        runBlocking {
            whenever(xdripSourcePlugin.isEnabled()).thenReturn(true)
            whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            val bundle = BundleMock.mocked().apply {
                putString(Intents.XDRIP_DATA_SOURCE, "G6 Native")
                putLong(Intents.EXTRA_TIMESTAMP, timestamp)
                putLong(Intents.EXTRA_SENSOR_STARTED_AT, timestamp)
                putDouble(Intents.EXTRA_BG_ESTIMATE, 150.0)
                putDouble(Intents.EXTRA_RAW, 150.0)
                putString(Intents.EXTRA_BG_SLOPE_NAME, "FortyFiveDown")
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp,
                value = 150.0,
                raw = 150.0,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE_XDRIP
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.Xdrip, listOf(expectedGv), emptyList(), timestamp)
        }
    }

    @Test
    fun `When bundle is missing then return failure`() {
        runBlocking {
            whenever(xdripSourcePlugin.isEnabled()).thenReturn(true)
            whenever(dataWorkerStorage.pickupBundle(1L)).thenReturn(null)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.failure(workDataOf("Error" to "missing input data")), result)
        }
    }

    @Test
    fun `When glucoseValues are missing then return failure`() {
        runBlocking {
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            whenever(xdripSourcePlugin.isEnabled()).thenReturn(true)
            val bundle = BundleMock.mocked().apply {
                putString("sensorType", "G6")
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.failure(workDataOf("Error" to "missing glucoseValue")), result)
        }
    }

    @Test
    fun `When plugin enabled then insert xDrip Sibionics2 source as SIBIONIC`() {
        val timestamp = now - 60000
        runBlocking {
            whenever(xdripSourcePlugin.isEnabled()).thenReturn(true)
            whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(false)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            val bundle = BundleMock.mocked().apply {
                putString(Intents.XDRIP_DATA_SOURCE, "Sibionics 2")
                putLong(Intents.EXTRA_TIMESTAMP, timestamp)
                putDouble(Intents.EXTRA_BG_ESTIMATE, 152.0)
                putDouble(Intents.EXTRA_RAW, 152.0)
                putString(Intents.EXTRA_BG_SLOPE_NAME, "Flat")
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp,
                value = 152.0,
                raw = 152.0,
                noise = null,
                trendArrow = TrendArrow.FLAT,
                sourceSensor = SourceSensor.SIBIONIC
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.Xdrip, listOf(expectedGv), emptyList(), null)
        }
    }

    @Test
    fun `When plugin enabled then insert Juggluco Sibionics data`() {
        val timestamp = now - 60000
        runBlocking {
            whenever(workerParameters.inputData).thenReturn(
                workDataOf(
                    DataWorkerStorage.STORE_KEY to 1L,
                    DataWorkerStorage.ACTION_KEY to Intents.JUGGLUCO_BG
                )
            )
            worker = XdripSourcePlugin.XdripSourceWorker(context, workerParameters)
            whenever(xdripSourcePlugin.isEnabled()).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            val bundle = BundleMock.mocked().apply {
                putInt(Intents.JUGGLUCO_BG_MGDL, 168)
                putFloat(Intents.JUGGLUCO_BG_RATE, -1.6f)
                putLong(Intents.JUGGLUCO_BG_TIME, timestamp)
                putString(Intents.JUGGLUCO_BG_SERIAL, "Sibionics 2")
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp,
                value = 168.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.SIBIONIC
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.Xdrip, listOf(expectedGv), emptyList(), null)
        }
    }

    @Test
    fun `When plugin enabled then insert Juggluco data with short keys`() {
        val timestamp = now - 120000
        runBlocking {
            whenever(workerParameters.inputData).thenReturn(
                workDataOf(
                    DataWorkerStorage.STORE_KEY to 1L,
                    DataWorkerStorage.ACTION_KEY to Intents.JUGGLUCO_BG
                )
            )
            worker = XdripSourcePlugin.XdripSourceWorker(context, workerParameters)
            whenever(xdripSourcePlugin.isEnabled()).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            val bundle = BundleMock.mocked().apply {
                putFloat("mgdl", 137.0f)
                putFloat("Rate", 0.2f)
                putLong("time", timestamp)
                putString("serial", "Sibionics 2")
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp,
                value = 137.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FLAT,
                sourceSensor = SourceSensor.SIBIONIC
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.Xdrip, listOf(expectedGv), emptyList(), null)
        }
    }
}

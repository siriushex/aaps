package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ListenableWorker.Result.Success
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.shared.tests.TestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@ExperimentalCoroutinesApi
internal class DataSyncWorkerTest : TestBase() {

    abstract class ContextWithInjector : Context(), HasAndroidInjector

    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var nsClient: NsClient
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock lateinit var context: ContextWithInjector

    private lateinit var sut: DataSyncWorker

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is DataSyncWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.dataSyncSelectorV3 = dataSyncSelectorV3
                it.activePlugin = activePlugin
                it.rxBus = rxBus
                it.nsClientV3Plugin = nsClientV3Plugin
            }
        }
    }

    @BeforeEach
    fun prepare() {
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.androidInjector()).thenReturn(injector.androidInjector())
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)
    }

    @Test
    fun doWorkAndLog() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClient.hasWritePermission).thenReturn(false)
        sut.doWorkAndLog()
        verify(dataSyncSelectorV3, times(0)).doUpload()

        whenever(nsClient.hasWritePermission).thenReturn(true)
        val result = sut.doWorkAndLog()
        verify(dataSyncSelectorV3, times(1)).doUpload()
        assertIs<Success>(result)
    }
}

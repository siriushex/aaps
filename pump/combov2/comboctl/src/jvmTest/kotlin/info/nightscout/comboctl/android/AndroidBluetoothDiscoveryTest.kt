package info.nightscout.comboctl.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import info.nightscout.comboctl.base.BluetoothInterface.DiscoveryStoppedReason
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AndroidBluetoothDiscoveryTest {

    @Test
    fun `an outgoing inquiry ending does not end the Combo search`() = withDiscovery { receiver, filter, context, stopped, _ ->
        assertFalse(filter.hasAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        receiver.onReceive(context, broadcast(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        assertTrue(stopped.isEmpty())
    }

    @Test
    fun `losing discoverability times out once but a mode before consent does not`() = withDiscovery { receiver, _, context, stopped, deadline ->
        val notDiscoverable = scanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE)
        receiver.onReceive(context, notDiscoverable)
        assertTrue(stopped.isEmpty())
        receiver.onReceive(context, scanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE))
        receiver.onReceive(context, notDiscoverable)
        receiver.onReceive(context, notDiscoverable)
        deadline.run()
        assertEquals(listOf(DiscoveryStoppedReason.DISCOVERY_TIMEOUT), stopped)
    }

    @Test
    fun `missing scan mode broadcasts still have a bounded timeout`() = withDiscovery { _, _, _, stopped, deadline ->
        deadline.run()
        assertEquals(listOf(DiscoveryStoppedReason.DISCOVERY_TIMEOUT), stopped)
    }

    private fun withDiscovery(
        check: (BroadcastReceiver, IntentFilter, Context, List<DiscoveryStoppedReason>, Runnable) -> Unit
    ) {
        // The local JVM uses Android stubs, so capture registered filters and the deadline.
        mockConstruction(IntentFilter::class.java) { filter, _ ->
            val actions = mutableSetOf<String>()
            whenever(filter.addAction(any())).thenAnswer { actions.add(it.getArgument(0)); null }
            whenever(filter.hasAction(any())).thenAnswer { it.getArgument<String>(0) in actions }
        }.use {
            mockConstruction(Handler::class.java).use { handlers ->
                val context = mock<Context>()
                val manager = mock<BluetoothManager>()
                val adapter = mock<BluetoothAdapter>()
                val socket = mock<BluetoothServerSocket>()
                whenever(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(manager)
                whenever(adapter.isEnabled).thenReturn(true)
                whenever(adapter.state).thenReturn(BluetoothAdapter.STATE_ON)
                whenever(adapter.bondedDevices).thenReturn(emptySet())
                whenever(manager.adapter).thenReturn(adapter)
                whenever(adapter.listenUsingInsecureRfcommWithServiceRecord(any(), any())).thenReturn(socket)
                whenever(socket.accept()).thenThrow(IOException("Listener closed"))
                val stopped = mutableListOf<DiscoveryStoppedReason>()
                val bluetooth = AndroidBluetoothInterface(context)
                bluetooth.setup()
                try {
                    bluetooth.startDiscovery("test", "test", "test", "0000", 30, stopped::add) {
                        error("No pump was expected")
                    }
                    val receiver = argumentCaptor<BroadcastReceiver>()
                    val filter = it.constructed().last()
                    verify(context).registerReceiver(receiver.capture(), eq(filter))
                    val handler = handlers.constructed().single()
                    val deadline = argumentCaptor<Runnable>()
                    verify(handler).postDelayed(deadline.capture(), eq(90_000L))
                    check(receiver.singleValue, filter, context, stopped, deadline.singleValue)
                    bluetooth.stopDiscovery()
                    verify(handler).removeCallbacks(deadline.singleValue)
                    val afterStop = stopped.toList()
                    deadline.singleValue.run()
                    assertEquals(afterStop, stopped)
                } finally {
                    bluetooth.stopDiscovery()
                    bluetooth.teardown()
                }
            }
        }
    }

    private fun broadcast(action: String) = mock<Intent>().also {
        whenever(it.action).thenReturn(action)
    }

    private fun scanMode(mode: Int) = broadcast(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED).also {
        whenever(it.getIntExtra(eq(BluetoothAdapter.EXTRA_SCAN_MODE), any())).thenReturn(mode)
        whenever(it.getIntExtra(eq(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE), any())).thenReturn(BluetoothAdapter.ERROR)
    }
}

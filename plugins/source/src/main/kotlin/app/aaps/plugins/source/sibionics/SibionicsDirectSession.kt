package app.aaps.plugins.source.sibionics

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.source.keys.SibionicsDirectBooleanKey
import app.aaps.plugins.source.keys.SibionicsDirectIntKey
import app.aaps.plugins.source.keys.SibionicsDirectLongKey
import app.aaps.plugins.source.keys.SibionicsDirectStringKey
import java.util.Locale
import kotlin.math.roundToInt

internal class SibionicsDirectSession(
    private val context: Context,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) {

    private data class Config(
        val enabled: Boolean,
        val subtype: Int,
        val pollIntervalSeconds: Int,
        val deviceName: String,
        val deviceAddress: String,
        val resetRequested: Boolean
    )

    private data class ParsedRecord(
        val index: Int,
        val timestampMillis: Long,
        val valueMgdl: Double,
        val trendRate: Double
    )

    private var config = readConfig()
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private var gatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null
    private var scanRunning = false

    private var bridge: SibionicsNativeBridge? = null
    private var nextIndex: Int = preferences.get(SibionicsDirectIntKey.NextIndex)
    private var pollArmed: Boolean = false

    private val scanTimeoutRunnable = Runnable {
        stopScanLocked()
        if (config.enabled) {
            aapsLogger.debug(LTag.BGSOURCE, "Sibionics direct: scan timeout, retrying")
            scheduleReconnectLocked(3000L)
        }
    }

    private val askRunnable = object : Runnable {
        override fun run() {
            if (pollArmed && gatt != null && writeCharacteristic != null) {
                askForDataLocked()
                handler?.postDelayed(this, config.pollIntervalSeconds * 1000L)
            }
        }
    }

    private val configWatcherRunnable = object : Runnable {
        override fun run() {
            val updated = readConfig()
            if (updated != config) {
                config = updated
                aapsLogger.debug(LTag.BGSOURCE, "Sibionics direct: config changed, restarting session")
                restartLocked()
            }
            handler?.postDelayed(this, 10000L)
        }
    }

    fun start() {
        if (handlerThread != null) return
        handlerThread = HandlerThread("SibionicsDirectSession").also { it.start() }
        handler = Handler(handlerThread!!.looper)
        handler?.post {
            config = readConfig()
            nextIndex = preferences.get(SibionicsDirectIntKey.NextIndex)
            restartLocked()
            handler?.post(configWatcherRunnable)
        }
    }

    fun stop() {
        val localHandler = handler ?: return
        localHandler.post {
            localHandler.removeCallbacksAndMessages(null)
            disconnectLocked()
            stopScanLocked()
        }
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    private fun restartLocked() {
        disconnectLocked()
        stopScanLocked()
        pollArmed = false
        handler?.removeCallbacks(askRunnable)
        if (!config.enabled) return
        if (!hasBlePermissions()) {
            aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: missing Bluetooth permissions")
            return
        }
        if (config.deviceName.isBlank() && config.deviceAddress.isBlank()) {
            aapsLogger.debug(LTag.BGSOURCE, "Sibionics direct: no device configured")
            return
        }
        bridge = if (config.subtype == 2) null else SibionicsNativeBridge(context, config.subtype, aapsLogger).also {
            if (!it.isAvailable()) {
                aapsLogger.error(
                    LTag.BGSOURCE,
                    "Sibionics direct: native bridge unavailable for subtype=${config.subtype}. Install matching official SI app package."
                )
            }
        }
        startScanLocked()
    }

    private fun scheduleReconnectLocked(delayMillis: Long) {
        handler?.postDelayed({ startScanLocked() }, delayMillis)
    }

    @SuppressLint("MissingPermission")
    private fun startScanLocked() {
        if (scanRunning || gatt != null) return
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
        val scanner = adapter?.bluetoothLeScanner ?: run {
            aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: Bluetooth scanner unavailable")
            return
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = device.name ?: result.scanRecord?.deviceName ?: ""
                val address = device.address ?: ""
                if (matchesConfiguredDevice(name, address)) {
                    handler?.post {
                        aapsLogger.debug(LTag.BGSOURCE, "Sibionics direct: found device $name $address")
                        connectLocked(device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                handler?.post {
                    scanRunning = false
                    aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: BLE scan failed error=$errorCode")
                    scheduleReconnectLocked(5000L)
                }
            }
        }
        scanCallback = callback

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SibionicsDirectCodec.serviceUuid))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(filters, settings, callback)
        scanRunning = true
        aapsLogger.debug(LTag.BGSOURCE, "Sibionics direct: scanning started")
        handler?.removeCallbacks(scanTimeoutRunnable)
        handler?.postDelayed(scanTimeoutRunnable, 25000L)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanLocked() {
        if (!scanRunning) return
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
        val scanner = adapter?.bluetoothLeScanner
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (_: Throwable) {
            // Ignore scanner stop races.
        }
        scanRunning = false
        scanCallback = null
        handler?.removeCallbacks(scanTimeoutRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun connectLocked(device: BluetoothDevice) {
        stopScanLocked()
        disconnectLocked()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectLocked() {
        handler?.removeCallbacks(askRunnable)
        pollArmed = false
        notifyCharacteristic = null
        writeCharacteristic = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Throwable) {
            // Ignore disconnect races.
        }
        gatt = null
    }

    @SuppressLint("MissingPermission")
    private fun onGattReadyLocked(bluetoothGatt: BluetoothGatt) {
        val service = bluetoothGatt.getService(SibionicsDirectCodec.serviceUuid)
        val notify = service?.getCharacteristic(SibionicsDirectCodec.notifyCharacteristicUuid)
        val write = service?.getCharacteristic(SibionicsDirectCodec.writeCharacteristicUuid)
        if (service == null || notify == null || write == null) {
            aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: required service/characteristics not found")
            scheduleReconnectLocked(3000L)
            disconnectLocked()
            return
        }
        notifyCharacteristic = notify
        writeCharacteristic = write
        bluetoothGatt.setCharacteristicNotification(notify, true)
        val descriptor = notify.getDescriptor(SibionicsDirectCodec.notificationDescriptorUuid)
        if (descriptor == null || !writeDescriptorCompat(bluetoothGatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
            aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: failed to enable notifications")
            scheduleReconnectLocked(3000L)
            disconnectLocked()
        }
    }

    private fun onNotificationsEnabledLocked() {
        if (config.resetRequested && config.subtype == 3) {
            sendResetLocked()
            preferences.put(SibionicsDirectBooleanKey.ResetRequested, false)
            config = readConfig()
        }
        pollArmed = true
        handler?.removeCallbacks(askRunnable)
        if (config.subtype != 2) {
            if (!sendAuthLocked()) {
                aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: auth not available, fallback to polling")
                askForDataLocked()
            }
            handler?.postDelayed(askRunnable, config.pollIntervalSeconds * 1000L)
        } else {
            askForDataLocked()
            handler?.postDelayed(askRunnable, config.pollIntervalSeconds * 1000L)
        }
    }

    private fun handleNotificationLocked(payload: ByteArray) {
        if (payload.isEmpty()) return

        val parsedRecords = ArrayList<ParsedRecord>()
        if (config.subtype == 2) {
            val result = SibionicsDirectCodec.parseChineseNotification(payload, dateUtil.now() / 1000L)
            when (result.status) {
                SibionicsDirectCodec.Status.AUTH_REQUIRED -> {
                    handler?.postDelayed({ sendAuthLocked() }, 1000L)
                }
                SibionicsDirectCodec.Status.DATA          ->
                    parsedRecords.addAll(result.records.map {
                        ParsedRecord(
                            index = it.index,
                            timestampMillis = it.timestampMillis,
                            valueMgdl = it.valueMgdl,
                            trendRate = it.trendRate
                        )
                    })

                SibionicsDirectCodec.Status.RETRY         -> askForDataLocked()
                SibionicsDirectCodec.Status.INVALID_PACKET,
                SibionicsDirectCodec.Status.NONE          -> {
                    // Ignore malformed/unrelated packets.
                }
            }
        } else {
            val split = bridge?.splitData(payload)
            if (split == null) {
                if (SibionicsDirectCodec.isAuthChallenge(payload)) sendAuthLocked()
                return
            }
            split.records.forEach { record ->
                parsedRecords += ParsedRecord(
                    index = record.index,
                    timestampMillis = record.timestampMillis,
                    valueMgdl = ((record.glucoseMmol * 18.0) * 10.0).roundToInt() / 10.0,
                    trendRate = record.trend * 1.3
                )
            }
            when (split.control) {
                SibionicsNativeBridge.Control.REAUTH    -> handler?.postDelayed({ sendAuthLocked() }, 1000L)
                SibionicsNativeBridge.Control.SEND_TIME -> sendTimeLocked()
                SibionicsNativeBridge.Control.ACTIVATE  -> sendActivationLocked()
                SibionicsNativeBridge.Control.ASK_VALUES -> askForDataLocked()
                SibionicsNativeBridge.Control.RESET     -> sendResetLocked()
                SibionicsNativeBridge.Control.NONE      -> {
                    // keep polling
                }
            }
            if (split.rawJson.isNotBlank()) {
                aapsLogger.debug(LTag.BGSOURCE, "Sibionics direct split code=${split.rawCode} json=${split.rawJson.take(180)}")
            }
        }

        if (parsedRecords.isNotEmpty()) {
            persistRecords(parsedRecords)
        }
    }

    private fun persistRecords(records: List<ParsedRecord>) {
        val now = dateUtil.now()
        var lastTimestamp = preferences.get(SibionicsDirectLongKey.LastTimestamp)
        val gvList = records
            .sortedBy { it.timestampMillis }
            .filter { it.timestampMillis > lastTimestamp && it.timestampMillis <= now + 60_000L }
            .map {
                GV(
                    timestamp = it.timestampMillis,
                    value = it.valueMgdl,
                    raw = null,
                    noise = null,
                    trendArrow = trendArrowFromRate(it.trendRate),
                    sourceSensor = SourceSensor.SIBIONIC
                )
            }

        if (gvList.isEmpty()) return
        runCatching {
            persistenceLayer.insertCgmSourceData(Sources.SiBionic, gvList, emptyList(), null).blockingGet()
        }.onFailure {
            aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: failed to persist glucose values", it)
        }

        val maxTimestamp = gvList.maxOf { it.timestamp }
        preferences.put(SibionicsDirectLongKey.LastTimestamp, maxTimestamp)
        lastTimestamp = maxTimestamp

        val maxIndex = records.maxOfOrNull { it.index }
        maxIndex?.let {
            nextIndex = maxOf(nextIndex, it + 1)
            preferences.put(SibionicsDirectIntKey.NextIndex, nextIndex)
        }
        aapsLogger.debug(LTag.BGSOURCE, "Sibionics direct: stored ${gvList.size} values, lastTs=$lastTimestamp nextIndex=$nextIndex")
    }

    @SuppressLint("MissingPermission")
    private fun askForDataLocked() {
        val command = when {
            config.subtype == 2 -> {
                val mac = gatt?.device?.address ?: ""
                SibionicsDirectCodec.buildAskDataCommand(nextIndex, mac)
            }

            else                -> bridge?.buildAskDataBytes(nextIndex)
        } ?: return
        writeCommandLocked(command)
    }

    @SuppressLint("MissingPermission")
    private fun sendTimeLocked(): Boolean {
        val command = bridge?.buildTimeBytes(dateUtil.now() / 1000L) ?: return false
        return writeCommandLocked(command)
    }

    @SuppressLint("MissingPermission")
    private fun sendActivationLocked(): Boolean {
        val command = bridge?.buildActivationBytes(dateUtil.now() / 1000L) ?: return false
        return writeCommandLocked(command)
    }

    @SuppressLint("MissingPermission")
    private fun sendResetLocked(): Boolean {
        val command = bridge?.buildResetBytes() ?: return false
        return writeCommandLocked(command)
    }

    @SuppressLint("MissingPermission")
    private fun sendAuthLocked(): Boolean {
        val mac = gatt?.device?.address ?: return false
        val command = bridge?.buildAuthBytes(mac) ?: return false
        return writeCommandLocked(command)
    }

    @SuppressLint("MissingPermission")
    private fun writeCommandLocked(command: ByteArray): Boolean {
        val bluetoothGatt = gatt ?: return false
        val characteristic = writeCharacteristic ?: return false
        return writeCharacteristicCompat(bluetoothGatt, characteristic, command)
    }

    private fun hasBlePermissions(): Boolean {
        val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val connectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return scanGranted && connectGranted
    }

    private fun matchesConfiguredDevice(name: String, address: String): Boolean {
        if (config.deviceAddress.isNotBlank() && address.equals(config.deviceAddress, ignoreCase = true)) return true
        if (config.deviceName.isBlank()) return false
        val expected = config.deviceName.lowercase(Locale.US)
        val tested = name.lowercase(Locale.US)
        return tested == expected || tested.endsWith(expected)
    }

    private fun readConfig(): Config = Config(
        enabled = preferences.get(SibionicsDirectBooleanKey.Enabled),
        subtype = preferences.get(SibionicsDirectIntKey.Subtype).coerceIn(0, 3),
        pollIntervalSeconds = preferences.get(SibionicsDirectIntKey.PollIntervalSeconds).coerceIn(15, 300),
        deviceName = preferences.get(SibionicsDirectStringKey.DeviceName).trim(),
        deviceAddress = preferences.get(SibionicsDirectStringKey.DeviceAddress).trim().uppercase(Locale.US),
        resetRequested = preferences.get(SibionicsDirectBooleanKey.ResetRequested)
    )

    private fun trendArrowFromRate(rate: Double): TrendArrow = when {
        rate >= 3.0  -> TrendArrow.TRIPLE_UP
        rate >= 2.0  -> TrendArrow.DOUBLE_UP
        rate >= 1.0  -> TrendArrow.SINGLE_UP
        rate >= 0.3  -> TrendArrow.FORTY_FIVE_UP
        rate <= -3.0 -> TrendArrow.TRIPLE_DOWN
        rate <= -2.0 -> TrendArrow.DOUBLE_DOWN
        rate <= -1.0 -> TrendArrow.SINGLE_DOWN
        rate <= -0.3 -> TrendArrow.FORTY_FIVE_DOWN
        else         -> TrendArrow.FLAT
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristicCompat(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        bluetoothGatt.writeCharacteristic(
            characteristic,
            value,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        bluetoothGatt.writeCharacteristic(characteristic)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(
        bluetoothGatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        bluetoothGatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        descriptor.value = value
        bluetoothGatt.writeDescriptor(descriptor)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(bluetoothGatt: BluetoothGatt, status: Int, newState: Int) {
            handler?.post {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    @SuppressLint("MissingPermission")
                    bluetoothGatt.discoverServices()
                } else {
                    aapsLogger.debug(LTag.BGSOURCE, "Sibionics direct: disconnected status=$status state=$newState")
                    disconnectLocked()
                    if (config.enabled) scheduleReconnectLocked(2500L)
                }
            }
        }

        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
            handler?.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onGattReadyLocked(bluetoothGatt)
                } else {
                    aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: service discovery failed status=$status")
                    disconnectLocked()
                    scheduleReconnectLocked(3000L)
                }
            }
        }

        override fun onDescriptorWrite(bluetoothGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler?.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onNotificationsEnabledLocked()
                } else {
                    aapsLogger.error(LTag.BGSOURCE, "Sibionics direct: descriptor write failed status=$status")
                    disconnectLocked()
                    scheduleReconnectLocked(3000L)
                }
            }
        }

        override fun onCharacteristicChanged(bluetoothGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: return
            handler?.post { handleNotificationLocked(value) }
        }
    }
}

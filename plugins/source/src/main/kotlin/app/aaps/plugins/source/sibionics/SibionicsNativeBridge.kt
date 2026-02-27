package app.aaps.plugins.source.sibionics

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.math.max

internal class SibionicsNativeBridge(
    private val context: Context,
    private val subtype: Int,
    private val aapsLogger: AAPSLogger
) {

    enum class Control {
        NONE,
        REAUTH,
        SEND_TIME,
        ACTIVATE,
        ASK_VALUES,
        RESET
    }

    data class Record(
        val index: Int,
        val timestampMillis: Long,
        val glucoseMmol: Double,
        val trend: Int
    )

    data class SplitResult(
        val rawCode: Int,
        val control: Control,
        val records: List<Record>,
        val rawJson: String
    )

    private data class Bundle(
        val packageName: String,
        val appId: String,
        val appKey: String
    )

    private val bundle: Bundle? = when (subtype) {
        0    -> Bundle(
            packageName = "com.sisensing.sijoy",
            appId = "com.sisensing.sijoy",
            appKey = "56CE249349040C94F8B4B2375A8752D5CBE7A17814B502D9132489C0BFDFC99F0CAC670E8CBB085AF1C780B3D282E3"
        )

        1    -> Bundle(
            packageName = "com.sisensing.rusibionics",
            appId = "com.sisensing.rusibionics",
            appKey = "60B05FEB7C0A148DEED2B3375A8754D9D0E6A5751BCE02D9132489C0BFDFC99F0CAC670E8DA7115CEACF87B7DE8FD4612E1B7638C2"
        )

        2    -> Bundle(
            packageName = "com.sisensing.sisensingcgm",
            appId = "com.sisensing.sisensingcgm",
            appKey = "4E8E1CAF43051F97EEC9C1475A8752D5C387D17A65B002D9132489C0BFDFC99F0CAC670E8CBB1150E6D581B7D08FC03404052C57AD58"
        )

        3    -> Bundle(
            packageName = "com.sisensing.eco",
            appId = "com.sisensing.eco",
            appKey = "068449FA5C1B1F97EEC9C1475A8752D5C387D17A65B002D9132489C0BFDFC99F0CAC670E9AB10D62FDE0B2B1E7"
        )

        else -> null
    }

    private var initialized = false
    private var available = false
    private var registeredKey = false
    private var dataHandleClass: Class<*>? = null
    private val methods = HashMap<String, java.lang.reflect.Method>()

    fun isAvailable(): Boolean {
        ensureInitialized()
        return available
    }

    fun buildAuthBytes(macAddress: String): ByteArray? {
        ensureInitialized()
        if (!available) return null
        if (!registerKey()) return null
        val reversed = reverseMac(macAddress) ?: return null
        val out = ByteArray(50)
        val len = invokeInt("V120ApplyAuthentication", 1, true, 0, reversed, out, out.size)
        return out.copyOfOrNull(len)
    }

    fun buildAskDataBytes(index: Int): ByteArray? {
        ensureInitialized()
        if (!available) return null
        val out = ByteArray(30)
        val len = invokeInt("V120RawData", 0, true, ByteArray(2), index.toLong(), 0, out, out.size)
        return out.copyOfOrNull(len)
    }

    fun buildActivationBytes(nowSeconds: Long): ByteArray? {
        ensureInitialized()
        if (!available) return null
        val out = ByteArray(50)
        val len = invokeInt("V120Activation", 0, true, ByteArray(2), nowSeconds, 1234, out, out.size)
        return out.copyOfOrNull(len)
    }

    fun buildTimeBytes(nowSeconds: Long): ByteArray? {
        ensureInitialized()
        if (!available) return null
        val out = ByteArray(50)
        val len = invokeInt("V120IsecUpdate", 0, true, ByteArray(2), nowSeconds, out, out.size)
        return out.copyOfOrNull(len)
    }

    fun buildResetBytes(): ByteArray? {
        ensureInitialized()
        if (!available) return null
        val out = ByteArray(1024)
        val len = invokeInt("V120Reset", 0, true, ByteArray(2), 0, out, out.size)
        return out.copyOfOrNull(len)
    }

    fun splitData(payload: ByteArray): SplitResult? {
        ensureInitialized()
        if (!available) return null

        val resultCodes = IntArray(2)
        val json = ByteArray(7168)
        val aux = ByteArray(2)
        var items = invokeInt("V120SpiltData", 0, payload, resultCodes, json, true, aux, payload.size)
        if (items <= 0) items = invokeInt("V120SpiltData", 0, payload, resultCodes, json, false, aux, payload.size)

        val rawCode = resultCodes[0]
        val rawJson = json.toString(Charsets.UTF_8).trimEnd('\u0000')
        val records = parseRecords(rawJson, max(items, 0))
        val control = mapControl(rawCode, resultCodes.getOrNull(1) ?: 0, rawJson)

        return SplitResult(rawCode = rawCode, control = control, records = records, rawJson = rawJson)
    }

    private fun mapControl(rawCode: Int, secondaryCode: Int, rawJson: String): Control {
        if (rawCode == 49227) return Control.RESET
        if (rawCode == 49165) {
            return mapSecondaryCode(secondaryCode, rawJson)
        }
        return if (rawJson.contains("49153")) Control.REAUTH else Control.NONE
    }

    private fun mapSecondaryCode(secondaryCode: Int, rawJson: String): Control = when {
        secondaryCode == 49153 || rawJson.contains("49153") -> Control.REAUTH
        secondaryCode == 49160 || rawJson.contains("49160") -> Control.SEND_TIME
        secondaryCode == 49154 || rawJson.contains("49154") -> Control.ACTIVATE
        secondaryCode == 49156 || rawJson.contains("49156") -> Control.ASK_VALUES
        rawJson.contains("49227")                              -> Control.RESET
        else                                                   -> Control.NONE
    }

    private fun parseRecords(rawJson: String, expectedItems: Int): List<Record> {
        if (rawJson.isBlank()) return emptyList()
        val objectRegex = Regex("\\{[^{}]+}")
        val keyValueRegex = Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"?([-0-9A-Za-z.]+)\"?")
        val records = ArrayList<Record>(expectedItems)

        objectRegex.findAll(rawJson).forEach { match ->
            val map = HashMap<String, String>()
            keyValueRegex.findAll(match.value).forEach { kv ->
                map[kv.groupValues[1]] = kv.groupValues[2]
            }

            val index = map["index"]?.toIntOrNull() ?: return@forEach
            val rawValue =
                map["current"]?.toDoubleOrNull()
                    ?: map["value"]?.toDoubleOrNull()
                    ?: map["glucose"]?.toDoubleOrNull()
                    ?: return@forEach
            val rawTimestamp =
                map["time"]?.toLongOrNull()
                    ?: map["timestamp"]?.toLongOrNull()
                    ?: map["eventTime"]?.toLongOrNull()
                    ?: return@forEach

            val trend = map["trend"]?.toIntOrNull() ?: 0
            val mmol = if (rawValue > 50.0) rawValue / 10.0 else rawValue
            val timestampMillis = if (rawTimestamp > 100_000_000_000L) rawTimestamp else rawTimestamp * 1000L
            records += Record(index = index, timestampMillis = timestampMillis, glucoseMmol = mmol, trend = trend)
        }
        return records
    }

    private fun ensureInitialized() {
        if (initialized) return
        initialized = true
        val b = bundle ?: return
        try {
            val packageContext = context.createPackageContext(
                b.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            val clazz = packageContext.classLoader.loadClass("com.no.sisense.enanddecryption.CGMDataHandle130")
            dataHandleClass = clazz
            register(clazz, "v120RegisterKey", ByteArray::class.java, Int::class.javaPrimitiveType, ByteArray::class.java)
            register(clazz, "V120ApplyAuthentication", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType, ByteArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType)
            register(clazz, "V120RawData", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, ByteArray::class.java, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType)
            register(clazz, "V120Activation", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, ByteArray::class.java, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType)
            register(clazz, "V120IsecUpdate", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, ByteArray::class.java, Long::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType)
            register(clazz, "V120Reset", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType)
            register(clazz, "V120SpiltData", Int::class.javaPrimitiveType, ByteArray::class.java, IntArray::class.java, ByteArray::class.java, Boolean::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType)
            available = true
            aapsLogger.debug(LTag.BGSOURCE, "Sibionics native bridge loaded from ${b.packageName}")
        } catch (t: Throwable) {
            aapsLogger.error(LTag.BGSOURCE, "Unable to load Sibionics native bridge for subtype=$subtype", t)
            available = false
        }
    }

    private fun register(clazz: Class<*>, name: String, vararg parameters: Class<*>?) {
        methods[name] = clazz.getMethod(name, *parameters)
    }

    private fun registerKey(): Boolean {
        if (registeredKey) return true
        ensureInitialized()
        if (!available) return false
        val b = bundle ?: return false
        val keyBytes = b.appKey.toByteArray(Charsets.UTF_8)
        val appIdBytes = b.appId.toByteArray(Charsets.UTF_8)
        val code = invokeInt("v120RegisterKey", keyBytes, keyBytes.size, appIdBytes)
        registeredKey = code >= 0
        return registeredKey
    }

    private fun invokeInt(name: String, vararg args: Any): Int {
        val method = methods[name] ?: return -1
        return try {
            (method.invoke(null, *args) as? Number)?.toInt() ?: -1
        } catch (t: Throwable) {
            aapsLogger.error(LTag.BGSOURCE, "Sibionics bridge call failed: $name", t)
            -1
        }
    }

    private fun reverseMac(macAddress: String): ByteArray? {
        val parts = macAddress.split(":")
        if (parts.size != 6) return null
        return try {
            ByteArray(6).also { out ->
                for (i in 0..5) out[i] = parts[5 - i].toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun ByteArray.copyOfOrNull(length: Int): ByteArray? =
        if (length <= 0 || length > size) null else copyOf(length)
}

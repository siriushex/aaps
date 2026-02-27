package app.aaps.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import androidx.annotation.VisibleForTesting
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.extensions.copyDouble
import app.aaps.core.utils.extensions.copyLong
import app.aaps.core.utils.extensions.copyString
import app.aaps.core.utils.receivers.BundleLogger
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.sync.nsclient.workers.NSClientAddUpdateWorker
import dagger.android.DaggerBroadcastReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject

open class DataReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var preferences: Preferences

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        processIntent(context, intent)
    }

    @VisibleForTesting
    fun processIntent(context: Context, intent: Intent) {
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.CORE, "onReceive ${intent.action} ${BundleLogger.log(bundle)}")
        when (intent.action) {
            Intents.ACTION_NEW_BG_ESTIMATE,
            Intents.JUGGLUCO_BG                       ->
                OneTimeWorkRequest.Builder(XdripSourcePlugin.XdripSourceWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(bundle, intent.action)).build()

            Intents.POCTECH_BG                        ->
                OneTimeWorkRequest.Builder(PoctechPlugin.PoctechWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("data", bundle)
                    }.build()).build()

            Intents.GLIMP_BG                          ->
                OneTimeWorkRequest.Builder(GlimpPlugin.GlimpWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyDouble("mySGV", bundle)
                        it.copyString("myTrend", bundle)
                        it.copyLong("myTimestamp", bundle)
                    }.build()).build()

            Intents.TOMATO_BG                         ->
                OneTimeWorkRequest.Builder(TomatoPlugin.TomatoWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyDouble("com.fanqies.tomatofn.Extras.BgEstimate", bundle)
                        it.copyLong("com.fanqies.tomatofn.Extras.Time", bundle)
                    }.build()).build()

            Intents.NS_EMULATOR                       ->
                if (isTreatmentsCollection(bundle)) buildTreatmentsWorkRequest(bundle, intent.action, requireSecret = false)
                else {
                    OneTimeWorkRequest.Builder(MM640gPlugin.MM640gWorker::class.java)
                        .setInputData(Data.Builder().also {
                            it.copyString("collection", bundle)
                            it.copyString("data", bundle)
                        }.build()).build()
                }

            Intents.LOCAL_TREATMENTS                  ->
                buildTreatmentsWorkRequest(bundle, intent.action, requireSecret = true)

            Intents.OTTAI_APP, Intents.OTTAI_APP_CN,
            Intents.SYAI_APP                          ->
                OneTimeWorkRequest.Builder(SyaiPlugin.SyaiWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()).build()

            Intents.SI_APP                            ->
                OneTimeWorkRequest.Builder(PatchedSiAppPlugin.PatchedSiAppWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()).build()

            Intents.SINO_APP                          ->
                OneTimeWorkRequest.Builder(PatchedSinoAppPlugin.PatchedSinoAppWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()).build()

            Telephony.Sms.Intents.SMS_RECEIVED_ACTION ->
                OneTimeWorkRequest.Builder(SmsCommunicatorPlugin.SmsCommunicatorWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(bundle, intent.action)).build()

            Intents.DEXCOM_BG, Intents.DEXCOM_G7_BG   ->
                OneTimeWorkRequest.Builder(DexcomPlugin.DexcomWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(bundle, intent.action)).build()

            else                                      -> null
        }?.let { request -> dataWorkerStorage.enqueue(request) }

        // Verify KeepAlive is running
        // Sometimes the schedule fail
        KeepAliveWorker.scheduleIfNotRunning(context, aapsLogger, fabricPrivacy)
    }

    private fun buildTreatmentsWorkRequest(bundle: Bundle, action: String?, requireSecret: Boolean): OneTimeWorkRequest? {
        if (requireSecret && !isAuthorizedForTreatments(bundle)) return null
        val treatments =
            parseTreatmentsPayload(bundle) ?: buildTreatmentsFromFlatExtras(bundle)
        if (treatments == null || treatments.length() == 0) {
            aapsLogger.error(LTag.CORE, "Ignoring treatment broadcast: missing or invalid payload")
            return null
        }
        return OneTimeWorkRequest.Builder(NSClientAddUpdateWorker::class.java)
            .setInputData(dataWorkerStorage.storeInputData(treatments, action)).build()
    }

    private fun isAuthorizedForTreatments(bundle: Bundle): Boolean {
        val expectedSecret = preferences.get(StringKey.NsClientApiSecret).trim()
        if (expectedSecret.isEmpty()) return true
        val providedSecret = getString(bundle, Intents.EXTRA_API_SECRET, Intents.EXTRA_SECRET, Intents.EXTRA_TOKEN)?.trim()
        if (!providedSecret.isNullOrEmpty() && providedSecret == expectedSecret) return true
        aapsLogger.error(LTag.CORE, "Rejected treatment broadcast: invalid secret")
        return false
    }

    private fun parseTreatmentsPayload(bundle: Bundle): JSONArray? {
        val payload = getString(bundle, Intents.EXTRA_TREATMENTS, Intents.EXTRA_DATA) ?: return null
        return parseJsonArray(payload)
    }

    private fun parseJsonArray(payload: String): JSONArray? = try {
        when {
            payload.trimStart().startsWith("[") -> JSONArray(payload)
            payload.trimStart().startsWith("{") -> JSONArray().put(JSONObject(payload))
            else                                -> null
        }
    } catch (e: Exception) {
        aapsLogger.error(LTag.CORE, "Invalid treatment payload JSON: ${e.message}")
        null
    }

    private fun buildTreatmentsFromFlatExtras(bundle: Bundle): JSONArray? {
        val treatments = JSONArray()
        val timestamp = normalizeTimestamp(getLong(bundle, Intents.EXTRA_MILLS, Intents.EXTRA_EVENT_TIMESTAMP, Intents.EXTRA_TIMESTAMP, "date", "ts"))
        val carbs = getDouble(bundle, Intents.EXTRA_CARBS)
        val carbsDuration = getLong(bundle, Intents.EXTRA_CARBS_DURATION, Intents.EXTRA_DURATION) ?: 0L

        if (carbs != null && carbs > 0.0) {
            treatments.put(
                JSONObject()
                    .put("eventType", TE.Type.CARBS_CORRECTION.text)
                    .put("carbs", carbs)
                    .put("duration", carbsDuration)
                    .put("mills", timestamp)
                    .put("_id", getString(bundle, "carbsId", Intents.EXTRA_ID, "identifier") ?: buildStableId("carbs", timestamp, carbs, carbsDuration))
            )
        }

        val singleTarget = getDouble(bundle, Intents.EXTRA_TARGET)
        val targetBottom = getDouble(bundle, Intents.EXTRA_TARGET_BOTTOM) ?: singleTarget
        val targetTop = getDouble(bundle, Intents.EXTRA_TARGET_TOP) ?: singleTarget
        val duration = getLong(bundle, Intents.EXTRA_DURATION, "ttDuration")
        if (targetBottom != null && targetTop != null && duration != null && duration > 0L) {
            val units = normalizeTargetUnits(getString(bundle, Intents.EXTRA_UNITS))
            val reason = getString(bundle, Intents.EXTRA_REASON)?.trim()?.ifEmpty { null } ?: "Automation"
            treatments.put(
                JSONObject()
                    .put("eventType", TE.Type.TEMPORARY_TARGET.text)
                    .put("duration", duration)
                    .put("targetBottom", targetBottom)
                    .put("targetTop", targetTop)
                    .put("units", units)
                    .put("reason", reason)
                    .put("mills", timestamp)
                    .put("_id", getString(bundle, "ttId", Intents.EXTRA_ID, "identifier") ?: buildStableId("tt", timestamp, targetBottom, targetTop, duration, units))
            )
        }
        return if (treatments.length() == 0) null else treatments
    }

    private fun normalizeTimestamp(value: Long?): Long {
        val raw = value ?: return System.currentTimeMillis()
        return if (raw in 1..9_999_999_999L) raw * 1000 else raw
    }

    private fun isTreatmentsCollection(bundle: Bundle): Boolean =
        getString(bundle, Intents.EXTRA_COLLECTION)
            ?.trim()
            ?.lowercase(Locale.US) == COLLECTION_TREATMENTS

    private fun getString(bundle: Bundle, vararg keys: String): String? {
        for (key in keys) {
            val value = bundle.get(key) ?: continue
            return when (value) {
                is String       -> value
                is CharSequence -> value.toString()
                else            -> value.toString()
            }
        }
        return null
    }

    private fun getDouble(bundle: Bundle, vararg keys: String): Double? {
        for (key in keys) {
            val value = bundle.get(key) ?: continue
            when (value) {
                is Number -> return value.toDouble()
                is String -> value.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun getLong(bundle: Bundle, vararg keys: String): Long? {
        for (key in keys) {
            val value = bundle.get(key) ?: continue
            when (value) {
                is Number -> return value.toLong()
                is String -> value.toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun normalizeTargetUnits(raw: String?): String {
        val normalized = raw?.trim()?.lowercase(Locale.US).orEmpty()
        return when (normalized) {
            "mg/dl", "mgdl", "mg dl" -> "mg/dl"
            "mmol", "mmol/l", "mmol\\l", "mmol l" -> "mmol"
            else -> "mmol"
        }
    }

    private fun buildStableId(prefix: String, vararg parts: Any): String {
        val payload = buildString {
            append(prefix)
            parts.forEach { append('|').append(it.toString()) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        val hash = digest.take(12).joinToString("") { "%02x".format(it) }
        return "local-$prefix-$hash"
    }

    companion object {

        private const val COLLECTION_TREATMENTS = "treatments"
    }
}

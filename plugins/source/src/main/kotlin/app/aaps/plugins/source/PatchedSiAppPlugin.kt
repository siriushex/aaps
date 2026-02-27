package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.plugins.source.activities.SibionicsDirectSetupActivity
import app.aaps.plugins.source.keys.SibionicsDirectBooleanKey
import app.aaps.plugins.source.keys.SibionicsDirectIntKey
import app.aaps.plugins.source.keys.SibionicsDirectLongKey
import app.aaps.plugins.source.keys.SibionicsDirectStringKey
import app.aaps.plugins.source.sibionics.SibionicsDirectSession
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatchedSiAppPlugin @Inject constructor(
    rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val context: Context,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val preferences: Preferences
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_generic_cgm)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.patched_si_app)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_patched_si_app),
    ownPreferences = listOf(
        SibionicsDirectBooleanKey::class.java,
        SibionicsDirectIntKey::class.java,
        SibionicsDirectLongKey::class.java,
        SibionicsDirectStringKey::class.java
    ),
    aapsLogger, rh, preferences
), BgSource {

    private var directSession: SibionicsDirectSession? = null

    override fun onStart() {
        super.onStart()
        if (directSession == null) {
            directSession = SibionicsDirectSession(
                context = context,
                preferences = preferences,
                aapsLogger = aapsLogger,
                persistenceLayer = persistenceLayer,
                dateUtil = dateUtil
            )
        }
        directSession?.start()
    }

    override fun onStop() {
        super.onStop()
        directSession?.stop()
        directSession = null
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        super.addPreferenceScreen(preferenceManager, parent, context, requiredKey)
        if (requiredKey != null) return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "sibionics_direct_settings"
            title = rh.gs(R.string.sibionics_direct_category_title)
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context,
                    intentKey = IntentKey.SibionicsDirectSetup,
                    intent = Intent(context, SibionicsDirectSetupActivity::class.java),
                    title = R.string.sibionics_direct_setup_title,
                    summary = R.string.sibionics_direct_setup_summary
                )
            )
        }
    }

    class PatchedSiAppWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var patchedSIAppPlugin: PatchedSiAppPlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()
            if (!patchedSIAppPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val collection = inputData.getString("collection") ?: return Result.failure(workDataOf("Error" to "missing collection"))
            if (collection == "entries") {
                val data = inputData.getString("data")
                aapsLogger.debug(LTag.BGSOURCE, "Received SI App Data $data")
                if (!data.isNullOrEmpty()) {
                    try {
                        val glucoseValues = mutableListOf<GV>()
                        val jsonArray = JSONArray(data)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            when (val type = jsonObject.getString("type")) {
                                "sgv" ->
                                    glucoseValues += GV(
                                        timestamp = jsonObject.getLong("date"),
                                        value = jsonObject.getDouble("sgv"),
                                        raw = null,
                                        noise = null,
                                        trendArrow = TrendArrow.fromString(jsonObject.getString("direction")),
                                        sourceSensor = SourceSensor.SIBIONIC
                                    )

                                else  -> aapsLogger.debug(LTag.BGSOURCE, "Unknown entries type: $type")
                            }
                        }
                        persistenceLayer.insertCgmSourceData(Sources.SiBionic, glucoseValues, emptyList(), null)
                            .doOnError { ret = Result.failure(workDataOf("Error" to it.toString())) }
                            .blockingGet()
                    } catch (e: JSONException) {
                        aapsLogger.error("Exception: ", e)
                        ret = Result.failure(workDataOf("Error" to e.toString()))
                    }
                }
            } else {
                ret = Result.failure(workDataOf("Error" to "missing input data"))
            }
            return ret
        }
    }
}

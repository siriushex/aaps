package app.aaps.wear.interaction

import android.os.Bundle
import android.view.ViewGroup
import androidx.preference.PreferenceFragmentCompat
import androidx.wear.tiles.TileService
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.preference.WearPreferenceActivity
import app.aaps.wear.tile.ActionsTileService
import app.aaps.wear.tile.TempTargetTileService
import dagger.android.AndroidInjection
import javax.inject.Inject

class TileConfigurationActivity : WearPreferenceActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    private var configFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        title = "Tile"
        configFileName = intent.action

        val view = window.decorView as ViewGroup
        view.requestFocus()
    }

    override fun createPreferenceFragment(): PreferenceFragmentCompat {
        val resXmlId = resources.getIdentifier(configFileName, "xml", applicationContext.packageName)
        aapsLogger.debug(LTag.WEAR, "TileConfigurationActivity::createPreferenceFragment --->> getIntent().getAction() $configFileName")
        aapsLogger.debug(LTag.WEAR, "TileConfigurationActivity::createPreferenceFragment --->> resXmlId $resXmlId")

        return TileConfigurationFragment.newInstance(resXmlId)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note that TileService updates are hard limited to once every 20 seconds.
        when (configFileName) {
            "tile_configuration_activity" -> {
                aapsLogger.info(LTag.WEAR, "onDestroy a: requestUpdate")
                TileService.getUpdater(this).requestUpdate(ActionsTileService::class.java)
            }

            "tile_configuration_tempt"    -> {
                aapsLogger.info(LTag.WEAR, "onDestroy tt: requestUpdate")
                TileService.getUpdater(this).requestUpdate(TempTargetTileService::class.java)
            }

            else                           -> {
                aapsLogger.info(LTag.WEAR, "onDestroy : NO tile service available for $configFileName")
            }
        }
    }

    /**
     * Fragment for loading tile configuration preferences
     */
    class TileConfigurationFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val resXmlId = arguments?.getInt(ARG_XML_RES_ID) ?: 0
            if (resXmlId != 0) {
                setPreferencesFromResource(resXmlId, rootKey)
            }
        }

        companion object {
            private const val ARG_XML_RES_ID = "xml_res_id"

            fun newInstance(xmlResId: Int): TileConfigurationFragment {
                return TileConfigurationFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_XML_RES_ID, xmlResId)
                    }
                }
            }
        }
    }
}

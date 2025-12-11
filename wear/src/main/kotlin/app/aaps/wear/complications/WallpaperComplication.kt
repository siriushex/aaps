@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData
import android.util.DisplayMetrics
import android.view.WindowManager
import app.aaps.core.interfaces.logging.LTag
import java.io.IOException

/**
 * Wallpaper Complication (Abstract Base)
 *
 * Provides wallpaper image complications scaled to watch screen size
 * Subclasses specify the wallpaper asset file to display
 * Type: LARGE_IMAGE
 *
 */
abstract class WallpaperComplication : ModernBaseComplicationProviderService() {

    abstract val wallpaperAssetsFileName: String

    override fun buildComplicationData(
        dataType: Int,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        return when (dataType) {
            ComplicationData.TYPE_LARGE_IMAGE -> {
                val metrics = DisplayMetrics()
                val windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager.defaultDisplay.getMetrics(metrics)
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val builder = ComplicationData.Builder(ComplicationData.TYPE_LARGE_IMAGE)
                val assetManager = assets
                try {
                    assetManager.open(wallpaperAssetsFileName).use { iStr ->
                        val bitmap = BitmapFactory.decodeStream(iStr)
                        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                        builder.setLargeImage(Icon.createWithBitmap(scaled))
                    }
                } catch (e: IOException) {
                    aapsLogger.error(LTag.WEAR, "Cannot read wallpaper asset: " + e.message, e)
                }
                builder.build()
            }

            else                              -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
                null
            }
        }
    }

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.NONE
}
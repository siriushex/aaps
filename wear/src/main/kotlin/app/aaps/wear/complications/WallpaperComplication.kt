package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
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
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        return when (type) {
            ComplicationType.PHOTO_IMAGE      -> {
                val metrics = DisplayMetrics()
                val windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager.defaultDisplay.getMetrics(metrics)
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val assetManager = assets
                var photoIcon: Icon? = null
                try {
                    assetManager.open(wallpaperAssetsFileName).use { iStr ->
                        val bitmap = BitmapFactory.decodeStream(iStr)
                        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                        photoIcon = Icon.createWithBitmap(scaled)
                    }
                } catch (e: IOException) {
                    aapsLogger.error(LTag.WEAR, "Cannot read wallpaper asset: " + e.message, e)
                }
                photoIcon?.let {
                    PhotoImageComplicationData.Builder(
                        photoImage = it,
                        contentDescription = PlainComplicationText.Builder(text = "Wallpaper").build()
                    ).build()
                }
            }

            else                              -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.NONE
}
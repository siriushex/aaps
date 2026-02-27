package app.aaps.utils

import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import leakcanary.AppWatcher
import leakcanary.LeakCanary

/**
 * Configures LeakCanary with optional Firebase Crashlytics reporting.
 *
 * @param isEnabled Whether to enable heap dumping and the launcher icon
 * @param fabricPrivacy FabricPrivacy instance for uploading memory leaks to Firebase Crashlytics.
 *                      When provided, memory leaks will be uploaded via FabricPrivacy.logException.
 */
fun configureLeakCanary(isEnabled: Boolean = false, fabricPrivacy: FabricPrivacy? = null) {
    if (!isEnabled) {
        AppWatcher.config = AppWatcher.config.copy(enabled = false)
        // Fully disable heap analysis listeners to avoid background analyzer load.
        LeakCanary.config = LeakCanary.config.copy(
            dumpHeap = false,
            eventListeners = emptyList()
        )
        // If watcher auto-install is disabled in debug resources, this call throws.
        runCatching { LeakCanary.showLeakDisplayActivityLauncherIcon(false) }
        return
    }

    AppWatcher.config = AppWatcher.config.copy(enabled = true)
    val eventListeners = if (fabricPrivacy != null) {
        LeakCanary.config.eventListeners + LeakUploadService(fabricPrivacy)
    } else {
        LeakCanary.config.eventListeners
    }

    LeakCanary.config = LeakCanary.config.copy(
        dumpHeap = isEnabled,
        eventListeners = eventListeners
    )
    runCatching { LeakCanary.showLeakDisplayActivityLauncherIcon(isEnabled) }
}

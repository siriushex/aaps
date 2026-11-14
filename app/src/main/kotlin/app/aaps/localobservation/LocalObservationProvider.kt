package app.aaps.localobservation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.SystemClock
import app.aaps.MainApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Same-phone observation only. No command or arbitrary query surface. */
class LocalObservationProvider : ContentProvider() {
    private val reading = AtomicBoolean(false)
    private val lastRead = AtomicLong(-5_000L)

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = checkNotNull(context).applicationContext as MainApp
        val callerUid = Binder.getCallingUid()
        val caller = callingPackage
        val permission = "${app.packageName}.permission.READ_LOCAL_OBSERVATION"
        // ContentProvider.call does not enforce the manifest read/write permissions.
        if (app.packageName != "info.nightscout.androidaps" ||
            caller != "io.aaps.predictivecopilot" ||
            app.checkCallingPermission(permission) != PackageManager.PERMISSION_GRANTED ||
            app.packageManager.checkSignatures(callerUid, android.os.Process.myUid()) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("local_observation_denied")
        }
        require(method == "snapshot_v1" && arg == null && extras == null)
        if (!reading.compareAndSet(false, true)) return Bundle()
        try {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastRead.get() < 5_000L) return Bundle()
            lastRead.set(nowElapsed)
            val identity = Binder.clearCallingIdentity()
            try {
                val snapshot = runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(2_500L) { app.readLocalObservation(System.currentTimeMillis()) }
                } ?: return Bundle()
                return Bundle().apply { putByteArray("snapshot", snapshot) }
            } catch (_: Exception) {
                return Bundle()
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        } finally {
            reading.set(false)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor = unsupported()
    override fun insert(uri: Uri, values: ContentValues?): Uri = unsupported()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun getType(uri: Uri): String? = null

    private fun unsupported(): Nothing = throw UnsupportedOperationException("local_observation_snapshot_only")
}

package io.nekohasekai.sagernet.update

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.nekohasekai.sagernet.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File

/** Hands a verified APK to Shizuku silent install or a PackageInstaller session; results arrive at [UpdateReceiver]. */
object UpdateInstaller {

    const val ACTION_STATUS = "io.nekohasekai.sagernet.update.INSTALL_STATUS"

    fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 26) setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Only honoured while this app is the installer of record; the system asks otherwise.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            try {
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 256 * 1024) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
                // The installer fills in the status extras, so the intent must stay mutable.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
            } catch (e: Throwable) {
                session.abandon()
                throw e
            }
        }
    }

    /**
     * Attempts a silent update via Shizuku UserService. Returns `null` on success, or an error description on failure
     * so callers can gracefully fall back to [install].
     */
    suspend fun installViaShizuku(context: Context, file: File): String? = withContext(Dispatchers.IO) {
        val connected = CompletableDeferred<IUpdateUserService?>()
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, UpdateUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("update_installer")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("update_installer")

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = binder?.let(IUpdateUserService.Stub::asInterface)
                connected.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!connected.isCompleted) {
                    connected.complete(null)
                }
            }
        }

        try {
            Shizuku.bindUserService(args, conn)
            val service = withTimeoutOrNull(10_000) { connected.await() }
                ?: return@withContext "Shizuku service bind timeout"
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { fd ->
                val errorOut = arrayOf("")
                val exitCode = service.installApk(fd, file.length(), errorOut)
                if (exitCode == 0) {
                    null
                } else {
                    errorOut[0].ifEmpty { "pm install failed (exit=$exitCode)" }
                }
            }
        } catch (t: Throwable) {
            t.message ?: t.javaClass.simpleName
        } finally {
            runCatching { Shizuku.unbindUserService(args, conn, true) }
        }
    }
}

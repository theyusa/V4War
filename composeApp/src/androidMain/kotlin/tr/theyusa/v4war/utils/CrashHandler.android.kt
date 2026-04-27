package tr.theyusa.v4war.utils

import android.content.Intent
import android.util.Log
import com.jakewharton.processphoenix.ProcessPhoenix
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.repository.resolveAndroidRepository

actual object CrashHandler : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // note: libc / go panic is in android log

        runCatching {
            Log.e(thread.toString(), throwable.stackTraceToString())
        }

        runCatching {
            Logs.e(thread.toString())
            Logs.e(throwable.stackTraceToString())
        }

        ProcessPhoenix.triggerRebirth(
            resolveAndroidRepository().context,
            Intent(resolveAndroidRepository().context, Class.forName("tr.theyusa.v4war.ui.BlankActivity"))
                .putExtra("log_title", "v4war_crash"),
        )
    }
}

package tr.theyusa.v4war.ktx

import android.content.Intent
import com.jakewharton.processphoenix.ProcessPhoenix
import tr.theyusa.v4war.repository.resolveAndroidRepository

actual fun restartApplication() {
    ProcessPhoenix.triggerRebirth(
        resolveAndroidRepository().context,
        Intent(resolveAndroidRepository().context, Class.forName("tr.theyusa.v4war.ui.MainActivity")),
    )
}

actual fun exitApplication() {
    android.os.Process.killProcess(android.os.Process.myPid())
}

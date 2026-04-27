package tr.theyusa.v4war.ktx

import android.widget.Toast
import tr.theyusa.v4war.repository.resolveAndroidRepository

actual fun showToast(message: String, long: Boolean) {
    val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    runOnMainDispatcher {
        Toast.makeText(resolveAndroidRepository().context, message, duration).show()
    }
}

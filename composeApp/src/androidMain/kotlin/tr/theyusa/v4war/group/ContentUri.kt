package tr.theyusa.v4war.group

import androidx.core.net.toUri
import tr.theyusa.v4war.repository.resolveAndroidRepository

actual fun readContentUri(uri: String): String? {
    return resolveAndroidRepository().context.contentResolver.openInputStream(uri.toUri())
        ?.bufferedReader()
        ?.readText()
}

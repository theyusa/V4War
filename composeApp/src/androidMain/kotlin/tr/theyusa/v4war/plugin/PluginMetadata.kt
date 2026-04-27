package tr.theyusa.v4war.plugin

import android.content.pm.ComponentInfo
import tr.theyusa.v4war.utils.PackageCache

fun ComponentInfo.loadString(key: String) =
    when (@Suppress("DEPRECATION") val value = metaData.get(key)) {
        is String -> value
        is Int -> PackageCache.packageManager
            .getResourcesForApplication(applicationInfo)
            .getString(value)

        null -> null
        else -> error("meta-data $key has invalid type ${value.javaClass}")
    }

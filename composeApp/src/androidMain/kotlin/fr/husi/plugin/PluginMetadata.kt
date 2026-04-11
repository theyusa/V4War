package fr.husi.plugin

import android.content.pm.ComponentInfo
import fr.husi.utils.PackageCache

fun ComponentInfo.loadString(key: String) =
    when (@Suppress("DEPRECATION") val value = metaData.get(key)) {
        is String -> value
        is Int -> PackageCache.packageManager
            .getResourcesForApplication(applicationInfo)
            .getString(value)

        null -> null
        else -> error("meta-data $key has invalid type ${value.javaClass}")
    }

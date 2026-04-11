package fr.husi.utils

import fr.v4war.BuildConfig
import fr.husi.database.DataStore
import fr.husi.ktx.currentUtcReportTimestamp
import kotlinx.coroutines.runBlocking

object CrashReport {

    fun formatThrowable(throwable: Throwable): String {
        var format = throwable.javaClass.name
        val message = throwable.message
        if (!message.isNullOrBlank()) {
            format += ": $message"
        }
        format += "\n"

        format += throwable.stackTrace.joinToString("\n") {
            "    at ${it.className}.${it.methodName}(${it.fileName}:${if (it.isNativeMethod) "native" else it.lineNumber})"
        }

        val cause = throwable.cause
        if (cause != null) {
            format += "\n\nCaused by: " + formatThrowable(cause)
        }

        return format
    }

    fun buildReportHeader(): String {
        var report = ""
        report += "V4War ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR.uppercase()}\n"
        report += "Date: ${getCurrentMilliSecondUTCTimeStamp()}\n\n"
        report += buildPlatformSystemInfoReport()

        try {
            report += "Settings: \n"
            runBlocking {
                report += DataStore.configurationStore.exportToString()
            }
        } catch (e: Exception) {
            report += "Export settings failed: " + formatThrowable(e)
        }

        report += "\n\n"

        return report
    }

    private fun getCurrentMilliSecondUTCTimeStamp(): String {
        return currentUtcReportTimestamp()
    }

}

internal expect fun buildPlatformSystemInfoReport(): String

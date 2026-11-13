package io.nekohasekai.sagernet.update

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Shizuku UserService running in shell UID 2000 for silent APK updates (`pm install -r -S`).
 */
class UpdateUserService : IUpdateUserService.Stub() {

    override fun installApk(apkFd: ParcelFileDescriptor?, size: Long, errorOut: Array<String>?): Int {
        if (apkFd == null || size <= 0L) {
            setError(errorOut, "Invalid APK descriptor or size")
            return -1
        }
        return try {
            val process = ProcessBuilder("pm", "install", "-r", "-S", size.toString())
                .redirectErrorStream(true)
                .start()

            ParcelFileDescriptor.AutoCloseInputStream(apkFd).use { input ->
                process.outputStream.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            val outputText = BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readText().trim()
            }
            val code = process.waitFor()
            if (code != 0) {
                setError(errorOut, outputText.ifEmpty { "pm install exited with code $code" })
            }
            code
        } catch (t: Throwable) {
            Log.e(TAG, "Shizuku silent install failed", t)
            setError(errorOut, t.message ?: t.javaClass.simpleName)
            -1
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    private fun setError(errorOut: Array<String>?, message: String) {
        if (errorOut != null && errorOut.isNotEmpty()) {
            errorOut[0] = message
        }
    }

    private companion object {
        private const val TAG = "UpdateUserService"
    }
}

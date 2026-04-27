package tr.theyusa.v4war.utils

internal actual fun dumpPlatformLogcat(): String {
    return Runtime.getRuntime()
        .exec(arrayOf("logcat", "-d"))
        .inputStream.bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}

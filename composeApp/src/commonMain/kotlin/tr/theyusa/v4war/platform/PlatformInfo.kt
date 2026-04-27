package tr.theyusa.v4war.platform

expect object PlatformInfo {
    val isAndroid: Boolean
    val isLinux: Boolean
    val isMacOs: Boolean
    val isWindows: Boolean
}

package fr.husi.platform

expect object PlatformInfo {
    val isAndroid: Boolean
    val isLinux: Boolean
    val isMacOs: Boolean
    val isWindows: Boolean
}

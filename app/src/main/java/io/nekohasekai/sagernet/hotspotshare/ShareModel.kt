package io.nekohasekai.sagernet.hotspotshare

/** How the VPN is shared over the hotspot. [LAN_PROXY] is the existing simple mode (proxy entered on the client). */
enum class ShareMethod(val code: String) {
    SHIZUKU("shizuku"),
    ROOT("root"),
    LAN_PROXY("lan_proxy"),
    ;

    companion object {
        fun fromCode(code: String): ShareMethod? = entries.firstOrNull { it.code == code }
    }
}

/** The user's choice: [AUTO] lets [HotspotShareSelector] pick, anything else is a manual override. */
enum class SharePreference(val code: String) {
    AUTO("auto"),
    SHIZUKU("shizuku"),
    ROOT("root"),
    LAN_PROXY("lan_proxy"),
    ;

    /** The method a manual choice forces, null for [AUTO]. */
    val forcedMethod: ShareMethod?
        get() = when (this) {
            AUTO -> null
            SHIZUKU -> ShareMethod.SHIZUKU
            ROOT -> ShareMethod.ROOT
            LAN_PROXY -> ShareMethod.LAN_PROXY
        }

    companion object {
        val CODES: List<String> = entries.map { it.code }
        fun fromCode(code: String): SharePreference = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/** What to do in ROOT mode if the running VPN drops unexpectedly (leak protection). */
enum class FailClosedAction(val code: String) {
    BLOCK_TRAFFIC("block"),
    STOP_HOTSPOT("stop"),
    ;

    companion object {
        val CODES: List<String> = entries.map { it.code }
        fun fromCode(code: String): FailClosedAction = entries.firstOrNull { it.code == code } ?: BLOCK_TRAFFIC
    }
}

/** Why sharing is unavailable, failed or stopped. [code] is persisted, so never rename one. */
enum class ShareFailure(val code: String) {
    UNSUPPORTED_ANDROID("unsupported_android"),
    SHIZUKU_NOT_RUNNING("shizuku_not_running"),
    SHIZUKU_PERMISSION_DENIED("shizuku_permission_denied"),
    ROOT_UNAVAILABLE("root_unavailable"),
    VPN_NOT_RUNNING("vpn_not_running"),
    VPN_LOST("vpn_lost"),
    HOTSPOT_NOT_ACTIVE("hotspot_not_active"),
    TETHER_LOST("tether_lost"),
    BACKEND_DIED("backend_died"),
    BACKEND_NOT_IMPLEMENTED("backend_not_implemented"),
    BACKEND_FAILED("backend_failed"),
    ;

    companion object {
        fun fromCode(code: String): ShareFailure? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Off: not sharing (never started, or the user turned it off). Stopped: sharing dropped by itself after it was
 * running, so the user is back on normal data and must be warned.
 */
sealed class ShareState {
    data object Off : ShareState()
    data object Starting : ShareState()
    data class Active(val method: ShareMethod) : ShareState()
    data class Error(val reason: ShareFailure, val detail: String? = null) : ShareState()
    data class Stopped(val cause: ShareFailure) : ShareState()
}

/** What the selector needs to know about the device; filled by the platform layer, faked in tests. */
data class ShareEnvironment(
    val sdkInt: Int,
    val shizukuRunning: Boolean,
    val shizukuGranted: Boolean,
    val rootAvailable: Boolean,
)

/** A [ShareState] as the three strings persisted for the UI process. */
data class EncodedShareState(val code: String, val method: String, val reason: String)

object ShareStateCodec {
    private const val OFF = "off"
    private const val STARTING = "starting"
    private const val ACTIVE = "active"
    private const val ERROR = "error"
    private const val STOPPED = "stopped"

    fun encode(state: ShareState): EncodedShareState = when (state) {
        ShareState.Off -> EncodedShareState(OFF, "", "")
        ShareState.Starting -> EncodedShareState(STARTING, "", "")
        is ShareState.Active -> EncodedShareState(ACTIVE, state.method.code, "")
        is ShareState.Error -> EncodedShareState(ERROR, "", state.reason.code)
        is ShareState.Stopped -> EncodedShareState(STOPPED, "", state.cause.code)
    }

    /** An unknown or damaged record decodes to [ShareState.Off] rather than inventing a state. */
    fun decode(encoded: EncodedShareState): ShareState = when (encoded.code) {
        STARTING -> ShareState.Starting
        ACTIVE -> ShareMethod.fromCode(encoded.method)?.let { ShareState.Active(it) } ?: ShareState.Off
        ERROR -> ShareFailure.fromCode(encoded.reason)?.let { ShareState.Error(it) } ?: ShareState.Off
        STOPPED -> ShareFailure.fromCode(encoded.reason)?.let { ShareState.Stopped(it) } ?: ShareState.Off
        else -> ShareState.Off
    }
}

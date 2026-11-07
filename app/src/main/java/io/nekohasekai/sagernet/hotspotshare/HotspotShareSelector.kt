package io.nekohasekai.sagernet.hotspotshare

sealed interface ShareSelection {
    /** [automatic] is true when the selector picked [method], false for a manual choice. */
    data class Chosen(val method: ShareMethod, val automatic: Boolean) : ShareSelection

    /** A manual choice that cannot run now; it is never silently replaced by another method. */
    data class Unavailable(val requested: ShareMethod, val reason: ShareFailure) : ShareSelection
}

/** Pure backend selection: Shizuku (granted, running, Android 13+), then root, then the LAN proxy. */
object HotspotShareSelector {

    /**
     * TetheringManager.setPreferTestNetworks exists from android-13.0.0_r1 and not in the android11/12/12L
     * branches, so the Shizuku backend needs API 33 (Build.VERSION_CODES.TIRAMISU).
     */
    const val MIN_SDK_SHIZUKU = 33

    /** Null when [method] can run on [env], otherwise the reason it cannot. */
    fun unavailableReason(method: ShareMethod, env: ShareEnvironment): ShareFailure? = when (method) {
        ShareMethod.SHIZUKU -> when {
            env.sdkInt < MIN_SDK_SHIZUKU -> ShareFailure.UNSUPPORTED_ANDROID
            !env.shizukuRunning -> ShareFailure.SHIZUKU_NOT_RUNNING
            !env.shizukuGranted -> ShareFailure.SHIZUKU_PERMISSION_DENIED
            else -> null
        }
        ShareMethod.ROOT -> if (env.rootAvailable) null else ShareFailure.ROOT_UNAVAILABLE
        ShareMethod.LAN_PROXY -> null
    }

    fun resolve(preference: SharePreference, env: ShareEnvironment): ShareSelection {
        val forced = preference.forcedMethod
        if (forced != null) {
            val reason = unavailableReason(forced, env) ?: return ShareSelection.Chosen(forced, automatic = false)
            return ShareSelection.Unavailable(forced, reason)
        }
        val method = listOf(ShareMethod.SHIZUKU, ShareMethod.ROOT)
            .firstOrNull { unavailableReason(it, env) == null }
            ?: ShareMethod.LAN_PROXY
        return ShareSelection.Chosen(method, automatic = true)
    }
}

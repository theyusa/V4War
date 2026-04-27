package tr.theyusa.v4war.aidl

expect class ServiceStatus(
    state: Int = 0,
    profileName: String? = null,
    started: Boolean = false,
    connected: Boolean = false,
) {
    val state: Int
    val profileName: String?
    val started: Boolean
    val connected: Boolean
}

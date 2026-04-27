package tr.theyusa.v4war.bg

expect object DefaultNetworkListener {
    suspend fun start(key: Any, listener: suspend () -> Unit)
    suspend fun stop(key: Any)
}

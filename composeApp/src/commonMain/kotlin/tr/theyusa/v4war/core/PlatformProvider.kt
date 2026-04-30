package tr.theyusa.v4war.core

expect class PlatformProvider(context: Any) {
    fun getPlatformName(): String
    suspend fun isNetworkAvailable(): Boolean
    suspend fun getDeviceId(): String
    suspend fun storeSecure(key: String, value: String): Result<Unit>
    suspend fun retrieveSecure(key: String): Result<String?>
    suspend fun removeSecure(key: String): Result<Unit>
}
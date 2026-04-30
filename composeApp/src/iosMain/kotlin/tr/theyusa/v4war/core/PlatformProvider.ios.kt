package tr.theyusa.v4war.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSUUID

actual class PlatformProvider actual constructor() : PlatformProvider {

    actual override fun getPlatformName(): String = "iOS"

    actual override suspend fun isNetworkAvailable(): Boolean =
        withContext(Dispatchers.IO) {
            true
        }

    actual override suspend fun getDeviceId(): String =
        withContext(Dispatchers.IO) {
            NSUUID().UUIDString()
        }

    actual override suspend fun storeSecure(key: String, value: String): Result<Unit> =
        Result.failure(NotImplementedError("iOS secure storage not implemented yet"))

    actual override suspend fun retrieveSecure(key: String): Result<String?> =
        Result.failure(NotImplementedError("iOS secure storage not implemented yet"))

    actual override suspend fun removeSecure(key: String): Result<Unit> =
        Result.failure(NotImplementedError("iOS secure storage not implemented yet"))
}
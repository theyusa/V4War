package tr.theyusa.v4war.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class AndroidPlatformProvider(
    private val context: Context
) : PlatformProvider {

    private val masterKey: MasterKey by lazy {
        runCatching {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }.getOrElse { e ->
            throw IllegalStateException("Security initialization failed", e)
        }
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "v4war_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual override fun getPlatformName(): String = "Android ${Build.VERSION.RELEASE}"

    actual override suspend fun isNetworkAvailable(): Boolean = withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    actual override suspend fun getDeviceId(): String = withContext(Dispatchers.IO) {
        val prefsKey = "device_id"
        encryptedPrefs.getString(prefsKey, null) ?: run {
            val newId = generateSecureDeviceId()
            encryptedPrefs.edit().putString(prefsKey, newId).apply()
            newId
        }
    }

    private fun generateSecureDeviceId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    actual override suspend fun storeSecure(key: String, value: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                encryptedPrefs.edit().putString(key, value).apply()
            }
        }

    actual override suspend fun retrieveSecure(key: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                encryptedPrefs.getString(key, null)
            }
        }

    actual override suspend fun removeSecure(key: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                encryptedPrefs.edit().remove(key).apply()
            }
        }
}
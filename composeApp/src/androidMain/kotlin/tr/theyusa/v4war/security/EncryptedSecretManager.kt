package tr.theyusa.v4war.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SecretManager {
    suspend fun store(key: String, value: String): Result<Unit>
    suspend fun retrieve(key: String): Result<String?>
    suspend fun remove(key: String): Result<Unit>
    fun wipe(): Boolean
}

class EncryptedSecretManager(
    private val context: Context
) : SecretManager {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "v4war_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun store(key: String, value: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                encryptedPrefs.edit().putString(key, value).apply()
            }
        }

    override suspend fun retrieve(key: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                encryptedPrefs.getString(key, null)
            }
        }

    override suspend fun remove(key: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                encryptedPrefs.edit().remove(key).apply()
            }
        }

    override fun wipe(): Boolean {
        return try {
            encryptedPrefs.edit().clear().apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
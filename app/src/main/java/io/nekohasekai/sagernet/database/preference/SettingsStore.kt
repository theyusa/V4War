package io.nekohasekai.sagernet.database.preference

import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.PreferenceProxy
import io.nekohasekai.sagernet.outbound.json.JsonArray
import io.nekohasekai.sagernet.outbound.json.JsonInput
import java.util.concurrent.ConcurrentHashMap

/**
 * The configuration store: a [PreferenceDataStore] over the `settings` table of [SagerDatabase] in the desktop's
 * encodings (SettingsRepo.cpp): booleans "true"/"false" (read back "true" or "1"), integers as decimal text, string
 * lists and string sets as compact JSON arrays, strings raw. The table is shared by both processes; non-volatile
 * configuration keys use a lightweight short-TTL (300 ms) read cache with synchronous write-through, while volatile
 * cross-process runtime keys bypass caching and always query the DAO directly.
 *
 * The one-argument getters return what is stored (null when the row is missing or unparsable). The two-argument
 * [PreferenceDataStore] getters fall back to [defaults] (the encoded default of a registered key) before the
 * caller's default, so a preference screen shows the effective value of a key without persisting it.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class SettingsStore(
    private val databaseProvider: () -> SagerDatabase,
    private val defaults: (String) -> String? = { null },
) : PreferenceDataStore() {

    private data class CachedRawValue(
        val value: String?,
        val timestampNanos: Long,
    )

    private val rawCache = ConcurrentHashMap<String, CachedRawValue>()

    private val database get() = databaseProvider()
    private val dao get() = database.settingsDao()

    // ------------------------------------------------------------------------------------------------ raw access

    fun contains(key: String): Boolean = getRaw(key) != null

    fun getRaw(key: String): String? {
        if (shouldBypassCache(key)) {
            return dao[key]
        }
        val now = System.nanoTime()
        rawCache[key]?.let { cached ->
            if (now - cached.timestampNanos in 0L..CACHE_TTL_NANOS) {
                return cached.value
            }
        }
        val value = dao[key]
        rawCache[key] = CachedRawValue(value, System.nanoTime())
        return value
    }

    /** Every stored row, sorted by key. */
    fun all(): Map<String, String> = dao.all().associateTo(LinkedHashMap()) { it.key to it.value }

    /** Upserts [values] in one transaction; listeners are not notified (bulk restores restart the app). */
    fun putAll(values: Map<String, String>) {
        if (values.isEmpty()) return
        database.runInTransaction {
            dao.putAll(values.map { (key, value) -> SettingEntry(key, value) })
        }
        val now = System.nanoTime()
        for ((key, value) in values) {
            if (shouldBypassCache(key)) {
                rawCache.remove(key)
            } else {
                rawCache[key] = CachedRawValue(value, now)
            }
        }
    }

    fun reset() {
        dao.reset()
        rawCache.clear()
    }

    fun remove(key: String) {
        dao.delete(key)
        if (shouldBypassCache(key)) {
            rawCache.remove(key)
        } else {
            rawCache[key] = CachedRawValue(null, System.nanoTime())
        }
        fireChangeListener(key)
    }

    private fun write(key: String, value: String) {
        dao.put(SettingEntry(key, value))
        if (shouldBypassCache(key)) {
            rawCache.remove(key)
        } else {
            rawCache[key] = CachedRawValue(value, System.nanoTime())
        }
        fireChangeListener(key)
    }

    // ------------------------------------------------------------------------------------------------ typed reads

    fun getBoolean(key: String): Boolean? = getRaw(key)?.let(::decodeBoolean)
    fun getFloat(key: String): Float? = getRaw(key)?.trim()?.toFloatOrNull()
    fun getInt(key: String): Int? = getRaw(key)?.let(::decodeInt)
    fun getLong(key: String): Long? = getRaw(key)?.let(::decodeLong)
    fun getString(key: String): String? = getRaw(key)
    fun getStringList(key: String): List<String>? = getRaw(key)?.let(::decodeList)
    fun getStringSet(key: String): MutableSet<String>? = getStringList(key)?.toMutableSet()

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        getBoolean(key) ?: defaults(key)?.let(::decodeBoolean) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        getFloat(key) ?: defaults(key)?.trim()?.toFloatOrNull() ?: defValue

    override fun getInt(key: String, defValue: Int): Int =
        getInt(key) ?: defaults(key)?.let(::decodeInt) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        getLong(key) ?: defaults(key)?.let(::decodeLong) ?: defValue

    override fun getString(key: String, defValue: String?): String? =
        getString(key) ?: defaults(key) ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        getStringSet(key) ?: defaults(key)?.let(::decodeList)?.toMutableSet() ?: defValues

    fun getStringList(key: String, defValue: List<String>): List<String> =
        getStringList(key) ?: defaults(key)?.let(::decodeList) ?: defValue

    // ------------------------------------------------------------------------------------------------ typed writes

    override fun putBoolean(key: String, value: Boolean) = write(key, encodeBoolean(value))
    override fun putFloat(key: String, value: Float) = write(key, value.toString())
    override fun putInt(key: String, value: Int) = write(key, value.toString())
    override fun putLong(key: String, value: Long) = write(key, value.toString())

    override fun putString(key: String, value: String?) = if (value == null) remove(key) else write(key, value)

    override fun putStringSet(key: String, values: MutableSet<String>?) =
        if (values == null) remove(key) else write(key, encodeList(values))

    fun putStringList(key: String, values: List<String>?) =
        if (values == null) remove(key) else write(key, encodeList(values))

    fun putBoolean(key: String, value: Boolean?) = if (value == null) remove(key) else putBoolean(key, value)
    fun putFloat(key: String, value: Float?) = if (value == null) remove(key) else putFloat(key, value)
    fun putInt(key: String, value: Int?) = if (value == null) remove(key) else putInt(key, value)
    fun putLong(key: String, value: Long?) = if (value == null) remove(key) else putLong(key, value)

    /** The JSON-array delegate next to the PreferenceDataStore ones of ktx/Preferences.kt. */
    fun stringList(
        name: String,
        defaultValue: () -> List<String> = { emptyList() },
    ) = PreferenceProxy(name, defaultValue, ::getStringList, ::putStringList)

    // ------------------------------------------------------------------------------------------------ listeners

    private val listeners = HashSet<OnPreferenceDataStoreChangeListener>()

    private fun fireChangeListener(key: String) {
        val listeners = synchronized(listeners) {
            listeners.toList()
        }
        listeners.forEach { it.onPreferenceDataStoreChanged(this, key) }
    }

    fun registerChangeListener(listener: OnPreferenceDataStoreChangeListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun unregisterChangeListener(listener: OnPreferenceDataStoreChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 300L
        private const val CACHE_TTL_NANOS = CACHE_TTL_MS * 1_000_000L

        @JvmStatic
        internal fun shouldBypassCache(key: String): Boolean =
            key.startsWith("hotspot_share_") ||
                key.startsWith("hotspot_gateway_") ||
                key.startsWith("inbound_") ||
                key.startsWith("core_box_clash_") ||
                key == "disable_mixed_inbound" ||
                key == "random_inbound_port" ||
                key.startsWith("service_error") ||
                key.startsWith(Key.SERVICE_ERROR) ||
                key.startsWith("running_") ||
                key.startsWith("running") ||
                key == "profile_id" ||
                key == Key.PROFILE_ID ||
                key == "profile_current" ||
                key == Key.PROFILE_CURRENT ||
                key == "service_mode" ||
                key == Key.SERVICE_MODE ||
                key == "start_without_rule_sets" ||
                key == Key.START_WITHOUT_RULE_SETS ||
                key == Key.RESUME_AFTER_UPDATE ||
                key == "vpn_mtu_reset_1500_v1" ||
                key == "fragment_default_reset_v1"

        @JvmStatic
        fun encodeBoolean(value: Boolean): String = if (value) "true" else "false"

        /** SettingsRepo.cpp:312-313. */
        @JvmStatic
        fun decodeBoolean(raw: String): Boolean = raw == "true" || raw == "1"

        @JvmStatic
        fun decodeInt(raw: String): Int? = raw.trim().toIntOrNull()

        @JvmStatic
        fun decodeLong(raw: String): Long? = raw.trim().toLongOrNull()

        /** QJsonDocument(QJsonArray::fromStringList(list)).toJson(Compact): blanks are kept. */
        @JvmStatic
        fun encodeList(values: Collection<String>): String {
            val array = JsonArray()
            for (value in values) array.add(value)
            return array.toCompact()
        }

        /** Null when [raw] is not a JSON array, the case in which the desktop keeps the default. */
        @JvmStatic
        fun decodeList(raw: String): List<String>? = (JsonInput.parseValue(raw) as? JsonArray)?.strings()
    }
}

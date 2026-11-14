package io.nekohasekai.sagernet.database.preference

import androidx.preference.PreferenceDataStore
import java.util.concurrent.ConcurrentHashMap

/**
 * A [PreferenceDataStore] over a [KeyValuePair.Dao] with a thread-safe in-memory read cache.
 *
 * The cache stores the decoded value for a key (or [NULL_SENTINEL] when the row is missing), avoiding repeated
 * Room queries and [java.nio.ByteBuffer] allocations on hot UI/profile paths. Every `put*`, [remove], and [reset]
 * updates the cache synchronously before firing listeners. [getStringSet] and [putStringSet] use immutable snapshots
 * in the cache and return defensive mutable copies so caller mutations never corrupt cached values.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
open class RoomPreferenceDataStore(private val kvPairDao: KeyValuePair.Dao) :
    PreferenceDataStore() {

    private val cache = ConcurrentHashMap<String, Any>()

    private fun decodeCachedValue(pair: KeyValuePair?): Any {
        if (pair == null) return NULL_SENTINEL
        return when (pair.valueType) {
            KeyValuePair.TYPE_BOOLEAN -> pair.boolean ?: NULL_SENTINEL
            KeyValuePair.TYPE_FLOAT -> pair.float ?: NULL_SENTINEL
            @Suppress("DEPRECATION")
            KeyValuePair.TYPE_INT,
            KeyValuePair.TYPE_LONG -> pair.long ?: NULL_SENTINEL
            KeyValuePair.TYPE_STRING -> pair.string ?: NULL_SENTINEL
            KeyValuePair.TYPE_STRING_SET -> pair.stringSet?.toSet() ?: NULL_SENTINEL
            else -> NULL_SENTINEL
        }
    }

    private fun cachedValue(key: String): Any {
        cache[key]?.let { return it }
        val decoded = decodeCachedValue(kvPairDao[key])
        return cache.putIfAbsent(key, decoded) ?: decoded
    }

    fun getBoolean(key: String): Boolean? = cachedValue(key) as? Boolean
    fun getFloat(key: String): Float? = cachedValue(key) as? Float
    fun getInt(key: String): Int? = getLong(key)?.toInt()
    fun getLong(key: String): Long? = cachedValue(key) as? Long
    fun getString(key: String): String? = cachedValue(key) as? String

    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String): Set<String>? = (cachedValue(key) as? Set<String>)?.toMutableSet()

    fun reset() {
        kvPairDao.reset()
        cache.clear()
    }

    override fun getBoolean(key: String, defValue: Boolean) = getBoolean(key) ?: defValue
    override fun getFloat(key: String, defValue: Float) = getFloat(key) ?: defValue
    override fun getInt(key: String, defValue: Int) = getInt(key) ?: defValue
    override fun getLong(key: String, defValue: Long) = getLong(key) ?: defValue
    override fun getString(key: String, defValue: String?) = getString(key) ?: defValue
    override fun getStringSet(key: String, defValue: MutableSet<String>?) =
        getStringSet(key)?.toMutableSet() ?: defValue

    fun putBoolean(key: String, value: Boolean?) =
        if (value == null) remove(key) else putBoolean(key, value)

    fun putFloat(key: String, value: Float?) =
        if (value == null) remove(key) else putFloat(key, value)

    fun putInt(key: String, value: Int?) =
        if (value == null) remove(key) else putLong(key, value.toLong())

    fun putLong(key: String, value: Long?) = if (value == null) remove(key) else putLong(key, value)
    override fun putBoolean(key: String, value: Boolean) {
        kvPairDao.put(KeyValuePair(key).put(value))
        cache[key] = value
        fireChangeListener(key)
    }

    override fun putFloat(key: String, value: Float) {
        kvPairDao.put(KeyValuePair(key).put(value))
        cache[key] = value
        fireChangeListener(key)
    }

    override fun putInt(key: String, value: Int) {
        val longVal = value.toLong()
        kvPairDao.put(KeyValuePair(key).put(longVal))
        cache[key] = longVal
        fireChangeListener(key)
    }

    override fun putLong(key: String, value: Long) {
        kvPairDao.put(KeyValuePair(key).put(value))
        cache[key] = value
        fireChangeListener(key)
    }

    override fun putString(key: String, value: String?) = if (value == null) remove(key) else {
        kvPairDao.put(KeyValuePair(key).put(value))
        cache[key] = value
        fireChangeListener(key)
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) =
        if (values == null) remove(key) else {
            val snapshot = values.toSet()
            kvPairDao.put(KeyValuePair(key).put(snapshot))
            cache[key] = snapshot
            fireChangeListener(key)
        }

    fun remove(key: String) {
        kvPairDao.delete(key)
        cache[key] = NULL_SENTINEL
        fireChangeListener(key)
    }

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

    private companion object {
        val NULL_SENTINEL = Any()
    }
}

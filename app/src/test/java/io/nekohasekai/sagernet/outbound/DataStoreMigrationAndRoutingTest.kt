package io.nekohasekai.sagernet.outbound

import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.FRAGMENT_DEFAULT_RESET_V1_KEY
import io.nekohasekai.sagernet.database.SettingsMapper
import io.nekohasekai.sagernet.database.SettingsRegistry
import io.nekohasekai.sagernet.database.VPN_MTU_RESET_1500_V1_KEY
import io.nekohasekai.sagernet.database.migrateFragmentDefaultReset
import io.nekohasekai.sagernet.database.migrateVpnMtuDefaultReset
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.database.preference.SettingsStore
import io.nekohasekai.sagernet.outbound.config.ConfigGenerator
import io.nekohasekai.sagernet.outbound.config.GeneratorSettings
import io.nekohasekai.sagernet.outbound.config.ProfileProvider
import io.nekohasekai.sagernet.outbound.config.Tags
import io.nekohasekai.sagernet.outbound.json.JsonInput
import io.nekohasekai.sagernet.outbound.json.JsonObject
import io.nekohasekai.sagernet.outbound.types.Vless
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreMigrationAndRoutingTest {

    private class InMemoryPreferenceStore(
        initialBooleans: Map<String, Boolean> = emptyMap(),
        initialStrings: Map<String, String> = emptyMap(),
    ) : PreferenceDataStore() {
        private val booleans = initialBooleans.toMutableMap()
        private val strings = initialStrings.toMutableMap()

        override fun getBoolean(key: String, defValue: Boolean): Boolean = booleans[key] ?: defValue

        override fun putBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }

        override fun getString(key: String, defValue: String?): String? = strings[key] ?: defValue

        override fun putString(key: String, value: String?) {
            if (value == null) strings.remove(key) else strings[key] = value
        }

        override fun getInt(key: String, defValue: Int): Int =
            strings[key]?.trim()?.toIntOrNull() ?: defValue

        override fun putInt(key: String, value: Int) {
            strings[key] = value.toString()
        }
    }

    private class CountingKeyValuePairDao : KeyValuePair.Dao {
        private val map = LinkedHashMap<String, KeyValuePair>()
        var getCalls = 0

        override fun all(): List<KeyValuePair> = map.values.toList()

        override fun get(key: String): KeyValuePair? {
            getCalls++
            return map[key]
        }

        override fun put(value: KeyValuePair): Long {
            map[value.key] = value
            return 1L
        }

        override fun delete(key: String): Int = if (map.remove(key) != null) 1 else 0

        override fun reset(): Int {
            val count = map.size
            map.clear()
            return count
        }

        override fun insert(list: List<KeyValuePair>) {
            for (item in list) map[item.key] = item
        }
    }

    @Test
    fun fragmentDefaultMigrationResetsLegacyTrueOnceAndPreservesLaterOptIn() {
        assertFalse(SettingsRegistry.FRAGMENT_DEFAULT_ON.default)

        val store = InMemoryPreferenceStore(
            initialBooleans = mapOf(SettingsRegistry.FRAGMENT_DEFAULT_ON.key to true),
        )

        migrateFragmentDefaultReset(store)
        assertFalse(store.getBoolean(SettingsRegistry.FRAGMENT_DEFAULT_ON.key, true))
        assertTrue(store.getBoolean(FRAGMENT_DEFAULT_RESET_V1_KEY, false))

        // Subsequent explicit opt-in must survive later initGlobal / migration passes.
        store.putBoolean(SettingsRegistry.FRAGMENT_DEFAULT_ON.key, true)
        migrateFragmentDefaultReset(store)
        assertTrue(store.getBoolean(SettingsRegistry.FRAGMENT_DEFAULT_ON.key, false))
    }

    @Test
    fun vpnMtuDefaultIs1500AndMigrationResetsLegacy9000OnceWhilePreservingCustomValues() {
        assertEquals(1500, SettingsRegistry.VPN_MTU.default)
        assertEquals(1500, GeneratorSettings().tunMtu)

        // Legacy 9000 default is migrated once to 1500.
        val legacyStore = InMemoryPreferenceStore(
            initialStrings = mapOf(SettingsRegistry.VPN_MTU.key to "9000"),
        )
        migrateVpnMtuDefaultReset(legacyStore)
        assertEquals("1500", legacyStore.getString(SettingsRegistry.VPN_MTU.key, null))
        assertEquals(1500, legacyStore.getInt(SettingsRegistry.VPN_MTU.key, 9000))
        assertTrue(legacyStore.getBoolean(VPN_MTU_RESET_1500_V1_KEY, false))

        // Subsequent explicit 9000 after migration flag is recorded must be preserved.
        legacyStore.putString(SettingsRegistry.VPN_MTU.key, "9000")
        migrateVpnMtuDefaultReset(legacyStore)
        assertEquals("9000", legacyStore.getString(SettingsRegistry.VPN_MTU.key, null))

        // Custom user MTU (e.g. 1400) is preserved on first migration pass.
        val customStore = InMemoryPreferenceStore(
            initialStrings = mapOf(SettingsRegistry.VPN_MTU.key to "1400"),
        )
        migrateVpnMtuDefaultReset(customStore)
        assertEquals("1400", customStore.getString(SettingsRegistry.VPN_MTU.key, null))
        assertTrue(customStore.getBoolean(VPN_MTU_RESET_1500_V1_KEY, false))
    }

    @Test
    fun roomPreferenceDataStoreCachesReadsAndProtectsStringSetSnapshots() {
        val dao = CountingKeyValuePairDao()
        val store = RoomPreferenceDataStore(dao)

        assertNull(store.getString("missing"))
        assertNull(store.getString("missing"))
        assertEquals(1, dao.getCalls)

        store.putString("host", "example.com")
        assertEquals("example.com", store.getString("host"))
        assertEquals("example.com", store.getString("host"))
        assertEquals(1, dao.getCalls)

        val inputSet = mutableSetOf("a", "b")
        store.putStringSet("tags", inputSet)
        inputSet.add("mutated-after-put")

        val firstRead = store.getStringSet("tags", null)
        assertEquals(setOf("a", "b"), firstRead)
        firstRead?.add("mutated-after-get")

        assertEquals(setOf("a", "b"), store.getStringSet("tags", null))
        assertEquals(1, dao.getCalls)

        store.remove("host")
        assertNull(store.getString("host"))
        assertEquals(1, dao.getCalls)
    }

    @Test
    fun settingsStoreBypassCachePolicyCoversCrossProcessVolatileKeys() {
        assertTrue(SettingsStore.shouldBypassCache("hotspot_share_status"))
        assertTrue(SettingsStore.shouldBypassCache("hotspot_gateway_status"))
        assertTrue(SettingsStore.shouldBypassCache("service_error"))
        assertTrue(SettingsStore.shouldBypassCache(Key.SERVICE_ERROR))
        assertTrue(SettingsStore.shouldBypassCache("running_profiles"))
        assertTrue(SettingsStore.shouldBypassCache(Key.RUNNING_PROFILES))
        assertTrue(SettingsStore.shouldBypassCache("profile_id"))
        assertTrue(SettingsStore.shouldBypassCache(Key.PROFILE_ID))
        assertTrue(SettingsStore.shouldBypassCache("profile_current"))
        assertTrue(SettingsStore.shouldBypassCache(Key.PROFILE_CURRENT))
        assertTrue(SettingsStore.shouldBypassCache("service_mode"))
        assertTrue(SettingsStore.shouldBypassCache(Key.SERVICE_MODE))
        assertTrue(SettingsStore.shouldBypassCache("start_without_rule_sets"))
        assertTrue(SettingsStore.shouldBypassCache(Key.START_WITHOUT_RULE_SETS))
        assertTrue(SettingsStore.shouldBypassCache(VPN_MTU_RESET_1500_V1_KEY))
        assertTrue(SettingsStore.shouldBypassCache(FRAGMENT_DEFAULT_RESET_V1_KEY))

        assertFalse(SettingsStore.shouldBypassCache(SettingsRegistry.VPN_MTU.key))
        assertFalse(SettingsStore.shouldBypassCache(SettingsRegistry.REMOTE_DNS.key))
    }

    @Test
    fun shareSocksInboundEnabledWheneverMethodAllowsShizuku() {
        assertTrue(SettingsMapper.isShareSocksInboundNeeded(enabled = false, method = "auto"))
        assertTrue(SettingsMapper.isShareSocksInboundNeeded(enabled = false, method = "shizuku"))
        assertTrue(SettingsMapper.isShareSocksInboundNeeded(enabled = true, method = "auto"))
        assertTrue(SettingsMapper.isShareSocksInboundNeeded(enabled = true, method = "shizuku"))
        assertFalse(SettingsMapper.isShareSocksInboundNeeded(enabled = false, method = "root"))
        assertFalse(SettingsMapper.isShareSocksInboundNeeded(enabled = false, method = "lan_proxy"))
        assertFalse(SettingsMapper.isShareSocksInboundNeeded(enabled = true, method = "root"))
        assertFalse(SettingsMapper.isShareSocksInboundNeeded(enabled = true, method = "lan_proxy"))
    }

    @Test
    fun tunInboundSetsFixedInterfaceNameToSkipNetlinkProbing() {
        val profile = Vless().apply {
            server = "185.157.217.124"
            serverPort = 443
            uuid = "a7c60bce-79c0-4442-9369-7a652b36c625"
            tls.enabled = true
            tls.server_name = "example.com"
        }
        val result = ConfigGenerator(
            profiles = ProfileProvider { id -> if (id == 1L) profile else null },
            settings = GeneratorSettings(vpnMode = true),
            buildContext = BuildContext.DEFAULT,
        ).build(1L)
        assertTrue(result.error, result.ok)

        val json = JsonInput.parseObject(result.coreConfig)
        val tunInbound = json.array("inbounds").filterIsInstance<JsonObject>()
            .firstOrNull { it.string("tag") == Tags.TUN_IN }
        assertEquals("tun", tunInbound?.string("type"))
        assertEquals("tun0", tunInbound?.string("interface_name"))
    }

    @Test
    fun shareSocksInboundAndResolveRuleMatchUpstreamWhenDisabledAndScopeCorrectlyWhenEnabled() {
        val profile = Vless().apply {
            server = "185.157.217.124"
            serverPort = 443
            uuid = "a7c60bce-79c0-4442-9369-7a652b36c625"
            tls.enabled = true
            tls.server_name = "example.com"
        }
        val profiles = ProfileProvider { id -> if (id == 1L) profile else null }

        val withoutShare = ConfigGenerator(
            profiles = profiles,
            settings = GeneratorSettings(
                shareSocksInboundEnabled = false,
                resolveDomainStrategy = "prefer_ipv4",
            ),
            buildContext = BuildContext.DEFAULT,
        ).build(1L)
        assertTrue(withoutShare.error, withoutShare.ok)

        val offJson = JsonInput.parseObject(withoutShare.coreConfig)
        val inboundsOff = offJson.array("inbounds").filterIsInstance<JsonObject>()
        assertNull(inboundsOff.firstOrNull { it.string("tag") == Tags.SHARE_SOCKS_IN })

        val resolveOff = offJson.obj("route").array("rules").filterIsInstance<JsonObject>()
            .firstOrNull { it.string("action") == "resolve" }
        assertEquals(listOf(Tags.MIXED_IN, Tags.TUN_IN), resolveOff?.array("inbound")?.strings())

        val withShare = ConfigGenerator(
            profiles = profiles,
            settings = GeneratorSettings(
                shareSocksInboundEnabled = true,
                shareSocksPort = 2088,
                resolveDomainStrategy = "prefer_ipv4",
            ),
            buildContext = BuildContext.DEFAULT,
        ).build(1L)
        assertTrue(withShare.error, withShare.ok)

        val onJson = JsonInput.parseObject(withShare.coreConfig)
        val inboundsOn = onJson.array("inbounds").filterIsInstance<JsonObject>()
        val shareInbound = inboundsOn.firstOrNull { it.string("tag") == Tags.SHARE_SOCKS_IN }
        assertEquals("socks", shareInbound?.string("type"))
        assertEquals("127.0.0.1", shareInbound?.string("listen"))
        assertEquals(2088, shareInbound?.int("listen_port"))

        val resolveOn = onJson.obj("route").array("rules").filterIsInstance<JsonObject>()
            .firstOrNull { it.string("action") == "resolve" }
        assertEquals(
            listOf(Tags.MIXED_IN, Tags.TUN_IN, Tags.SHARE_SOCKS_IN),
            resolveOn?.array("inbound")?.strings(),
        )
    }
}

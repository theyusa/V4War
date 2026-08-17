package tr.theyusa.v4war.database

import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import tr.theyusa.v4war.database.SagerDatabase
import tr.theyusa.v4war.fmt.AbstractBean
import tr.theyusa.v4war.fmt.KryoConverters
import tr.theyusa.v4war.fmt.Serializable
import tr.theyusa.v4war.fmt.anytls.AnyTLSBean
import tr.theyusa.v4war.fmt.buildConfig
import tr.theyusa.v4war.fmt.buildSingBoxOutbound
import tr.theyusa.v4war.fmt.config.ConfigBean
import tr.theyusa.v4war.fmt.direct.DirectBean
import tr.theyusa.v4war.fmt.http.HttpBean
import tr.theyusa.v4war.fmt.hysteria.HysteriaBean
import tr.theyusa.v4war.fmt.hysteria.buildHysteriaConfig
import tr.theyusa.v4war.fmt.hysteria.canUseSingBox
import tr.theyusa.v4war.fmt.hysteria.toUri
import tr.theyusa.v4war.fmt.internal.ChainBean
import tr.theyusa.v4war.fmt.internal.ProxySetBean
import tr.theyusa.v4war.fmt.juicity.JuicityBean
import tr.theyusa.v4war.fmt.mieru.MieruBean
import tr.theyusa.v4war.fmt.naive.NaiveBean
import tr.theyusa.v4war.fmt.shadowquic.ShadowQUICBean
import tr.theyusa.v4war.fmt.shadowsocks.ShadowsocksBean
import tr.theyusa.v4war.fmt.shadowsocks.toUri
import tr.theyusa.v4war.fmt.shadowtls.ShadowTLSBean
import tr.theyusa.v4war.fmt.socks.SOCKSBean
import tr.theyusa.v4war.fmt.socks.toUri
import tr.theyusa.v4war.fmt.ssh.SSHBean
import tr.theyusa.v4war.fmt.toUniversalLink
import tr.theyusa.v4war.fmt.trojan.TrojanBean
import tr.theyusa.v4war.fmt.trusttunnel.TrustTunnelBean
import tr.theyusa.v4war.fmt.tuic.TuicBean
import tr.theyusa.v4war.fmt.tuic.toUri
import tr.theyusa.v4war.fmt.v2ray.VLESSBean
import tr.theyusa.v4war.fmt.v2ray.VMessBean
import tr.theyusa.v4war.fmt.v2ray.toUriVMessVLESSTrojan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

@Entity(
    tableName = "proxy_entities", indices = [Index("groupId", name = "groupId")],
)
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var groupId: Long = 0L,
    var type: Int = 0,
    var userOrder: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
    var status: Int = STATUS_INITIAL,
    var ping: Int = 0,
    var error: String? = null,
    var socksBean: SOCKSBean? = null,
    var httpBean: HttpBean? = null,
    var ssBean: ShadowsocksBean? = null,
    var vmessBean: VMessBean? = null,
    var vlessBean: VLESSBean? = null,
    var trojanBean: TrojanBean? = null,
    var mieruBean: MieruBean? = null,
    var naiveBean: NaiveBean? = null,
    var hysteriaBean: HysteriaBean? = null,
    var tuicBean: TuicBean? = null,
    var juicityBean: JuicityBean? = null,
    var sshBean: SSHBean? = null,
    var shadowTLSBean: ShadowTLSBean? = null,
    var directBean: DirectBean? = null,
    var anyTLSBean: AnyTLSBean? = null,
    var shadowQUICBean: ShadowQUICBean? = null,
    var trustTunnelBean: TrustTunnelBean? = null,
    var proxySetBean: ProxySetBean? = null,
    var chainBean: ChainBean? = null,
    var configBean: ConfigBean? = null,
) : Serializable() {

    companion object {
        const val TYPE_SOCKS = 0
        const val TYPE_HTTP = 1
        const val TYPE_SS = 2
        const val TYPE_VMESS = 4
        const val TYPE_VLESS = 5
        const val TYPE_TROJAN = 6
        const val TYPE_TROJAN_GO = 7 // Deleted
        const val TYPE_CHAIN = 8
        const val TYPE_NAIVE = 9
        const val TYPE_HYSTERIA = 15
        const val TYPE_SSH = 17
        const val TYPE_SHADOWTLS = 19
        const val TYPE_TUIC = 20
        const val TYPE_MIERU = 21
        const val TYPE_JUICITY = 22
        const val TYPE_DIRECT = 23
        const val TYPE_ANYTLS = 24
        const val TYPE_SHADOWQUIC = 25
        const val TYPE_PROXY_SET = 26
        const val TYPE_TRUST_TUNNEL = 27
        const val TYPE_CONFIG = 998
        const val TYPE_NEKO = 999 // Deleted

        /** Plugin not found or not support this ping type */
        const val STATUS_INVALID = -1
        const val STATUS_INITIAL = 0
        const val STATUS_AVAILABLE = 1

        /** Unclear */
        const val STATUS_UNREACHABLE = 2

        /** Has obvious error */
        const val STATUS_UNAVAILABLE = 3

        @JvmField
        val CREATOR = object : CREATOR<ProxyEntity>() {

            override fun newInstance(): ProxyEntity {
                return ProxyEntity()
            }

            override fun newArray(size: Int): Array<ProxyEntity?> {
                return arrayOfNulls(size)
            }
        }
    }

    @Ignore
    @Transient
    var dirty: Boolean = false

    override fun initializeDefaultValues() {
    }

    override fun serializeToBuffer(output: ByteBufferOutput) {
        output.writeInt(1)

        output.writeLong(id)
        output.writeLong(groupId)
        output.writeInt(type)
        output.writeLong(userOrder)
        output.writeLong(tx)
        output.writeLong(rx)
        output.writeInt(status)
        output.writeInt(ping)
        output.writeString(error)

        val data = KryoConverters.serialize(requireBean())
        output.writeVarInt(data.size, true)
        output.writeBytes(data)

        output.writeBoolean(dirty)
    }

    override fun deserializeFromBuffer(input: ByteBufferInput) {
        val version = input.readInt()

        id = input.readLong()
        groupId = input.readLong()
        type = input.readInt()
        userOrder = input.readLong()
        tx = input.readLong()
        rx = input.readLong()
        status = input.readInt()
        ping = input.readInt()
        if (version < 1) {
            // useless uuid
            input.readString()
        }
        error = input.readString()
        putByteArray(input.readBytes(input.readVarInt(true)))

        dirty = input.readBoolean()
    }


    fun putByteArray(byteArray: ByteArray) {
        when (type) {
            TYPE_SOCKS -> socksBean = KryoConverters.socksDeserialize(byteArray)
            TYPE_SS -> ssBean = KryoConverters.shadowsocksDeserialize(byteArray)
            TYPE_VMESS -> vmessBean = KryoConverters.vmessDeserialize(byteArray)
            TYPE_VLESS -> vlessBean = KryoConverters.vlessDeserialize(byteArray)
            TYPE_TROJAN -> trojanBean = KryoConverters.trojanDeserialize(byteArray)
            TYPE_HYSTERIA -> hysteriaBean = KryoConverters.hysteriaDeserialize(byteArray)
            TYPE_SSH -> sshBean = KryoConverters.sshDeserialize(byteArray)
            TYPE_TUIC -> tuicBean = KryoConverters.tuicDeserialize(byteArray)
            TYPE_PROXY_SET -> proxySetBean = KryoConverters.proxySetDeserialize(byteArray)
            TYPE_CHAIN -> chainBean = KryoConverters.chainDeserialize(byteArray)
            TYPE_CONFIG -> configBean = KryoConverters.configDeserialize(byteArray)
        }
    }

    fun displayName() = requireBean().displayName()
    fun displayAddress() = requireBean().displayAddress()
    fun displayNameForService(): String {
        val profileName = displayName()
        val groupName = if (DataStore.showGroupInNotification) runBlocking {
            SagerDatabase.groupDao.getById(groupId).firstOrNull()?.displayName()
        } else {
            null
        }
        return if (groupName == null) profileName else "[$groupName] $profileName"
    }

    fun requireBean(): AbstractBean {
        return when (type) {
            TYPE_SOCKS -> socksBean
            TYPE_SS -> ssBean
            TYPE_VMESS -> vmessBean
            TYPE_VLESS -> vlessBean
            TYPE_TROJAN -> trojanBean
            TYPE_HYSTERIA -> hysteriaBean
            TYPE_SSH -> sshBean
            TYPE_TUIC -> tuicBean
            TYPE_PROXY_SET -> proxySetBean
            TYPE_CHAIN -> chainBean
            TYPE_CONFIG -> configBean
            else -> error("Undefined type $type")
        } ?: error("Null $type profile")
    }

    /** Determines if has internal link. */
    fun haveLink(): Boolean = when (type) {
        TYPE_PROXY_SET -> false
        TYPE_CHAIN -> false
        else -> true
    }

    /** Determines if has standard link. */
    fun haveStandardLink(): Boolean = when (type) {
        TYPE_SSH -> false
        TYPE_PROXY_SET -> false
        TYPE_CHAIN -> false
        TYPE_CONFIG -> false
        else -> true
    }

    fun toStdLink(): String = with(requireBean()) {
        when (this) {
            is SOCKSBean -> toUri()
            is ShadowsocksBean -> toUri()
            is VMessBean -> toUriVMessVLESSTrojan()
            is VLESSBean -> toUriVMessVLESSTrojan()
            is TrojanBean -> toUriVMessVLESSTrojan()
            is HysteriaBean -> toUri()
            is TuicBean -> toUri()
            else -> toUniversalLink()
        }
    }

    fun mustUsePlugin(): Boolean = when (type) {
        TYPE_HYSTERIA -> !hysteriaBean!!.canUseSingBox()
        else -> false
    }

    private val exportName get() = "${requireBean().displayName()}.json"

    fun exportConfig(): Pair<String, String> {
        return with(requireBean()) {
            StringBuilder().apply {
                val config = buildConfig(this@ProxyEntity, forExport = true)
                append(config.config)

                if (!config.externalIndex.all { it.chain.isEmpty() }) {
                    name = "profiles.txt"
                }

                for ((chain) in config.externalIndex) {
                    chain.entries.forEach { (port, profile) ->
                        when (val bean = profile.requireBean()) {
                            is HysteriaBean -> {
                                append("\n\n")
                                append(bean.buildHysteriaConfig(port, false, null))
                            }
                        }
                    }
                }
            }.toString()
        } to exportName
    }

    fun exportOutbound(): Pair<String, String> = buildSingBoxOutbound(requireBean()) to exportName

    fun needExternal(): Boolean {
        return when (type) {
            TYPE_HYSTERIA -> !hysteriaBean!!.canUseSingBox()
            else -> false
        }
    }

    fun putBean(bean: AbstractBean): ProxyEntity {
        socksBean = null
        ssBean = null
        vmessBean = null
        vlessBean = null
        trojanBean = null
        hysteriaBean = null
        sshBean = null
        tuicBean = null
        proxySetBean = null
        chainBean = null
        configBean = null

        when (bean) {
            is SOCKSBean -> {
                type = TYPE_SOCKS
                socksBean = bean
            }

            is ShadowsocksBean -> {
                type = TYPE_SS
                ssBean = bean
            }

            is VMessBean -> {
                type = TYPE_VMESS
                vmessBean = bean
            }

            is VLESSBean -> {
                type = TYPE_VLESS
                vlessBean = bean
            }

            is TrojanBean -> {
                type = TYPE_TROJAN
                trojanBean = bean
            }

            is HysteriaBean -> {
                type = TYPE_HYSTERIA
                hysteriaBean = bean
            }

            is SSHBean -> {
                type = TYPE_SSH
                sshBean = bean
            }

            is TuicBean -> {
                type = TYPE_TUIC
                tuicBean = bean
            }

            is ProxySetBean -> {
                type = TYPE_PROXY_SET
                proxySetBean = bean
            }

            is ChainBean -> {
                type = TYPE_CHAIN
                chainBean = bean
            }

            is ConfigBean -> {
                type = TYPE_CONFIG
                configBean = bean
            }

            else -> error("Undefined type $type")
        }
        return this
    }

    @androidx.room.Dao
    interface Dao {

        @Query("select * from proxy_entities")
        suspend fun getAll(): List<ProxyEntity>

        @Query("SELECT id FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        suspend fun getIdsByGroup(groupId: Long): List<Long>

        @Query("SELECT * FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getByGroup(groupId: Long): Flow<List<ProxyEntity>>

        @Query("SELECT * FROM proxy_entities WHERE id in (:proxyIds)")
        suspend fun getEntities(proxyIds: List<Long>): List<ProxyEntity>

        @Query("SELECT COUNT(*) FROM proxy_entities WHERE groupId = :groupId")
        fun countByGroup(groupId: Long): Flow<Long>

        @Query("SELECT  MAX(userOrder) + 1 FROM proxy_entities WHERE groupId = :groupId")
        suspend fun nextOrder(groupId: Long): Long?

        @Query("SELECT * FROM proxy_entities WHERE id = :proxyId")
        suspend fun getById(proxyId: Long): ProxyEntity?

        @Query("DELETE FROM proxy_entities WHERE id IN (:proxyId)")
        suspend fun deleteById(proxyId: Long): Int

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        suspend fun deleteByGroup(groupId: Long)

        @Query("DELETE FROM proxy_entities WHERE groupId in (:groupId)")
        suspend fun deleteByGroup(groupId: LongArray)

        @Delete
        suspend fun deleteProxy(proxy: ProxyEntity): Int

        @Delete
        suspend fun deleteProxy(proxies: List<ProxyEntity>): Int

        @Query("DELETE FROM proxy_entities WHERE id IN (:proxyIds)")
        suspend fun deleteProxies(proxyIds: List<Long>): Int

        @Update
        suspend fun updateProxy(proxy: ProxyEntity): Int

        @Update
        suspend fun updateProxy(proxies: List<ProxyEntity>): Int

        @Insert
        suspend fun addProxy(proxy: ProxyEntity): Long

        @Insert
        suspend fun insert(proxies: List<ProxyEntity>)

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        suspend fun deleteAll(groupId: Long): Int

        @Query("DELETE FROM proxy_entities")
        suspend fun reset()

        /**
         * Though UI disallow edit config when it is running,
         * but like chain and front/landing proxy still can be edited when running.
         * This can just update the traffic of a proxy entity when not influence other settings.
         */
        @Query(
            """
        UPDATE proxy_entities
           SET tx = CASE WHEN :tx  IS NULL THEN tx  ELSE :tx  END,
               rx = CASE WHEN :rx  IS NULL THEN rx  ELSE :rx  END
         WHERE id = :id
    """,
        )
        suspend fun updateTraffic(id: Long, tx: Long?, rx: Long?): Int

        @Transaction
        suspend fun syncProxies(
            toInsert: List<ProxyEntity>,
            toUpdate: List<ProxyEntity>,
            toDelete: List<ProxyEntity>
        ) {
            if (toInsert.isNotEmpty()) {
                insert(toInsert)
            }
            if (toUpdate.isNotEmpty()) {
                updateProxy(toUpdate)
            }
            if (toDelete.isNotEmpty()) {
                deleteProxy(toDelete)
            }
        }
    }

    override fun describeContents(): Int {
        return 0
    }
}

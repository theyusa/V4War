package tr.theyusa.v4war.fmt

import androidx.room.TypeConverter
import com.esotericsoftware.kryo.KryoException
import tr.theyusa.v4war.database.SubscriptionBean
import tr.theyusa.v4war.fmt.anytls.AnyTLSBean
import tr.theyusa.v4war.fmt.config.ConfigBean
import tr.theyusa.v4war.fmt.direct.DirectBean
import tr.theyusa.v4war.fmt.http.HttpBean
import tr.theyusa.v4war.fmt.hysteria.HysteriaBean
import tr.theyusa.v4war.fmt.internal.ChainBean
import tr.theyusa.v4war.fmt.internal.ProxySetBean
import tr.theyusa.v4war.fmt.juicity.JuicityBean
import tr.theyusa.v4war.fmt.mieru.MieruBean
import tr.theyusa.v4war.fmt.naive.NaiveBean
import tr.theyusa.v4war.fmt.shadowquic.ShadowQUICBean
import tr.theyusa.v4war.fmt.shadowsocks.ShadowsocksBean
import tr.theyusa.v4war.fmt.shadowtls.ShadowTLSBean
import tr.theyusa.v4war.fmt.socks.SOCKSBean
import tr.theyusa.v4war.fmt.ssh.SSHBean
import tr.theyusa.v4war.fmt.trojan.TrojanBean
import tr.theyusa.v4war.fmt.trusttunnel.TrustTunnelBean
import tr.theyusa.v4war.fmt.tuic.TuicBean
import tr.theyusa.v4war.fmt.v2ray.VLESSBean
import tr.theyusa.v4war.fmt.v2ray.VMessBean
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.ktx.byteBuffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KryoConverters {

    companion object {
        private val NULL = ByteArray(0)

        @JvmStatic
        fun serialize(bean: Serializable?): ByteArray {
            if (bean == null) return NULL
            val out = ByteArrayOutputStream()
            val buffer = out.byteBuffer()
            bean.serializeToBuffer(buffer)
            buffer.flush()
            buffer.close()
            return out.toByteArray()
        }

        @TypeConverter
        @JvmStatic
        fun serializeForRoom(bean: Serializable?): ByteArray? = serialize(bean)

        @JvmStatic
        fun <T : Serializable> deserialize(bean: T, bytes: ByteArray?): T {
            if (bytes == null) return bean
            val input = ByteArrayInputStream(bytes)
            val buffer = input.byteBuffer()
            try {
                bean.deserializeFromBuffer(buffer)
            } catch (e: KryoException) {
                Logs.w(e)
            }
            bean.initializeDefaultValues()
            return bean
        }

        @TypeConverter
        @JvmStatic
        fun socksDeserialize(bytes: ByteArray?): SOCKSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SOCKSBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun httpDeserialize(bytes: ByteArray?): HttpBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(HttpBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun shadowsocksDeserialize(bytes: ByteArray?): ShadowsocksBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowsocksBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun configDeserialize(bytes: ByteArray?): ConfigBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ConfigBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun vmessDeserialize(bytes: ByteArray?): VMessBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(VMessBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun vlessDeserialize(bytes: ByteArray?): VLESSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(VLESSBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun trojanDeserialize(bytes: ByteArray?): TrojanBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TrojanBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun mieruDeserialize(bytes: ByteArray?): MieruBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(MieruBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun naiveDeserialize(bytes: ByteArray?): NaiveBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(NaiveBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun hysteriaDeserialize(bytes: ByteArray?): HysteriaBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(HysteriaBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun sshDeserialize(bytes: ByteArray?): SSHBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SSHBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun tuicDeserialize(bytes: ByteArray?): TuicBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TuicBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun juicityDeserialize(bytes: ByteArray?): JuicityBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(JuicityBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun directDeserialize(bytes: ByteArray?): DirectBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(DirectBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun anyTLSDeserialize(bytes: ByteArray?): AnyTLSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(AnyTLSBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun shadowTLSDeserialize(bytes: ByteArray?): ShadowTLSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowTLSBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun shadowQUICDeserialize(bytes: ByteArray?): ShadowQUICBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowQUICBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun proxySetDeserialize(bytes: ByteArray?): ProxySetBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ProxySetBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun trustTunnelDeserialize(bytes: ByteArray?): TrustTunnelBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TrustTunnelBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun chainDeserialize(bytes: ByteArray?): ChainBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ChainBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun subscriptionDeserialize(bytes: ByteArray?): SubscriptionBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SubscriptionBean(), bytes)
        }
    }
}

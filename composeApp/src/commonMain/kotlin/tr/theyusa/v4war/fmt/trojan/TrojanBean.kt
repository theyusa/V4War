package tr.theyusa.v4war.fmt.trojan

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import tr.theyusa.v4war.fmt.KryoConverters
import tr.theyusa.v4war.fmt.v2ray.StandardV2RayBean

@KxsSerializable
class TrojanBean : StandardV2RayBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<TrojanBean>() {
            override fun newInstance(): TrojanBean {
                return TrojanBean()
            }

            override fun newArray(size: Int): Array<TrojanBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var password: String = ""

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(password)
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input) // StandardV2RayBean
        password = input.readString()
    }

    override fun clone(): TrojanBean {
        return KryoConverters.deserialize(TrojanBean(), KryoConverters.serialize(this))
    }

    override val defaultPort get() = 443

}

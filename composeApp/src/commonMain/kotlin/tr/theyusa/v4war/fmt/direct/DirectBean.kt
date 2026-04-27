package tr.theyusa.v4war.fmt.direct

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import tr.theyusa.v4war.fmt.AbstractBean
import tr.theyusa.v4war.fmt.KryoConverters
import tr.theyusa.v4war.fmt.SingBoxOptions

@KxsSerializable
class DirectBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<DirectBean>() {
            override fun newInstance(): DirectBean {
                return DirectBean()
            }

            override fun newArray(size: Int): Array<DirectBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
    }

    override fun clone(): DirectBean {
        return KryoConverters.deserialize(DirectBean(), KryoConverters.serialize(this))
    }

    override fun displayName(): String {
        if (name.isEmpty()) {
            return SingBoxOptions.TYPE_DIRECT
        }
        return name
    }

    override fun displayAddress(): String {
        return ""
    }

    override val canICMPing get() = true
    override val canTCPing get() = true
}

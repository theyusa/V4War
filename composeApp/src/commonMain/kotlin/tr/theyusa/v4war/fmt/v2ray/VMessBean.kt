package tr.theyusa.v4war.fmt.v2ray

import kotlinx.serialization.Serializable as KxsSerializable
import tr.theyusa.v4war.fmt.AbstractBean
import tr.theyusa.v4war.fmt.KryoConverters

@KxsSerializable
class VMessBean : StandardV2RayBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<VMessBean>() {
            override fun newInstance(): VMessBean {
                return VMessBean()
            }

            override fun newArray(size: Int): Array<VMessBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var alterId: Int = 0
    var authenticatedLength: Boolean = false

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (encryption.isBlank()) encryption = "auto"
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is VMessBean) return
        if (authenticatedLength) {
            other.authenticatedLength = true
        }
    }

    override fun clone(): VMessBean {
        return KryoConverters.deserialize(VMessBean(), KryoConverters.serialize(this))
    }

    override val defaultPort get() = 10086
}

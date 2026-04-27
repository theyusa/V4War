package tr.theyusa.v4war.fmt.internal

import kotlinx.serialization.Serializable as KxsSerializable
import tr.theyusa.v4war.fmt.AbstractBean

@KxsSerializable
abstract class InternalBean : AbstractBean() {

    override fun displayAddress(): String {
        return ""
    }

    override val canICMPing get() = false
    override val canTCPing get() = false
    override val canMapping get() = false
}

package fr.husi.database

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.Serializable

class SubscriptionBean : Serializable() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<SubscriptionBean>() {
            override fun newInstance(): SubscriptionBean {
                return SubscriptionBean()
            }

            override fun newArray(size: Int): Array<SubscriptionBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var type: Int = 0
    var link: String = ""
    var token: String = ""
    var forceResolve: Boolean = false
    var deduplication: Boolean = false
    var filterNotRegex: String = ""
    var updateWhenConnectedOnly: Boolean = false
    var customUserAgent: String = ""
    var autoUpdate: Boolean = false
    var autoUpdateDelay: Int = 1440
    var lastUpdated: Int = 0
    var customSni: String = ""
    var removeNonTlsXtls: Boolean = false

    // SIP008
    var bytesUsed: Long = 0L
    var bytesRemaining: Long = 0L // Also for OOC

    // Open Online Config
    var username: String = ""
    var expiryDate: Long = 0L

    override fun serializeToBuffer(output: ByteBufferOutput) {
        output.writeInt(5)

        output.writeInt(type)
        output.writeString(link)

        output.writeBoolean(forceResolve)
        output.writeBoolean(deduplication)
        output.writeBoolean(updateWhenConnectedOnly)
        output.writeString(customUserAgent)
        output.writeBoolean(autoUpdate)
        output.writeInt(autoUpdateDelay)
        output.writeInt(lastUpdated)
        output.writeLong(expiryDate)
        output.writeLong(bytesUsed)
        output.writeLong(bytesRemaining)
        output.writeString(token)
        output.writeString(filterNotRegex)
        output.writeString(customSni)
        output.writeBoolean(removeNonTlsXtls)
    }

    fun serializeForShare(output: ByteBufferOutput) {
        output.writeInt(2)

        output.writeInt(type)
        output.writeString(link)

        output.writeBoolean(forceResolve)
        output.writeBoolean(deduplication)
        output.writeBoolean(updateWhenConnectedOnly)
        output.writeString(customUserAgent)
        output.writeString(token)
        output.writeString(customSni)
        output.writeBoolean(removeNonTlsXtls)
    }

    override fun deserializeFromBuffer(input: ByteBufferInput) {
        val version = input.readInt()

        type = input.readInt()
        link = input.readString()
        forceResolve = input.readBoolean()
        deduplication = input.readBoolean()
        updateWhenConnectedOnly = input.readBoolean()
        customUserAgent = input.readString()
        autoUpdate = input.readBoolean()
        autoUpdateDelay = input.readInt()
        lastUpdated = input.readInt()

        if (version >= 2) {
            expiryDate = input.readLong()
            bytesUsed = input.readLong()
            bytesRemaining = input.readLong()
        }

        if (version >= 3) {
            token = input.readString()
        }

        if (version >= 4) {
            filterNotRegex = input.readString()
        }

        if (version >= 5) {
            customSni = input.readString()
            removeNonTlsXtls = input.readBoolean()
        }
    }

    fun deserializeFromShare(input: ByteBufferInput) {
        val version = input.readInt()

        type = input.readInt()
        link = input.readString()
        forceResolve = input.readBoolean()
        deduplication = input.readBoolean()
        updateWhenConnectedOnly = input.readBoolean()
        customUserAgent = input.readString()

        if (version >= 1) {
            token = input.readString()
        }

        if (version >= 2) {
            customSni = input.readString()
            removeNonTlsXtls = input.readBoolean()
        }
    }

}

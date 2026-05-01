package tr.theyusa.v4war.bg

import java.net.Socket
import android.net.Network

actual object NetworkSocketFactory {
    actual fun createSocket(): Socket? {
        val network = DefaultNetworkMonitor.defaultNetwork
        return network?.socketFactory?.createSocket() ?: Socket()
    }
}
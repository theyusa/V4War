package tr.theyusa.v4war.ui.dashboard

import androidx.compose.runtime.Stable
import tr.theyusa.v4war.ktx.emptyAsNull
import tr.theyusa.v4war.ktx.toList
import tr.theyusa.v4war.libcore.TrackerInfo

@Stable
data class ConnectionDetailState(
    val uuid: String = "",
    val inbound: String = "",
    val ipVersion: Short? = null,
    val network: String = "",
    val uploadTotal: Long = 0L,
    val downloadTotal: Long = 0L,
    val startedAt: String = "",
    val closedAt: String = "",
    val src: String = "",
    val dst: String = "",
    val host: String = "",
    val matchedRule: String = "",
    val outbound: String = "",
    val chain: String = "",
    val protocol: String? = null,
    val processes: List<String>? = null,
    val uid: Int = -1,
) {
    val isClosed: Boolean
        get() = closedAt.isNotEmpty()
}

fun TrackerInfo.toDetailState(): ConnectionDetailState {
    return ConnectionDetailState(
        uuid = uuid,
        inbound = inbound,
        ipVersion = ipVersion.takeIf { it > 0 },
        network = network,
        uploadTotal = uploadTotal,
        downloadTotal = downloadTotal,
        startedAt = startedAt,
        closedAt = closedAt,
        src = src,
        dst = dst,
        host = host,
        matchedRule = matchedRule,
        outbound = outbound,
        chain = chain,
        protocol = protocol.emptyAsNull(),
        processes = processes?.toList()?.takeIf { it.isNotEmpty() },
        uid = uid,
    )
}

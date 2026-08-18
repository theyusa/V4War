package tr.theyusa.v4war.bg.proto

import tr.theyusa.v4war.libcore.ConnectionEvent
import tr.theyusa.v4war.libcore.Libcore
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndUpdate

@OptIn(ExperimentalAtomicApi::class)
class OutboundTrafficAggregator {

    private class OutboundCounter(
        val upload: AtomicLong = AtomicLong(0L),
        val download: AtomicLong = AtomicLong(0L),
    )

    private val idToTag = ConcurrentHashMap<String, String>()
    private val tagToCounter = ConcurrentHashMap<String, OutboundCounter>()
    /** Last seen cumulative up/down per connection id; survives stream resets. */
    private val idTotals = ConcurrentHashMap<String, Pair<Long, Long>>()

    fun onEvent(event: ConnectionEvent) {
        when (event.type) {
            Libcore.ConnectionEventNew -> {
                val connection = event.trackerInfo ?: return
                val tag = connection.outbound.ifEmpty { return }
                idToTag[event.id] = tag
                val watermark = idTotals[event.id] ?: (0L to 0L)
                val upCredit = (connection.uploadTotal - watermark.first).coerceAtLeast(0L)
                val downCredit = (connection.downloadTotal - watermark.second).coerceAtLeast(0L)
                idTotals[event.id] = connection.uploadTotal to connection.downloadTotal
                if (upCredit == 0L && downCredit == 0L) return
                tagToCounter.computeIfAbsent(tag) { OutboundCounter() }.apply {
                    upload.addAndFetch(upCredit)
                    download.addAndFetch(downCredit)
                }
            }

            Libcore.ConnectionEventUpdate -> {
                val tag = idToTag[event.id] ?: return
                tagToCounter.computeIfAbsent(tag) { OutboundCounter() }.let { counter ->
                    counter.upload.addAndFetch(event.uplinkDelta)
                    counter.download.addAndFetch(event.downlinkDelta)
                }
                idTotals.compute(event.id) { _, prev ->
                    val (up, down) = prev ?: (0L to 0L)
                    (up + event.uplinkDelta) to (down + event.downlinkDelta)
                }
            }

            Libcore.ConnectionEventClosed -> {
                idToTag.remove(event.id)
                idTotals.remove(event.id)
            }
        }
    }

    fun drain(tag: String, isUpload: Boolean): Long {
        val counter = tagToTag(tag) ?: return 0L
        return if (isUpload) {
            counter.upload.fetchAndUpdate { 0L }
        } else {
            counter.download.fetchAndUpdate { 0L }
        }
    }

    private fun tagToTag(tag: String): OutboundCounter? {
        return tagToCounter[tag]
    }
}

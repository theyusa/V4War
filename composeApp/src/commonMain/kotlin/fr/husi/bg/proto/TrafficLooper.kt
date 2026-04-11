package fr.husi.bg.proto

import fr.husi.Key
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.bg.SpeedStats
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.fmt.ConfigBuildResult
import fr.husi.fmt.TAG_DIRECT
import fr.husi.ktx.Logs
import fr.husi.libcore.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrafficLooper(
    private val box: Service,
    private val config: ConfigBuildResult,
    private val scope: CoroutineScope,
    private val onSpeedUpdate: (suspend (SpeedStats) -> Unit)? = null,
) {

    private var job: Job? = null
    private val idMap = mutableMapOf<Long, TrafficUpdater.TrafficLooperData>() // id to 1 data
    private val tagMap = mutableMapOf<String, TrafficUpdater.TrafficLooperData>() // tag to 1 data

    suspend fun stop() {
        job?.cancel()
        if (!DataStore.profileTrafficStatistics) return
        updateDb()
        Logs.d("finally traffic post done")
    }

    fun start() {
        job = scope.launch { loop() }
    }

    fun updateSelectedTag(groupName: String, old: String, new: String) {
        val group = config.trafficMap[groupName] ?: return
        val oldID = config.tagToID[old]
        val newID = config.tagToID[new]
        for (entity in group) {
            when (entity.id) {
                oldID -> {
                    idMap[oldID]?.ignore = true
                }

                newID -> {
                    idMap[newID]?.ignore = false
                }
            }
        }
    }

    private suspend fun loop() {
        val speedInterval = DataStore.configurationStore
            .intFlow(Key.SPEED_INTERVAL, 1000)
            .map { it.toLong() }
            .stateIn(scope, SharingStarted.Eagerly, 1000L)
        val showDirectSpeed = DataStore.configurationStore
            .booleanFlow(Key.SHOW_DIRECT_SPEED, true)
            .stateIn(scope, SharingStarted.Eagerly, true)
        val profileTrafficStatistics = DataStore.configurationStore
            .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
            .stateIn(scope, SharingStarted.Eagerly, true)
        // update database / 10s
        val persistEveryMs = 10_000L

        // Calculate loop times (ticks) based on delay ms.
        fun persistTicksForDelay(delay: Long): Long {
            val effectiveDelay = delay.coerceAtLeast(1L)
            return ((persistEveryMs + effectiveDelay - 1) / effectiveDelay).coerceAtLeast(1L)
        }

        var delayMs = speedInterval.value
        var persistTicks = if (delayMs > 0) persistTicksForDelay(delayMs) else 1L
        var ticks = 0L

        // for display
        val itemBypass = TrafficUpdater.TrafficLooperData(tag = TAG_DIRECT)

        // initialize
        idMap.clear()
        idMap[-1] = itemBypass
        val mainID = config.tagToID[config.mainTag]
        config.trafficMap.forEach { (tag, entities) ->
            val isProxySet = entities.any { it.type == ProxyEntity.TYPE_PROXY_SET }
            for (ent in entities) {
                val item = TrafficUpdater.TrafficLooperData(
                    tag = tag,
                    rx = ent.rx,
                    tx = ent.tx,
                    rxBase = ent.rx,
                    txBase = ent.tx,
                    ignore = isProxySet && ent.id != mainID,
                )
                idMap[ent.id] = item
                tagMap[tag] = item
                Logs.d("traffic count $tag to ${ent.id}")
            }
        }
        val trafficUpdater = TrafficUpdater(
            box = box, items = idMap.values.toList(),
        )
        box.initializeProxySet()

        while (scope.isActive) {
            var currentDelayMs = speedInterval.value
            if (currentDelayMs <= 0L) {
                delayMs = 0L
                ticks = 0
                // Wait until valid value
                currentDelayMs = speedInterval.filter { it > 0L }.first()
            }
            if (currentDelayMs != delayMs) {
                delayMs = currentDelayMs
                persistTicks = persistTicksForDelay(delayMs)
                ticks = 0
            }

            trafficUpdater.updateAll()
            if (!scope.isActive) return

            // add all non-bypass to "main"
            var mainTxRate = 0L
            var mainRxRate = 0L
            var mainTx = 0L
            var mainRx = 0L
            tagMap.forEach { (_, it) ->
                if (!it.ignore) {
                    mainTxRate += it.txRate
                    mainRxRate += it.rxRate
                }
                mainTx += it.tx - it.txBase
                mainRx += it.rx - it.rxBase
            }

            // speed
            val speedStats = SpeedStats(
                txRateProxy = mainTxRate,
                rxRateProxy = mainRxRate,
                txRateDirect = if (showDirectSpeed.value) itemBypass.txRate else 0L,
                rxRateDirect = if (showDirectSpeed.value) itemBypass.rxRate else 0L,
                txTotal = mainTx,
                rxTotal = mainRx,
            )

            // Update shared speed state
            if (DataStore.serviceState == ServiceState.Connected) {
                BackendState.updateSpeed(speedStats)
                onSpeedUpdate?.invoke(speedStats)
            }

            if (profileTrafficStatistics.value) {
                if (++ticks >= persistTicks) {
                    updateDb()
                    ticks = 0
                }
            } else {
                ticks = 0
            }

            delay(delayMs)
        }
    }

    private suspend fun updateDb() {
        config.trafficMap.forEach { (_, entities) ->
            for (entity in entities) {
                val item = idMap[entity.id] ?: return@forEach
                ProfileManager.updateTraffic(entity, item.tx, item.rx)
            }
        }
    }
}

package tr.theyusa.v4war.bg.proto

import fr.v4war.BuildConfig
import tr.theyusa.v4war.aidl.SpeedDisplayData
import tr.theyusa.v4war.bg.BaseService
import tr.theyusa.v4war.bg.SpeedStats
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.ProxyEntity
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var displayProfileName = profile.displayNameForService()

    var trafficLooper: TrafficLooper? = null
    private val looperScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun buildConfig() {
        super.buildConfig()
        Logs.d(config.config)
        if (DataStore.isExpert) Logs.d("trafficMap: " + config.trafficMap.toString())
    }

    override suspend fun init(isVPN: Boolean) {
        super.init(isVPN)
        pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override fun launch() {
        super.launch() // start box
        looperScope.launch {
            val data = service?.data ?: return@launch
            trafficLooper = TrafficLooper(
                box = resolveRepository().boxService!!,
                config = config,
                scope = looperScope,
                onSpeedUpdate = { stats ->
                    val speed = stats.toSpeedDisplayData()
                    data.binder.notifySpeed(speed)
                    data.notification.apply {
                        if (canPostSpeed()) onSpeed(speed)
                    }
                },
            )
            trafficLooper?.start()
        }
    }

    override fun close() {
        super.close()
        runBlocking {
            trafficLooper?.stop()
            trafficLooper = null
        }
        looperScope.cancel()
    }
}

private fun SpeedStats.toSpeedDisplayData() = SpeedDisplayData(
    txRateProxy = txRateProxy,
    rxRateProxy = rxRateProxy,
    txRateDirect = txRateDirect,
    rxRateDirect = rxRateDirect,
    txTotal = txTotal,
    rxTotal = rxTotal,
)

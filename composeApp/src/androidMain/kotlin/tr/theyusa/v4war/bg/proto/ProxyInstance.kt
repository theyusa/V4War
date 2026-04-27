package tr.theyusa.v4war.bg.proto

import fr.v4war.BuildConfig
import tr.theyusa.v4war.aidl.SpeedDisplayData
import tr.theyusa.v4war.bg.BaseService
import tr.theyusa.v4war.bg.SpeedStats
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.ProxyEntity
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.ktx.runOnDefaultDispatcher
import tr.theyusa.v4war.repository.resolveRepository
import kotlinx.coroutines.runBlocking

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var displayProfileName = profile.displayNameForService()

    var trafficLooper: TrafficLooper? = null

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
        runOnDefaultDispatcher {
            val data = service?.data ?: return@runOnDefaultDispatcher
            trafficLooper = TrafficLooper(
                box = resolveRepository().boxService!!,
                config = config,
                scope = this,
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

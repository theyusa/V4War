package tr.theyusa.v4war.bg.proto

import tr.theyusa.v4war.bg.AbstractInstance
import tr.theyusa.v4war.bg.GuardedProcessPool
import tr.theyusa.v4war.bg.initPlugins
import tr.theyusa.v4war.bg.launchPlugins
import tr.theyusa.v4war.database.ProxyEntity
import tr.theyusa.v4war.fmt.ConfigBuildResult
import tr.theyusa.v4war.fmt.buildConfig
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.ktx.readableMessage
import tr.theyusa.v4war.ktx.runOnDefaultDispatcher
import tr.theyusa.v4war.repository.resolveRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import java.io.File
import kotlin.system.exitProcess

abstract class BoxInstance(
    val profile: ProxyEntity,
) : AbstractInstance {

    lateinit var config: ConfigBuildResult

    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    private val externalInstances = hashMapOf<Int, AbstractInstance>()
    open lateinit var processes: GuardedProcessPool
    private var cacheFiles = ArrayList<File>()
    fun isInitialized(): Boolean {
        return ::config.isInitialized && resolveRepository().boxService?.hasInstance() == true
    }

    protected open fun buildConfig() {
        config = buildConfig(profile)
    }

    protected open suspend fun loadConfig() {
        resolveRepository().boxService!!.newInstance(config.config)
    }

    open suspend fun init(isVPN: Boolean) {
        buildConfig()
        pluginConfigs.putAll(initPlugins(config, isVPN, cacheFiles))
        loadConfig()
    }

    override fun launch() {
        for ((chain) in config.externalIndex) {
            chain.entries.forEach { (port, _) ->
                if (externalInstances.containsKey(port)) {
                    externalInstances[port]!!.launch()
                }
            }
        }
        launchPlugins(config, pluginConfigs, processes, cacheFiles)
        resolveRepository().boxService!!.startInstance()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun close() {
        for (instance in externalInstances.values) {
            runCatching {
                instance.close()
            }
        }

        cacheFiles.removeAll { it.delete(); true }

        if (::processes.isInitialized) processes.close(GlobalScope + Dispatchers.IO)

        if (resolveRepository().boxService?.hasInstance() == true) {
            try {
                resolveRepository().boxService!!.stopInstance()
            } catch (e: Exception) {
                Logs.w(e)
                // Kill the process if it is not closed properly to clean exist inbound listeners.
                // Do not kill in main process, whose test not starts any listener.
                if (!resolveRepository().isMainProcess && e.readableMessage.contains("sing-box did not close in time")) runOnDefaultDispatcher {
                    delay(500) // Wait for error handling
                    exitProcess(0)
                }
            }
        }
    }

}

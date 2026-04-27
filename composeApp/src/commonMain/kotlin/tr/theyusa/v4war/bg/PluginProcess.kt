package tr.theyusa.v4war.bg

import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.fmt.ConfigBuildResult
import tr.theyusa.v4war.fmt.hysteria.HysteriaBean
import tr.theyusa.v4war.fmt.hysteria.buildHysteriaConfig
import tr.theyusa.v4war.platform.PlatformInfo
import tr.theyusa.v4war.plugin.PluginManager
import tr.theyusa.v4war.repository.resolveRepository
import java.io.File

fun initPlugins(
    config: ConfigBuildResult,
    isVPN: Boolean,
    cacheFiles: MutableList<File>,
): Map<Int, Pair<Int, String>> {
    val repository = resolveRepository()
    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    for ((chain) in config.externalIndex) {
        chain.entries.forEach { (port, profile) ->
            when (val bean = profile.requireBean()) {
                is HysteriaBean -> {
                    PluginManager.init(
                        when (bean.protocolVersion) {
                            HysteriaBean.PROTOCOL_VERSION_1 -> "hysteria-plugin"
                            HysteriaBean.PROTOCOL_VERSION_2 -> "hysteria2-plugin"
                            else -> "hysteria2-plugin"
                        }
                    )
                    pluginConfigs[port] =
                        profile.type to bean.buildHysteriaConfig(port, isVPN) { type ->
                            File(repository.cacheDir, "hysteria_${System.currentTimeMillis()}.$type").also {
                                it.parentFile?.mkdirs()
                                cacheFiles.add(it)
                            }
                        }
                }
                else -> {}
            }
        }
    }
    return pluginConfigs
}

fun launchPlugins(
    config: ConfigBuildResult,
    pluginConfigs: Map<Int, Pair<Int, String>>,
    processes: GuardedProcessPool,
    cacheFiles: MutableList<File>,
) {
    val cacheDir = File(resolveRepository().cacheDir, "tmpcfg")
    cacheDir.mkdirs()

    for ((chain) in config.externalIndex) {
        chain.entries.forEach { (port, profile) ->
            val bean = profile.requireBean()
            val (_, cfg) = pluginConfigs[port] ?: return@forEach

            when (bean) {
                is HysteriaBean -> {
                    val configFile = File(cacheDir, "hysteria_${System.currentTimeMillis()}.json")
                    configFile.writeText(cfg)
                    cacheFiles.add(configFile)
                    val commands = if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
                        mutableListOf(
                            PluginManager.init("hysteria-plugin")!!.path,
                            "client",
                            "--no-check",
                            "--config",
                            configFile.absolutePath,
                            "--log-level",
                            if (DataStore.logLevel > 0) "trace" else "warn",
                        )
                    } else {
                        mutableListOf(
                            PluginManager.init("hysteria2-plugin")!!.path,
                            "client",
                            "--config",
                            configFile.absolutePath,
                            "--log-level",
                            if (DataStore.logLevel > 0) "warn" else "error",
                        )
                    }
                    if (PlatformInfo.isAndroid &&
                        bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2 &&
                        bean.protocol == HysteriaBean.PROTOCOL_FAKETCP
                    ) {
                        commands.addAll(0, listOf("su", "-c"))
                    }
                    processes.start(
                        commands,
                        mutableMapOf("HYSTERIA_DISABLE_UPDATE_CHECK" to "1"),
                    )
                }
                else -> {}
            }
        }
    }
}

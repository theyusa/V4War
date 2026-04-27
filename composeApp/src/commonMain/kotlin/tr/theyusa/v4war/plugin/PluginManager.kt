package tr.theyusa.v4war.plugin

import tr.theyusa.v4war.ExpectedException
import java.io.FileNotFoundException

class PluginNotFoundException(val plugin: String) : FileNotFoundException(plugin),
    ExpectedException

data class PluginInitResult(
    val path: String,
)

expect object PluginManager {

    @Throws(Throwable::class)
    fun init(pluginId: String): PluginInitResult?
}

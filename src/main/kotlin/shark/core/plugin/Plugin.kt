package shark.core.plugin

import org.springframework.stereotype.Component
import shark.core.data.registry.ResourceLocation
import shark.core.event.Event
import java.io.File

annotation class SharkPlugin(val pluginName: String) {

    companion object {
        fun checkPluginName(pluginName: String) = ResourceLocation.isValidNamespace(pluginName)
    }

}

@Component
abstract class Plugin {

    internal var pluginLoadingEvent: PluginLoadingEvent? = null
        set(value) {
            if (field != null) throw UnsupportedOperationException()
            field = value
        }

    fun getPluginLoadingEvent() = pluginLoadingEvent!!
    abstract suspend fun initialize()

}

open class PluginLoadingEvent(private val plugin: Plugin, private val languageProvider: LanguageProvider, private val pluginInfo: SharkPluginInfo, private val pluginFile: File, private val pluginId: String) : Event() {

    open fun getPluginId() = pluginId
    open fun getLanguageProvider(): LanguageProvider = languageProvider
    open fun getPlugin(): Plugin = plugin
    open fun getPluginFile(): File = pluginFile
    open fun getPluginInfo(): SharkPluginInfo = pluginInfo

}

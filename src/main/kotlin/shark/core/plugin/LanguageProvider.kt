package shark.core.plugin

import shark.core.event.EventBus
import java.io.File

interface LanguageProvider {

    suspend fun isPluginSupported(plugin: File, eventBus: EventBus): Boolean
    suspend fun loadPlugin(file: File, eventBus: EventBus): Plugin?

    suspend fun loadData(plugin: Plugin, pluginFile: File) {}
    suspend fun loadResources(plugin: Plugin, pluginFile: File) {}

}
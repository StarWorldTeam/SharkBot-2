package shark.core.plugin

import shark.core.event.EventBus
import java.io.File

interface LanguageProvider {

    suspend fun isPluginSupported(plugin: File, eventBus: EventBus): Boolean
    suspend fun loadPlugin(file: File, eventBus: EventBus): Plugin?
    suspend fun initializePlugin(plugin: Plugin, pluginFile: File, eventBus: EventBus)
    suspend fun disposePlugin(plugin: Plugin, pluginFile: File)

    suspend fun loadData(plugin: Plugin, pluginFile: File) {}
    suspend fun loadAssets(plugin: Plugin, pluginFile: File) {}

}
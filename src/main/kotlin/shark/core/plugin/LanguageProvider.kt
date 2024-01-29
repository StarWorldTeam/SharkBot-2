package shark.core.plugin

import shark.core.event.EventBus
import java.io.File

interface LanguageProvider {

    fun isPluginSupported(plugin: File, eventBus: EventBus): Boolean
    fun loadPlugin(file: File, eventBus: EventBus): Plugin?

}
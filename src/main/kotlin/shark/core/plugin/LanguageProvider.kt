package shark.core.plugin

import java.io.File

interface LanguageProvider {

    fun isPluginSupported(plugin: File): Boolean
    fun loadPlugin(file: File): Plugin?

}
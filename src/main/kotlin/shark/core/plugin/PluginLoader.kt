package shark.core.plugin

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import shark.SharkBotEnvironment
import shark.core.resource.ResourceLoader
import java.io.File

@Service
class SharkLanguageProvider : LanguageProvider {

    override fun loadPlugin(file: File): Plugin {
        return object : Plugin {}
    }

    override fun isPluginSupported(plugin: File): Boolean {
        return plugin.isFile && plugin.endsWith(".jar")
    }

}

@Component
class PluginLoader : InitializingBean {

    @Autowired
    private lateinit var resourceLoader: ResourceLoader

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var provider: SharkLanguageProvider

    val pluginDirectory: File = SharkBotEnvironment.getSharkDirectory("plugins").toFile().also {
        it.mkdirs()
    }

    private val plugins: MutableMap<LanguageProvider, MutableList<Pair<File, Plugin>>> = mutableMapOf()
    fun getPlugins() = plugins

    override fun afterPropertiesSet() {
        resourceLoader.loadAssets()
        plugins.getOrPut(provider, ::mutableListOf).addAll(
            pluginDirectory.listFiles().map {
                it to provider.loadPlugin(it)
            }
        )
        for (file in pluginDirectory.listFiles()) {
            for (provider in context.getBeansOfType(LanguageProvider::class.java)) {
                if (provider == this.provider) continue
                if (provider.value.isPluginSupported(file)) {
                    val plugin = provider.value.loadPlugin(file)
                    if (plugin != null)
                        plugins.getOrPut(provider.value, ::mutableListOf).add(file to plugin)
                }
            }
        }
    }

}
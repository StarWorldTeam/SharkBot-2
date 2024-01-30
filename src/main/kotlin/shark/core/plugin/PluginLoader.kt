package shark.core.plugin

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import shark.SharkBotEnvironment
import shark.core.event.EventBus
import shark.core.resource.ResourceLoader
import java.io.File
import java.net.URLClassLoader
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class ClassLoaderBeanDefinitionScanner(private val classLoader: ClassLoader, private val beanFactory: DefaultListableBeanFactory) : ClassPathBeanDefinitionScanner(beanFactory) {

    companion object {
        fun generateBeanName(className: String) = "shark.plugin.bean.${className}"
    }

    init {
        resourceLoader = DefaultResourceLoader(classLoader)
    }

    override fun doScan(vararg basePackages: String?): Set<BeanDefinitionHolder> {
        val beanDefinitions: MutableSet<BeanDefinitionHolder> = LinkedHashSet()
        for (basePackage in basePackages) {
            val candidates = findCandidateComponents(basePackage!!)
            for (candidate in candidates) {
                val clazz = classLoader.loadClass(candidate.beanClassName)
                val beanName = generateBeanName(clazz.name)
                val beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(clazz).rawBeanDefinition
                beanFactory.registerBeanDefinition(beanName, beanDefinition)
            }
        }
        return beanDefinitions
    }

}

@Service
class SharkLanguageProvider : LanguageProvider {

    @Autowired
    private lateinit var beanFactory: DefaultListableBeanFactory

    fun scanBeans(loader: ClassLoader) {
        val scanner = ClassLoaderBeanDefinitionScanner(loader, beanFactory)
        scanner.scan(*loader.definedPackages.map { it.name }.toTypedArray())
    }

    override suspend fun loadPlugin(file: File, eventBus: EventBus): Plugin? {
        val classLoader = URLClassLoader(arrayOf(file.toURI().toURL()), ClassLoader.getSystemClassLoader())
        val stream = classLoader.getResourceAsStream("META-INF/plugin.yml") ?: return null
        val info = YAMLMapper().readValue<DefaultSharkPluginInfo>(stream)
        val mainClass = classLoader.loadClass(info.getMainClass())
        if (!mainClass.isAnnotationPresent(SharkPlugin::class.java)) return null
        val annotation = mainClass.getAnnotation(SharkPlugin::class.java)
        if (!SharkPlugin.checkPluginName(annotation.pluginName)) return null
        scanBeans(classLoader)
        val plugin = beanFactory.getBean<Plugin>(ClassLoaderBeanDefinitionScanner.generateBeanName(mainClass.name))
        val event = PluginLoadingEvent(plugin, this, info, file, annotation.pluginName)
        plugin.pluginLoadingEvent = event
        plugin.initialize()
        plugin.getPluginLoadingEvent()
        eventBus.emit(event)
        return plugin
    }

    @Autowired
    private lateinit var resourceLoader: ResourceLoader
    override suspend fun loadResources(plugin: Plugin, pluginFile: File) {
        resourceLoader.loadAssets(classLoader = plugin.javaClass.classLoader)
    }

    override suspend fun loadData(plugin: Plugin, pluginFile: File) {
        resourceLoader.loadData(classLoader = plugin.javaClass.classLoader)
    }

    override suspend fun isPluginSupported(plugin: File, eventBus: EventBus): Boolean {
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

    private val plugins: MutableMap<String, Pair<LanguageProvider, Plugin>> = mutableMapOf()
    fun getPlugins() = plugins

    private val eventBus = EventBus(this::class)
    fun getEventBus() = eventBus

    override fun afterPropertiesSet() = runBlocking {
        resourceLoader.loadAssets()
        plugins.putAll(
            pluginDirectory.listFiles()
                .map {
                    async { it.path to (provider to provider.loadPlugin(it, getEventBus())) }
                }
                .awaitAll()
                .filter { it.second.second != null }
                .map { it.first to (it.second.first to it.second.second!!) }
        )
        val asyncJobs = mutableListOf<Job>()
        for (file in pluginDirectory.listFiles()) {
            if (file.path in plugins) continue
            launch {
                for (provider in context.getBeansOfType(LanguageProvider::class.java)) {
                    if (provider == this@PluginLoader.provider) continue
                    if (provider.value.isPluginSupported(file, getEventBus())) {
                        val plugin = provider.value.loadPlugin(file, getEventBus())
                        if (plugin != null)
                            plugins[file.path] = provider.value to plugin
                    }
                }
            }.also(asyncJobs::add)
        }
        asyncJobs.joinAll()
    }

}

interface SharkPluginInfo {

    fun getName(): String
    fun getVersion(): String
    fun getDescription(): Array<String>
    fun getAuthors(): Array<String>
    fun getMainClass(): String

}

class DefaultSharkPluginInfo: SharkPluginInfo {

    private lateinit var name: String
    private lateinit var version: String
    private lateinit var description: Array<String>
    @JsonProperty("main") private lateinit var mainClass: String
    @JsonProperty("author") private lateinit var authors: Array<String>

    override fun getAuthors() = authors
    override fun getName() = name
    override fun getVersion() = version
    override fun getDescription() = description
    override fun getMainClass(): String = mainClass

}

@Service
class PluginLoadingContext {

    @Autowired
    private lateinit var loader: PluginLoader

    suspend fun waitPlugin(name: String): Plugin = suspendCoroutine { continuation ->
        loader.getEventBus().on<PluginLoadingEvent> {
            if (it.getPluginId() == name)
                continuation.resume(it.getPlugin())
        }
        for (plugin in loader.getPlugins()) {
            if (plugin.value.second.getPluginLoadingEvent().getPluginId() == name)
                continuation.resume(plugin.value.second)
        }
    }

    suspend fun waitPlugins(vararg name: String) = coroutineScope {
        return@coroutineScope name.map {
            async { waitPlugin(it) }
        }.awaitAll()
    }

}

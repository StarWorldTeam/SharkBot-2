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
import shark.core.event.Event
import shark.core.event.EventBus
import shark.core.resource.AssetsResourceLoader
import shark.core.resource.DataResourceLoader
import shark.core.resource.ResourceLoader
import java.io.File
import java.net.URLClassLoader
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class PluginClassLoaderBeanDefinitionScanner(private val classLoader: ClassLoader, private val beanFactory: DefaultListableBeanFactory) : ClassPathBeanDefinitionScanner(beanFactory) {

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
        val scanner = PluginClassLoaderBeanDefinitionScanner(loader, beanFactory)
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
        val plugin = beanFactory.getBean<Plugin>(PluginClassLoaderBeanDefinitionScanner.generateBeanName(mainClass.name))
        val event = PluginLoadingEvent(plugin, this, info, file, annotation.pluginName)
        plugin.pluginLoadingEvent = event
        eventBus.emit(event)
        return plugin
    }

    override suspend fun initializePlugin(plugin: Plugin, pluginFile: File, eventBus: EventBus) {
        plugin.initialize()
        plugin.getPluginLoadingEvent().setInitialized(true)
        eventBus.emit(PluginInitializedEvent(plugin))
    }

    @Autowired
    private lateinit var assetsLoader: AssetsResourceLoader
    override suspend fun loadAssets(plugin: Plugin, pluginFile: File) {
        assetsLoader.loadAssets(classLoader = plugin.javaClass.classLoader)
    }

    @Autowired
    private lateinit var dataLoader: DataResourceLoader
    override suspend fun loadData(plugin: Plugin, pluginFile: File) {
        dataLoader.loadData(classLoader = plugin.javaClass.classLoader)
    }

    override suspend fun isPluginSupported(plugin: File, eventBus: EventBus): Boolean {
        return plugin.isFile && plugin.name.endsWith(".jar")
    }

    override suspend fun disposePlugin(plugin: Plugin, pluginFile: File) = plugin.dispose()

}

enum class PluginLoaderState {
    IDLE, CONSTRUCTING, INITIALIZING, FINISHED;
}

@Component
class PluginLoader : InitializingBean {

    private var state = PluginLoaderState.IDLE
    fun getState() = state

    @Autowired
    private lateinit var pluginLoadingContext: PluginLoadingContext

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

    override fun afterPropertiesSet(): Unit = loadPlugins()

    fun loadPlugins() = runBlocking {
        state = PluginLoaderState.CONSTRUCTING
        pluginLoadingContext.loader = this@PluginLoader
        pluginDirectory.listFiles()!!
            .filter { provider.isPluginSupported(it, getEventBus()) }
            .map { file ->
                launch {
                    provider.loadPlugin(file, getEventBus())?.let {
                        file.path to (provider to it)
                    }?.let {
                        plugins[it.first] = it.second
                    }
                }
            }
            .joinAll()
        val asyncJobs = mutableListOf<Deferred<Unit>>()
        for (file in pluginDirectory.listFiles()!!) {
            if (file.path in plugins) continue
            asyncJobs.add(
                async {
                    for (provider in context.getBeansOfType(LanguageProvider::class.java)) {
                        if (provider == this@PluginLoader.provider) continue
                        if (provider.value.isPluginSupported(file, getEventBus())) {
                            val plugin = provider.value.loadPlugin(file, getEventBus())
                            if (plugin != null) {
                                plugins[file.path] = provider.value to plugin
                                break
                            }
                        }
                    }
                }
            )
        }
        asyncJobs.awaitAll()
        state = PluginLoaderState.INITIALIZING
        initialize()
        state = PluginLoaderState.FINISHED
    }

    suspend fun initialize(): Unit = runBlocking {
        plugins.map {
            async {
                it.value.first.initializePlugin(it.value.second, it.value.second.getPluginLoadingEvent().getPluginFile(), getEventBus())
            }
        }.awaitAll()
    }

    suspend fun dispose(): Unit = runBlocking {
        plugins.map {
            async {
                it.value.first.disposePlugin(
                    it.value.second,
                    it.value.second.getPluginLoadingEvent().getPluginFile()
                )
            }
        }.awaitAll()
    }

}

class PluginInitializedEvent(private val plugin: Plugin) : Event() {

    fun getPlugin() = plugin

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

    internal lateinit var loader: PluginLoader
    fun getPluginLoader() = loader

    suspend fun waitPlugin(name: String): Plugin = suspendCoroutine { continuation ->
        loader.getEventBus().on<PluginInitializedEvent> {
            if (it.getPlugin().getPluginLoadingEvent().getPluginId() == name)
                continuation.resume(it.getPlugin())
        }
        for (plugin in loader.getPlugins()) {
            if (!plugin.value.second.getPluginLoadingEvent().isInitialized()) continue
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

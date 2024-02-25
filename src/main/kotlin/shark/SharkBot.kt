package shark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootVersion
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.system.JavaVersion
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Service
import shark.core.event.Event
import shark.core.event.EventBus
import shark.core.plugin.PluginLoader
import shark.core.resource.*
import shark.network.SharkClient
import shark.util.ConfigType
import shark.util.SharkConfig
import java.io.PrintStream
import java.nio.file.Path
import kotlin.concurrent.thread

@SpringBootApplication(scanBasePackages = ["shark", "sharkbot"])
@EnableMongoRepositories(value = ["shark", "sharkbot"])
@EnableScheduling
class SharkBotApplication {

    @Autowired
    private lateinit var client: SharkClient
    fun getClient() = client

    @Autowired
    private lateinit var resourceLoader: ResourceLoader
    fun getResourceLoader() = resourceLoader

}

class SharkApplicationConfig {
    var defaultLocale: String = "zh_hans"
}

@Service
class SharkBotService {

    private val applicationConfig = SharkBot.applicationConfig
    fun getConfig() = applicationConfig


}

object SharkBotEnvironment {

    fun getSharkDirectory(vararg path: String): Path = Path.of(System.getProperty("user.dir")!!, "shark", *path)

    private val meta = YAMLMapper()
        .readValue<Map<Any?, Any?>>(SharkBot::class.java.getResourceAsStream("/META-INF/shark.yml")!!)
    fun getVersion() = meta["version"] as String

}

object SharkBot {

    val eventBus = EventBus(SharkBot::class)

    val applicationConfig = SharkConfig.useConfig<SharkApplicationConfig>("shark/application.yml", ConfigType.YAML)!!

    val applicationBuilder: SpringApplicationBuilder = SpringApplicationBuilder(SharkBotApplication::class.java)
        .banner(SharkBanner())

    internal var application: SpringApplication? = null
        set(value) {
            if (field != null) throw UnsupportedOperationException()
            field = value
        }
    fun getApplication() = application!!

    internal var context: ConfigurableApplicationContext? = null
        set(value) {
            if (field != null) throw UnsupportedOperationException()
            field = value
        }
    fun getContext() = context!!
}

class SharkBanner : Banner {

    companion object {
        const val sharkBot = "SharkBot"
        val banner = listOf(
            " ____    __                       __          ____            __      ",
            "/\\  _`\\ /\\ \\                     /\\ \\        /\\  _`\\         /\\ \\__   ",
            "\\ \\,\\L\\_\\ \\ \\___      __     _ __\\ \\ \\/'\\    \\ \\ \\L\\ \\    ___\\ \\ ,_\\  ",
            " \\/_\\__ \\\\ \\  _ `\\  /'__`\\  /\\`'__\\ \\ , <     \\ \\  _ <'  / __`\\ \\ \\/  ",
            "   /\\ \\L\\ \\ \\ \\ \\ \\/\\ \\L\\.\\_\\ \\ \\/ \\ \\ \\\\`\\    \\ \\ \\L\\ \\/\\ \\L\\ \\ \\ \\_ ",
            "   \\ `\\____\\ \\_\\ \\_\\ \\__/.\\_\\\\ \\_\\  \\ \\_\\ \\_\\   \\ \\____/\\ \\____/\\ \\__\\",
            "    \\/_____/\\/_/\\/_/\\/__/\\/_/ \\/_/   \\/_/\\/_/    \\/___/  \\/___/  \\/__/"
        )
    }

    override fun printBanner(environment: Environment, sourceClass: Class<*>, printStream: PrintStream) {
        banner.forEach {
            printStream.println(
                AnsiOutput.toString(
                    AnsiColor.BLUE, it
                )
            )
        }
        printStream.println(
            AnsiOutput.toString(
                AnsiColor.CYAN, "Shark", AnsiColor.DEFAULT, " ", AnsiStyle.FAINT, "(${SharkBotEnvironment.getVersion()})", " ",
                AnsiColor.CYAN, "SpringBoot", AnsiColor.DEFAULT, " ", AnsiStyle.FAINT, "(${SpringBootVersion.getVersion()})", " ",
                AnsiColor.CYAN, "Kotlin", AnsiColor.DEFAULT, " ", AnsiStyle.FAINT, "(${KotlinVersion.CURRENT})", " ",
                AnsiColor.CYAN, "Java", AnsiColor.DEFAULT, " ", AnsiStyle.FAINT, "(${JavaVersion.getJavaVersion()})"
            )
        )
    }

}

class SharkApplicationStartedEvent : Event()

suspend fun main(args: Array<String>) {
    System.setProperty("shark.application.arguments", ObjectMapper().writeValueAsString(args))
    SharkBot.application = SharkBot.applicationBuilder.build()
    SharkBot.context = SharkBot.getApplication().run(*args)
    SharkBot.eventBus.emit(SharkApplicationStartedEvent())
    run {
        val pluginLoader = SharkBot.getContext().getBean<PluginLoader>()
        val assetsLoader = SharkBot.getContext().getBean<AssetsResourceLoader>()
        val dataLoader = SharkBot.getContext().getBean<DataResourceLoader>()
        assetsLoader.loadAssets()
        for (plugin in pluginLoader.getPlugins())
            plugin.value.first.loadAssets(
                plugin.value.second,
                plugin.value.second.getPluginLoadingEvent().getPluginFile()
            )
        assetsLoader.getEventBus().emit(AllAssetsLoadedEvent(assetsLoader))
        dataLoader.loadData()
        for (plugin in pluginLoader.getPlugins())
            plugin.value.first.loadData(
                plugin.value.second,
                plugin.value.second.getPluginLoadingEvent().getPluginFile()
            )
        dataLoader.getEventBus().emit(AllDataLoadedEvent(dataLoader))
        SharkBot.eventBus.emit(AllResourcesLoadedEvent(assetsLoader, dataLoader))
    }
    SharkBot.getContext().getBean<SharkBotThreads>().start()
}

@Service
class SharkBotThreads {

    private var running: Boolean = false

    @Autowired private lateinit var client: SharkClient

    private val threadSharkClient by lazy {
        thread(name = "shark-client", start = false) { runBlocking { client.start() } }
    }
    private val threadBackend by lazy {
        thread(name = "shark-backend", start = false) {}
    }

    fun getSharkClientThread() = threadSharkClient
    fun getBackendThread() = threadBackend

    fun start() {
        getBackendThread().start()
        getSharkClientThread().start()
        running = true
    }

}

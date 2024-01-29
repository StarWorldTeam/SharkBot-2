package shark

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Autowired
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
import org.springframework.stereotype.Service
import shark.core.resource.Language
import shark.core.resource.ResourceLoader
import shark.core.resource.TypedResourcesLoadedEvent
import shark.network.SharkClient
import shark.util.ConfigType
import shark.util.ResourceUtil
import shark.util.SharkConfig
import java.io.PrintStream
import java.nio.file.Path

@SpringBootApplication(scanBasePackages = ["shark", "sharkbot"])
@EnableMongoRepositories(value = ["shark", "sharkbot"])
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

suspend fun main(args: Array<String>) {
    SharkBot.application = SharkBot.applicationBuilder.build()
    SharkBot.context = SharkBot.getApplication().run(*args)
    SharkBot.getContext().getBean(ResourceLoader::class.java).apply {
        getAssetsEventBus().on<TypedResourcesLoadedEvent> {
            if (it.getResourceType().path == "language") {
                for (language in it.getResources()) {
                    val languageName = language.getFileName(false)
                    Language.getLanguageOrPut(languageName).putAll(
                        ResourceUtil.resolveToMap(language)
                    )
                }
            }
        }
    }
    runCatching {
        SharkBot.getContext().getBean(SharkClient::class.java).start()
    }
}

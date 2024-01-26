package shark

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import shark.network.SharkClient
import java.nio.file.Path

@SpringBootApplication
class SharkBotApplication {

    @Autowired
    lateinit var client: SharkClient

}

object SharkBotEnvironment {

    fun getSharkDirectory(vararg path: String): Path = Path.of(System.getProperty("user.dir")!!, "shark", *path)

}

object SharkBot {
    val applicationBuilder: SpringApplicationBuilder = SpringApplicationBuilder(SharkBotApplication::class.java)
    val application: SpringApplication = applicationBuilder.build()
    var context: ConfigurableApplicationContext? = null
        set(value) {
            if (field != null) throw UnsupportedOperationException()
            field = value
        }
}

suspend fun main(args: Array<String>) {
    SharkBot.context = SharkBot.application.run(*args)
    SharkBot.context!!.getBean(SharkClient::class.java).start()
}

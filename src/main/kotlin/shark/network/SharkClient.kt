package shark.network

import com.neovisionaries.ws.client.WebSocketFactory
import dev.minn.jda.ktx.jdabuilder.light
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component
import shark.network.command.SharkCommandBeanPostProcessor
import shark.util.ConfigType
import shark.util.SharkConfig
import java.net.InetSocketAddress
import java.net.Proxy

class ProxyConfig {

    var enabled: Boolean = false
    var host: String = "localhost"
    var port: Int = 7890

}

class SharkClientConfig {
    var proxy: ProxyConfig = ProxyConfig()
    var token: String = ""
}

@Component
class SharkClient {

    @SharkConfig("shark/client.yml", ConfigType.YAML)
    private lateinit var clientConfig: SharkClientConfig
    fun getClientConfig() = clientConfig

    @Autowired
    private lateinit var commandBeanPostProcessor: SharkCommandBeanPostProcessor
    fun getCommandBeanPostProcessor() = commandBeanPostProcessor

    @Autowired
    private lateinit var context: ConfigurableApplicationContext

    private lateinit var client: JDA
    fun getClient() = client

    suspend fun start() {
        client = light(getClientConfig().token, enableCoroutines = true) {
            enableIntents(listOf(*GatewayIntent.values()))
            setHttpClientBuilder(
                OkHttpClient.Builder().also {
                    if (getClientConfig().proxy.enabled) it.proxy(Proxy(Proxy.Type.HTTP, getClientConfig().proxy.let { proxy -> InetSocketAddress(proxy.host, proxy.port) }))
                }
            )
            setWebsocketFactory(
                WebSocketFactory().also {
                    if (getClientConfig().proxy.enabled) it.proxySettings.setHost(getClientConfig().proxy.host).port = getClientConfig().proxy.port
                }
            )
            enableIntents(listOf(*GatewayIntent.values()))
            addEventListeners(context.getBean(SharkClientEventListener::class.java))
        }
        client.awaitReady()
    }

}

@Component
class SharkClientEventListener : ListenerAdapter() {

    @Autowired
    private lateinit var client: SharkClient

}

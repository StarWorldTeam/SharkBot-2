package shark.network

import com.neovisionaries.ws.client.WebSocketFactory
import dev.minn.jda.ktx.jdabuilder.light
import kodash.type.None
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import okhttp3.OkHttpClient
import okhttp3.internal.toImmutableList
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import shark.core.event.EventBus
import shark.network.command.AutoCompleteMode
import shark.network.command.MetaCommand
import shark.network.command.SharkCommandBeanPostProcessor
import shark.network.command.SharkCommandOption
import shark.network.interaction.CommandInteractionEvent
import shark.network.interaction.InteractionAutoCompleteEvent
import shark.util.ConfigType
import shark.util.SharkConfig
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.reflect.jvm.javaMethod

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

    private val eventBus = EventBus(this::class)
    fun getEventBus() = eventBus

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
        client.updateCommands().also(::updateCommands).queue()
        client.awaitReady()
    }

    fun updateCommands(action: CommandListUpdateAction) {
        val commands = mutableListOf<SlashCommandData>()
        for (metaCommand in commandBeanPostProcessor.getCommands()) {
            val command = metaCommand.getCommand()
            command.also(metaCommand.getSharkCommand()::setup)
            val function = metaCommand.getAction().getFunction().javaMethod!!
            function.parameters.forEach {
                if (it.isAnnotationPresent(SharkCommandOption::class.java)) {
                    val annotation = it.getAnnotation(SharkCommandOption::class.java)
                    if (annotation.register)
                        command.addOption(
                            annotation.type,
                            SharkCommandOption.getName(it),
                            annotation.description,
                            annotation.required,
                            annotation.autoComplete.isEnabledFor(MethodParameter.forParameter(it))
                        )
                }
            }
            commands.add(command)
        }
        action.addCommands(commands)
    }

}

@Component
class SharkClientEventListener : ListenerAdapter() {

    @Autowired
    private lateinit var client: SharkClient

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent): Unit = runBlocking {
        val result = MetaCommand[event.name].getAction().run(event)
        client.getEventBus().emit(CommandInteractionEvent(event, result))
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        val focusedValue = event.focusedOption.value
        val metaCommand = MetaCommand[event.name]
        val function = metaCommand.getAction().getFunction().javaMethod!!
        val firstOrNull = function.parameters.firstOrNull {
            it.isAnnotationPresent(SharkCommandOption::class.java) && it.getAnnotation(SharkCommandOption::class.java).autoComplete == AutoCompleteMode.DEFAULT
        }?.let { parameter ->
            val annotation = parameter.getAnnotation(SharkCommandOption::class.java)
            if (annotation.autoComplete.isEnabledFor(MethodParameter.forParameter(parameter))) {
                event.replyChoiceStrings(
                    *(parameter.type.enumConstants as Array<Enum<*>>).map { it.name }.filter { it.lowercase().trim().contains(focusedValue.trim().lowercase()) }.take(25).toTypedArray()
                ).queue()
            }
            None
        }
        if (firstOrNull != null) return
        val autoCompleteEvent = InteractionAutoCompleteEvent(event)
        client.getEventBus().emit(autoCompleteEvent)
        val choices = autoCompleteEvent.getChoices().toImmutableList()
        val displayChoices = mutableListOf<Choice>()
        for (iterable in choices) {
            for (choice in iterable) {
                if (displayChoices.size >= 25) return
                if (choice.first) displayChoices.add(choice.second)
            }
        }
        for (iterable in choices) {
            for (choice in iterable) {
                if (displayChoices.size >= 25) return
                if (choice.first) continue
                if (choice.second.name.trim().contains(focusedValue.trim(), true) || choice.second.asString.trim().contains(focusedValue.trim(), true)) {
                    displayChoices.add(choice.second)
                }
            }
        }
        if (displayChoices.isNotEmpty())
            event.replyChoices(displayChoices.take(25)).queue()
    }

}

package shark.network.command

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.CaseFormat
import com.google.common.collect.HashBiMap
import kodash.coroutine.Promise
import kotlinx.coroutines.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.InteractionType
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.beans.factory.getBean
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import shark.core.data.registry.ResourceLocation
import shark.core.entity.IUser
import shark.core.resource.toSharkLanguage
import shark.network.SharkClient
import shark.util.ConstantUtil
import shark.util.SharkConfig
import java.lang.annotation.*
import java.lang.reflect.Parameter
import java.time.OffsetDateTime
import kotlin.reflect.*
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod

enum class AutoCompleteMode {
    DEFAULT, ENABLED, DISABLED;

    fun isEnabledFor(parameter: MethodParameter): Boolean {
        if (!parameter.parameter.isAnnotationPresent(Command.Option::class.java)) {
            return when (this) {
                DEFAULT -> false
                ENABLED -> true
                DISABLED -> false
            }
        } else {
            val annotation = parameter.parameter.getAnnotation(Command.Option::class.java)!!
            if (annotation.type == OptionType.STRING && parameter.parameterType.isEnum && annotation.autoComplete == DEFAULT)
                return true
            if (this == ENABLED) return true
            return false
        }
    }

}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Controller
annotation class SharkCommand(val id: String, val description: String = ConstantUtil.emptyCommandDescription)

class MetaCommandAction(private val function: KFunction<*>, private val command: MetaCommand) {

    fun getFunction() = function
    fun getCommand() = command
    fun getSharkCommand() = getCommand().getSharkCommand()

    suspend fun run(commandEvent: CommandInteractionEvent): Any? {
        val args = arrayListOf<Any?>()
        args.add(0, command.getSharkCommand())
        val javaMethod = function.javaMethod!!
        val client = getCommand().getSharkCommand().getContext().getBean(SharkClient::class.java)
        function.javaMethod!!.parameters.indices.forEach {
            val parameter = javaMethod.parameters[it]
            val methodParameter = MethodParameter.forParameter(parameter)
            val resolved = SharkCommandActionResolver.resolveArgument(methodParameter, commandEvent, this)
            val commandArgumentResolvingEvent = CommandArgumentResolvingEvent(methodParameter, commandEvent, this, resolved)
            client.getEventBus().emit(commandArgumentResolvingEvent)
            args.add(it + 1, commandArgumentResolvingEvent.getValue())
        }
        val commandResultResolvingEvent = CommandResultResolvingEvent(
            this,
            commandEvent,
            SharkCommandActionResolver.resolveResult(function.call(*args.toTypedArray()), commandEvent)
        )
        client.getEventBus().emit(commandResultResolvingEvent)
        return commandResultResolvingEvent.getValue()
    }

}

class MetaCommand private constructor(private val sharkCommand: Command) {

    init {
        sharkCommand::class.memberFunctions.forEach {
            if (!it.javaMethod!!.isAnnotationPresent(Command.Action::class.java)) return@forEach
            require(action == null) { "Duplicate trigger: ${it.name}" }
            this.action = MetaCommandAction(it, this)
        }
        validate()
    }

    fun validate() {
        assert(action != null) { "Command must have a trigger" }
    }

    private val command: SlashCommandData = Commands
        .slash(getResourceLocation().format { left, right -> "$left-$right" }, getAnnotation().description)

    fun getCommand() = command
    fun getSharkCommand() = sharkCommand

    companion object {

        val pool = HashBiMap.create<MetaCommand, Command>()

        operator fun get(command: Command) = pool.inverse().getOrPut(command) {
            MetaCommand(command).let {
                validate(it.getResourceLocation()) { it }
            }
        }

        operator fun get(command: MetaCommand) =
            pool.getOrPut(command) { validate(command.getResourceLocation()) { command.getSharkCommand() } }

        operator fun get(location: ResourceLocation) = pool.keys.find { it.getResourceLocation() == location }!!
        operator fun get(name: String) = pool.keys.find { it.getCommand().name == name }!!
        operator fun get(data: SlashCommandData) = pool.values.find { it.getMetaCommand().getCommand() == data }

        private fun <T> validate(location: ResourceLocation, block: () -> T): T {
            for (i in pool.keys) if (i.getResourceLocation() == location) throw UnsupportedOperationException()
            return block()
        }

    }

    fun getAnnotation(): SharkCommand =
        getSharkCommand().javaClass.getAnnotation(SharkCommand::class.java)

    fun getResourceLocation(): ResourceLocation = ResourceLocation.of(getAnnotation().id)

    private var action: MetaCommandAction? = null
    fun getAction() = action!!

}

@Component
abstract class Command {

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Option(
        val name: String = "",
        val description: String = ConstantUtil.emptyCommandOptionDescription,
        val type: OptionType = OptionType.STRING,
        val autoComplete: AutoCompleteMode = AutoCompleteMode.DEFAULT,
        val required: Boolean = true,
        val register: Boolean = true
    ) {
        companion object {

            fun getName(parameter: Parameter): String = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getInternalName(parameter))

            fun getInternalName(parameter: Parameter): String {
                if (!parameter.isAnnotationPresent(Option::class.java)) return parameter.name
                val annotation = parameter.getAnnotation(Option::class.java)
                if (annotation.name.trim() == "") return parameter.name
                return annotation.name
            }
        }
    }
    
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Action
    
    @Autowired
    private lateinit var context: ConfigurableApplicationContext
    final fun getContext() = context

    fun setup(event: CommandSetupEvent) {}

    final fun getMetaCommand() = MetaCommand[this]

    override fun toString() = "${getMetaCommand().getResourceLocation()}"

    final fun getTranslation(suffix: String) = "command.${getMetaCommand().getResourceLocation().toLanguageKey()}.$suffix"

}

@Component
class SharkCommandBeanPostProcessor : BeanPostProcessor {

    private val commands = mutableListOf<MetaCommand>()

    fun getCommands() = commands.toList()

    override fun postProcessBeforeInitialization(bean: Any, beanName: String) = bean.also {
        if (bean !is Command) return@also
        if (!bean.javaClass.isAnnotationPresent(SharkCommand::class.java)) return@also
        commands.add(bean.getMetaCommand())
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
        return bean
    }

}

object SharkCommandActionResolver {

    suspend fun resolveResult(result: Any?, commandEvent: CommandInteractionEvent): Any? {
        val event = commandEvent.getInteractionEvent()
        return when (result) {
            is String -> resolveResult(Fragment.literal(result), commandEvent)
            is Promise<*> -> resolveResult(result.await(), commandEvent)
            is Deferred<*> -> resolveResult(result.await(), commandEvent)
            is Job -> result.also { it.join() }
            is Fragment -> result.also { event.reply(it.asMessage(commandEvent.getSession().getSharkUser()!!.getUserLocaleOrDefault(event.userLocale.toSharkLanguage()))).queue() }
            is MessageFragment -> result.also { it.reply(event, commandEvent.getSession().getSharkUser()!!.getUserLocaleOrDefault(event.userLocale.toSharkLanguage())) }
            is MessageCreateData -> result.also { event.reply(it).queue() }
            is MessageCreateBuilder -> resolveResult(result.build(), commandEvent)
            else -> result
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun resolveOption(
        optionAnnotation: Command.Option,
        parameter: MethodParameter,
        commandEvent: CommandInteractionEvent,
        action: MetaCommandAction
    ): Any? {
        val event = commandEvent.getInteractionEvent()
        val option = event.getOption(Command.Option.getName(parameter.parameter))
        if (option != null) {
            val type = parameter.parameterType
            if (type == OptionMapping::class.java) return option
            if (type == String::class.java || type == java.lang.String::class.java) return option.asString
            if (type == Long::class.java || type == java.lang.Long::class.java) return option.asLong
            if (type == Int::class.java || type == java.lang.Integer::class.java) return option.asInt
            if (type == Short::class.java || type == java.lang.Short::class.java) return option.asLong.toShort()
            if (type == Byte::class.java || type == java.lang.Byte::class.java) return option.asInt.toByte()
            if (type == Float::class.java || type == java.lang.Float::class.java) return option.asDouble.toFloat()
            if (type == Boolean::class.java || type == java.lang.Boolean::class.java) return option.asBoolean
            if (type == Double::class.java || type == java.lang.Double::class.java) return option.asDouble
            if (type == Char::class.java || type == java.lang.Character::class.java) return option.asInt.toChar()
            if (type == Attachment::class.java) return option.asAttachment
            if (type == Member::class.java) return option.asMember
            if (type == User::class.java) return option.asUser
            if (type == IMentionable::class.java) return option.asMentionable
            if (type == Role::class.java) return option.asRole
            if (type == Channel::class.java) return option.asChannel
            if (type == ResourceLocation::class.java) return ResourceLocation.of(option.asString)
            if (type == Fragment::class.java) return Fragment.literal(option.asString)
            if (type == JsonNode::class.java) return ObjectMapper().readTree(option.asString)
            if (type == IUser::class.java) return shark.core.entity.User.of(option.asUser, action.getCommand().getSharkCommand().getContext().getBean<SharkClient>())
            if (type == shark.core.entity.User::class.java) return shark.core.entity.User.of(option.asUser, action.getCommand().getSharkCommand().getContext().getBean<SharkClient>())
            if (type.kotlin.isSubclassOf(Enum::class)) {
                return if (optionAnnotation.type == OptionType.STRING) (type.enumConstants as Array<Enum<*>>).firstOrNull { it.name == option.asString }
                else type.enumConstants.getOrNull(option.asInt)
            }
        }
        return null
    }

    suspend fun resolveArgument(
        parameter: MethodParameter,
        commandEvent: CommandInteractionEvent,
        action: MetaCommandAction
    ): Any? {
        val event = commandEvent.getInteractionEvent()
        val client = action.getCommand().getSharkCommand().getContext().getBean(SharkClient::class.java)
        if (parameter.parameter.isAnnotationPresent(Command.Option::class.java)) {
            val option = resolveOption(parameter.parameter.getAnnotation(Command.Option::class.java), parameter, commandEvent, action)
            val resolvingEvent = CommandOptionResolvingEvent(parameter, commandEvent, action, option)
            client.getEventBus().emit(resolvingEvent)
            return resolvingEvent.getValue()
        }
        if (parameter.parameter.isAnnotationPresent(SharkConfig::class.java)) {
            return parameter.parameter.getAnnotation(SharkConfig::class.java)
                .let { annotation ->
                    SharkConfig.useConfig(parameter.parameterType, annotation.file, annotation.type) {
                        try {
                            parameter.parameterType.getConstructor().newInstance()
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
        }
        if (parameter.parameter.isAnnotationPresent(Autowired::class.java)) {
            return action.getCommand().getSharkCommand().getContext().getBean(parameter.parameterType)
        }
        val type = parameter.parameterType
        if (type == Array<OptionMapping>::class.java) return event.options.toTypedArray()
        if (type == CommandInteractionEvent::class.java) return commandEvent
        if (type == SlashCommandInteractionEvent::class.java) return event
        if (type == SlashCommandInteraction::class.java) return event.interaction
        if (type == CoroutineScope::class.java) return CoroutineScope(Dispatchers.Default)
        if (type == MessageChannelUnion::class.java) return event.channel
        if (type == User::class.java) return event.user
        if (type == DiscordLocale::class.java) return event.userLocale
        if (type == Guild::class.java) return event.guild
        if (type == InteractionHook::class.java) return event.id
        if (type == Member::class.java) return event.member
        if (type == String::class.java) return event.commandString
        if (type == IReplyCallback::class.java) return event
        if (type == Channel::class.java) return event.channel
        if (type == MessageChannel::class.java) return event.messageChannel
        if (type == GuildMessageChannelUnion::class.java) return event.guildChannel
        if (type == CommandData::class.java || type == SlashCommandData::class.java) return MetaCommand[event.name].getCommand()
        if (type == DataObject::class.java) return event.rawData
        if (type == JDA::class.java) return event.jda
        if (type == OffsetDateTime::class.java) return event.timeCreated
        if (type == InteractionType::class.java) return event.type
        if (type == Session::class.java || type == CommandSession::class.java) return commandEvent.getSession()
        if (type == IUser::class.java) return shark.core.entity.User.of(event.user, action.getSharkCommand().getContext().getBean<SharkClient>())
        if (type == shark.core.entity.User::class.java) return shark.core.entity.User.of(event.user, action.getCommand().getSharkCommand().getContext().getBean<SharkClient>())
        return null
    }

}

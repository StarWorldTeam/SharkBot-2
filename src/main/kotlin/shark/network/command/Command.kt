package shark.network.command

import com.google.common.collect.HashBiMap
import kodash.coroutine.Promise
import kotlinx.coroutines.*
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import shark.core.data.registry.ResourceLocation
import java.lang.annotation.*
import kotlin.reflect.*
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Controller
annotation class SharkCommandController(val id: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SharkCommandAction

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SharkCommandOption

class MetaCommandAction(private val function: KFunction<*>, private val command: MetaCommand) {

    suspend fun run(event: SlashCommandInteractionEvent): Any? {
        val args = arrayListOf<Any?>()
        args.add(0, command.getSharkCommand())
        val javaMethod = function.javaMethod!!
        function.javaMethod!!.parameters.indices.forEach {
            val parameter = javaMethod.parameters[it]
            val methodParameter = MethodParameter.forParameter(parameter)
            args.add(it + 1, SharkCommandActionResolver.resolveArgument(methodParameter, event))
        }
        return SharkCommandActionResolver.resolveResult(function.call(*args.toTypedArray()), event)
    }

}

class MetaCommand private constructor(private val sharkCommand: Command) {

    init {
        sharkCommand::class.memberFunctions.forEach {
            if (!it.javaMethod!!.isAnnotationPresent(SharkCommandAction::class.java)) return@forEach
            require(action == null) { "Duplicate trigger: ${it.name}" }
            this.action = MetaCommandAction(it, this)
        }
        validate()
    }

    fun validate() {
        assert(action != null) { "Command must have a trigger" }
    }

    private val command: SlashCommandData = Commands.slash(getResourceLocation().format { left, right -> "$left-$right" }, "-")
    fun getCommand() = command
    fun getSharkCommand() = sharkCommand

    companion object {

        val pool = HashBiMap.create<MetaCommand, Command>()

        operator fun get(command: Command) = pool.inverse().getOrPut(command) {
            MetaCommand(command).let {
                validate(it.getResourceLocation()) { it }
            }
        }
        operator fun get(command: MetaCommand) = pool.getOrPut(command) { validate(command.getResourceLocation()) { command.getSharkCommand() } }

        operator fun get(location: ResourceLocation) = pool.keys.find { it.getResourceLocation() == location }!!
        operator fun get(name: String) = pool.keys.find { it.getCommand().name == name }!!


        private fun <T> validate(location: ResourceLocation, block: () -> T): T {
            for (i in pool.keys) if (i.getResourceLocation() == location) throw UnsupportedOperationException()
            return block()
        }

    }

    fun getAnnotation() = getSharkCommand().javaClass.getAnnotation(SharkCommandController::class.java).id
    fun getResourceLocation(): ResourceLocation = ResourceLocation.of(getAnnotation())

    private var action: MetaCommandAction? = null
    fun getAction() = action!!

}

abstract class Command {

    fun getMetaCommand() = MetaCommand[this]

    override fun toString() = "${getMetaCommand().getResourceLocation()}"

}

@Component
class SharkCommandBeanPostProcessor : BeanPostProcessor {

    private val commands = mutableListOf<MetaCommand>()

    fun getCommands() = commands.toList()

    override fun postProcessBeforeInitialization(bean: Any, beanName: String) = bean.also {
        if (bean !is Command) return@also
        if (!bean.javaClass.isAnnotationPresent(SharkCommandController::class.java)) return@also
        commands.add(bean.getMetaCommand())
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
        return bean
    }

}

object SharkCommandActionResolver {

    suspend fun resolveResult(result: Any?, event: SlashCommandInteractionEvent): Any? {
        return when(result) {
            is Promise<*> -> resolveResult(result.await(), event)
            is Deferred<*> -> resolveResult(result.await(), event)
            is Job -> result.also { it.join() }
            is Fragment -> result.also { event.reply(it.asMessage()).queue() }
            is MessageFragment -> result.also { it.reply(event).queue() }
            is MessageCreateData -> result.also { event.reply(it).queue() }
            is MessageCreateBuilder -> resolveResult(result.build(), event)
            else -> result
        }
    }

    fun resolveArgument(parameter: MethodParameter, event: SlashCommandInteractionEvent): Any? {
        if (parameter.parameterType == SlashCommandInteractionEvent::class.java) return event
        if (parameter.parameterType == SlashCommandInteraction::class.java) return event.interaction
        if (parameter.parameterType == CoroutineScope::class.java) return CoroutineScope(Dispatchers.Default)
        return null
    }

}

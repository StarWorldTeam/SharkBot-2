package shark.network.command

import com.google.common.collect.HashBiMap
import kodash.coroutine.Promise
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import shark.core.data.registry.ResourceLocation
import java.lang.annotation.*
import kotlin.reflect.*
import kotlin.reflect.full.callSuspend
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
        val args = arrayOf<Any?>(function.javaMethod!!.parameterCount)
        val javaMethod = function.javaMethod!!
        function.javaMethod!!.parameters.indices.forEach {
            val parameter = javaMethod.parameters[it]
            val methodParameter = MethodParameter.forParameter(parameter)
            args[it] = SharkCommandActionResolver.resolveArgument(methodParameter, event)
        }
        return SharkCommandActionResolver.resolveResult(
            if (function.isSuspend) function.callSuspend(*args)
            else function.call(*args)
        )
    }

}

class MetaCommand private constructor(private val command: Command) {

    init {
        command::class.memberFunctions.forEach {
            if (!it.javaMethod!!.isAnnotationPresent(SharkCommandAction::class.java)) return@forEach
            require(action == null) { "Duplicate trigger: ${it.name}" }
            this.action = MetaCommandAction(it, this)
        }
    }

    fun getCommand() = command

    companion object {

        val pool = HashBiMap.create<Command, MetaCommand>()

        operator fun get(command: Command) = pool.getOrPut(command) { MetaCommand(command) }
        operator fun get(command: MetaCommand) = pool.inverse().getOrPut(command) { command.getCommand() }

    }

    fun getAnnotation() = getCommand().javaClass.getAnnotation(SharkCommandController::class.java).id
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

    suspend fun resolveResult(result: Any?): Any? {
        if (result is Promise<*>) return resolveResult(result.await())
        return result
    }

    fun resolveArgument(parameter: MethodParameter, event: SlashCommandInteractionEvent): Any? {
        if (parameter.parameterType == SlashCommandInteractionEvent::class.java) return event
        if (parameter.parameterType == SlashCommandInteraction::class.java) return event.interaction
        return null
    }

}

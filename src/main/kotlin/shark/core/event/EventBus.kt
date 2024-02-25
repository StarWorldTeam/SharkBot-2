package shark.core.event

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf

@Suppress("LeakingThis")
open class EventBus constructor(bindClass: Class<*>) {

    init {
        if (bindClass in instances) throw DuplicateKeyException("null")
        instances[bindClass] = this
    }

    companion object {
        private val instances: BiMap<Class<*>, EventBus> = HashBiMap.create()
        operator fun get(bindClass: Class<*>): EventBus = instances[bindClass]!!
        operator fun get(bindClass: KClass<*>): EventBus = get(bindClass.java)
        operator fun get(eventBus: EventBus): Class<*> = instances.inverse()[eventBus]!!
        operator fun contains(eventBus: EventBus) = instances.containsValue(eventBus)
        operator fun contains(bindClass: Class<*>) = instances.contains(bindClass)
        operator fun contains(bindClass: KClass<*>) = contains(bindClass.java)
    }

    constructor(name: KClass<*>) : this(name.java)

    val eventListeners = arrayListOf<Pair<KClass<in Event>, (Event) -> Unit>>()

    inline fun <reified T : Event> on(noinline callback: (T) -> Unit): (T) -> Unit =
        on(T::class, callback)

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> on(type: KClass<T>, callback: (T) -> Unit): (T) -> Unit {
        for (listener in eventListeners) {
            if (listener.first == type && listener.second == callback) return callback
        }
        eventListeners.add(Pair(type as KClass<in Event>, callback as ((Event) -> Unit)))
        return callback
    }

    inline fun <reified T : Event> off(noinline callback: (T) -> Unit): (T) -> Unit =
        off(T::class, callback)

    fun <T : Event> off(type: KClass<T>, callback: (T) -> Unit): (T) -> Unit {
        eventListeners.removeIf {
            (it.first == type) && (it.second == callback)
        }
        return callback
    }

    fun <T : Event> emit(event: T) = this.also {
        for (pair in listAllListeners(event.javaClass.kotlin)) {
            event.setEventBus(this)
            pair.second(event)
        }
    }

    inline fun <reified T : Event> listAllListeners() = listAllListeners(T::class)

    fun <T : Any> listAllListeners(eventType: KClass<T>): Sequence<Pair<KClass<in Event>, (Event) -> Unit>> {
        return sequence {
            for (pair in eventListeners) {
                if (pair.first.isSuperclassOf(eventType) || pair.first == eventType) {
                    yield(pair)
                }
            }
        }
    }

    fun offIf(filter: (KClass<in Event>, (Event) -> Unit) -> Boolean) =
        eventListeners.removeIf { filter(it.first, it.second) }

    override fun toString() = "EventBus@${get(this).name}"

}


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Controller
@MustBeDocumented
annotation class EventSubscriber(val eventBus: KClass<*>)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SubscribeEvent

@Component
class EventBusBeanPostProcessor : BeanPostProcessor {

    @Suppress("UNCHECKED_CAST")
    override fun postProcessBeforeInitialization(bean: Any, beanName: String) = bean.also {
        if (!bean::class.java.isAnnotationPresent(EventSubscriber::class.java)) return@also
        val eventBus = EventBus[bean::class.java.getAnnotation(EventSubscriber::class.java).eventBus]
        for (method in bean::class.java.methods) {
            if (method.isAnnotationPresent(SubscribeEvent::class.java) && method.parameterCount == 1) {
                val type = method.parameterTypes[0].kotlin
                if (!(type.isSubclassOf(Event::class) || type == Event::class)) continue
                eventBus.on(type as KClass<Event>) {
                    method.invoke(bean, it)
                }
            }
        }
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
        return bean
    }

}


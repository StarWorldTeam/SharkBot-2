package shark.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component
import shark.SharkBotEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.*


enum class ConfigType {

    YAML, JSON, PROPERTIES;

    inline fun <reified T> read(bytes: ByteArray, typeReference: TypeReference<T> = jacksonTypeRef()): T {
        return when (this) {
            YAML -> ObjectMapper(YAMLFactory()).readValue(bytes, typeReference)
            JSON -> ObjectMapper().readValue(bytes, typeReference)
            PROPERTIES -> {
                val entries = Properties().also { it.load(ByteArrayInputStream(bytes)) }.entries.map {
                    it.key to it.value
                }.toTypedArray()
                ObjectMapper().let { it.readValue(it.writeValueAsBytes(mapOf(*entries)), typeReference) }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> read(bytes: ByteArray, clazz: Class<T>): T {
        return (read(bytes, ClassTypeReference(clazz)) as? T)!!
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> default(clazz: Class<T>): ByteArray {
        val value = clazz.getConstructor().newInstance()
        return write(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> write(value: T): ByteArray {
        return when (this) {
            YAML -> ObjectMapper(YAMLFactory()).writeValueAsBytes(value)
            JSON -> ObjectMapper().writeValueAsBytes(value)
            PROPERTIES -> {
                val stream = ByteArrayOutputStream()
                ObjectMapper(YAMLFactory()).let { mapper ->
                    Properties().also { properties ->
                        mapper.readValue(mapper.writeValueAsBytes(value), jacksonTypeRef<Map<Any?, Any?>>()).forEach {
                            properties[it.key] = it.value.toString()
                        }
                    }.store(stream, "")
                }
                stream.toByteArray()
            }
        }
    }

}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class SharkConfig(val file: String, val type: ConfigType) {

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun <T> useConfig(clazz: Class<*>, path: String, type: ConfigType, default: () -> T? = { clazz.getConstructor().newInstance() as T? }): T? {
            val file = SharkBotEnvironment.getSharkDirectory("config", path).toFile()
            file.parentFile.mkdirs()
            if (!file.exists()) {
                try {
                    default()?.also { file.createNewFile() }?.let(type::write)?.let(file::writeBytes)
                } catch (_: Throwable) {}
            }
            if (file.exists()) {
                val bytes = file.readBytes()
                return type.read(bytes, clazz) as T?
            }
            return null
        }

        inline fun <reified T> useConfig(
            path: String,
            type: ConfigType,
            noinline default: () -> T? = { T::class.java.getConstructor().newInstance() }
        ) = useConfig(T::class.java, path, type, default)

    }

}

@Component
class SharkConfigBeanPostProcessor : BeanPostProcessor {

    fun getDirectory(vararg path: String): Path {
        return SharkBotEnvironment.getSharkDirectory("config", *path)
    }

    override fun postProcessBeforeInitialization(bean: Any, beanName: String) = bean.also {
        val declaredFields = bean.javaClass.declaredFields
        for (declaredField in declaredFields) {
            try {
                val annotation = declaredField.getAnnotation(SharkConfig::class.java) ?: continue
                declaredField.isAccessible = true
                declaredField.set(bean, SharkConfig.useConfig(declaredField.type, annotation.file, annotation.type))
            } catch (_: Throwable) {}
        }
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
        return bean
    }

}


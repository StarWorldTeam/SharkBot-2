package shark.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import de.undercouch.bson4jackson.BsonFactory
import okhttp3.internal.toImmutableMap
import shark.core.resource.SharkResource
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.*

enum class ResourceFileType {

    JSON, YAML, PROPERTIES, BSON;

    companion object {

        fun getType(fileName: String): ResourceFileType {
            if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) return YAML
            if (fileName.endsWith(".properties")) return PROPERTIES
            if (fileName.endsWith(".bson")) return BSON
            return JSON
        }

    }

    fun <T> readAs(content: ByteArray, typeReference: TypeReference<T>): T {
        return when (this) {
            BSON -> ObjectMapper(BsonFactory()).readValue(content, typeReference)
            YAML -> YAMLMapper().readValue(content, typeReference)
            PROPERTIES -> JsonMapper().let { mapper ->
                mapper.readValue(
                    mapper.writeValueAsBytes(Properties().also { properties -> properties.load(ByteArrayInputStream(content)) }.toImmutableMap()),
                    typeReference
                )
            }
            JSON -> JsonMapper().readValue(content, typeReference)
        }
    }

    fun <T> writeObject(outputStream: OutputStream, content: T): T = content.also {
        when (this) {
            BSON -> ObjectMapper(BsonFactory()).writeValue(outputStream, content)
            YAML -> YAMLMapper().writeValue(outputStream, content)
            PROPERTIES -> JsonMapper().let { mapper ->
                Properties().also { properties ->
                    properties.putAll(mapper.readValue(mapper.writeValueAsBytes(content), jacksonTypeRef<Map<Any?, Any?>>()))
                }
            }
            JSON -> JsonMapper().writeValue(outputStream, content)
        }
    }

}

object ResourceUtil {

    inline fun <reified K, reified V> resolveToMap(sharkResource: SharkResource) = resolveToMap<K, V>(sharkResource.contentAsByteArray(), sharkResource.getResourcePath())

    inline fun <reified K, reified V> resolveToMap(content: ByteArray, fileName: String): Map<K, V> {
        val typeReference = jacksonTypeRef<Map<K, V>>()
        return ResourceFileType.getType(fileName).readAs(content, typeReference)
    }

    fun <T> writeObject(outputStream: OutputStream, fileName: String, content: T) = ResourceFileType.getType(fileName).writeObject(outputStream, content)

}
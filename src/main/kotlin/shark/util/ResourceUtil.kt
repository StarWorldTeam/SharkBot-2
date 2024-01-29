package shark.util

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import okhttp3.internal.toImmutableMap
import shark.core.resource.SharkResource
import java.io.ByteArrayInputStream
import java.util.*

object ResourceUtil {

    inline fun <reified K, reified V> resolveToMap(sharkResource: SharkResource) = resolveToMap<K, V>(sharkResource.contentAsByteArray(), sharkResource.getResourcePath())

    inline fun <reified K, reified V> resolveToMap(content: ByteArray, fileName: String): Map<K, V> {
        val typeReference = jacksonTypeRef<Map<K, V>>()
        if (fileName.endsWith("yml", true) || fileName.endsWith("yaml", true))
            return YAMLMapper().readValue(content, typeReference)
        if (fileName.endsWith("properties", true))
            return JsonMapper().let { mapper ->
                mapper.readValue(
                    mapper.writeValueAsBytes(Properties().also { properties -> properties.load(ByteArrayInputStream(content)) }.toImmutableMap()),
                    typeReference
                )
            }
        return JsonMapper().readValue(content, typeReference)
    }

}
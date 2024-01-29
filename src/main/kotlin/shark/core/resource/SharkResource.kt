package shark.core.resource

import shark.core.data.registry.ResourceLocation
import java.io.InputStream
import java.nio.charset.Charset

abstract class SharkResource {

    abstract fun getResourceLocation(): ResourceLocation
    abstract fun getResourceType(): ResourceLocation
    abstract fun getResourcePath(): String

    open fun getFileName(suffix: Boolean = true) = getResourcePath().split("\\").joinToString("/").split("/").last().let {
        if (suffix) it
        else it.split(".").first()
    }

    abstract fun stream(): InputStream
    open fun contentAsByteArray(): ByteArray = stream().readAllBytes()
    open fun contentAsString(charset: Charset = Charsets.UTF_8): String = contentAsByteArray().toString(charset)

    override fun toString() = "SharkResource[type=${getResourceType()}, path=${getResourcePath()}, location=${getResourceLocation()}]"

}

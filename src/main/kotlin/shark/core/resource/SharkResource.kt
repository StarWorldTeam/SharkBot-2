package shark.core.resource

import shark.core.data.registry.ResourceLocation
import java.io.InputStream
import java.nio.charset.Charset

abstract class SharkResource {

    abstract fun getResourceLocation(): ResourceLocation
    abstract fun getResourceType(): ResourceLocation
    abstract fun getResourcePath(): String

    open fun getFileName(extension: Boolean = true) = getResourcePath().split("\\").joinToString("/").split("/").last().let {
        if (extension) it
        else it.split(".").let { split -> if (split.size <= 1) split else split.subList(0, split.size - 1) }.joinToString(".")
    }

    abstract fun stream(): InputStream
    open fun contentAsByteArray(): ByteArray = stream().readAllBytes()
    open fun contentAsString(charset: Charset = Charsets.UTF_8): String = contentAsByteArray().toString(charset)

    override fun toString() = "SharkResource[type=${getResourceType()}, path=${getResourcePath()}, location=${getResourceLocation()}]"

}

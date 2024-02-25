package shark.core.data.registry

import java.util.*

class ResourceLocation private constructor(val namespace: String, val path: String) {
    companion object {

        const val DEFAULT_NAMESPACE = "shark"

        fun isValidPath(path: String): Boolean {
            return path.trim().isNotEmpty() && path.chars().allMatch { i: Int -> isPathCharacter(i.toChar()) }
        }

        fun isValidNamespace(namespace: String): Boolean {
            return namespace.trim().isNotEmpty() && namespace.chars().allMatch { i: Int -> isNamespaceCharacter(i.toChar()) }
        }

        fun isPathCharacter(character: Char): Boolean {
            return character == '.' || character == '_' || character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' || character == '/' || character == '-'
        }

        fun isNamespaceCharacter(character: Char): Boolean {
            return character == '_' || character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' || character == '-'
        }

        fun of(namespace: String?, path: String): ResourceLocation {
            return ResourceLocation(namespace ?: DEFAULT_NAMESPACE, path)
        }

        fun of(location: String): ResourceLocation {
            return if (!location.contains(":")) of(DEFAULT_NAMESPACE, location) else {
                val split = Arrays.stream(location.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()).toList()
                of(split[0], split[1])
            }
        }

    }

    init {
        require(isValidNamespace(namespace)) { "Invalid namespace $namespace" }
        require(isValidPath(path)) { "Invalid path $path" }
    }

    override fun equals(other: Any?): Boolean {
        return other === this || (other != null && other is ResourceLocation && other.namespace == this.namespace && other.path == this.path)
    }

    override fun hashCode() = toString().hashCode()

    fun <T> format(formatter: (namespace: String, path: String) -> T) = formatter(namespace, path)
    fun toLanguageKey() = "$namespace.$path"
    fun toLanguageKey(type: String) = type + "." + this.toLanguageKey()
    fun toLanguageKey(type: String, attribute: String) = type + "." + this.toLanguageKey() + "." + attribute
    override fun toString() = "$namespace:$path"

}

annotation class SharkResourceLocation(val namespace: String, val path: String)

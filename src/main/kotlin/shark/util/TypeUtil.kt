package shark.util

import com.fasterxml.jackson.core.type.TypeReference
import java.lang.reflect.Type

class ClassTypeReference(private val clazz: Class<*>) : TypeReference<Any?>() {

    override fun getType(): Type {
        return clazz
    }

}
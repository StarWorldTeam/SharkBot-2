package shark.core.resource

import com.google.common.collect.HashBiMap
import okhttp3.internal.toImmutableList
import okhttp3.internal.toImmutableMap
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Controller
import shark.SharkBot
import shark.core.data.registry.ResourceLocation
import shark.core.event.Event
import shark.core.event.EventBus


open class AnyRepository<T> {

    private val pool = HashBiMap.create<ResourceLocation, T>()
    protected fun getPool() = pool

    operator fun set(key: ResourceLocation, value: T) = this.also { pool[key] = value }
    operator fun set(key: T, value: ResourceLocation) = this.also { pool.inverse()[key] = value }

    operator fun get(key: ResourceLocation) = pool[key]!!
    operator fun get(key: T) = pool.inverse()[key]!!
    operator fun contains(key: ResourceLocation) = pool.containsKey(key)
    operator fun contains(value: T) = pool.containsValue(value)
    operator fun minusAssign(value: T) { pool.inverse().remove(value) }
    operator fun minusAssign(value: ResourceLocation) { pool.remove(value) }
    fun entries() = pool.entries
    fun keys() = pool.keys
    fun values() = pool.values

}

open class ResourceRepository : AnyRepository<SharkResource>() {
    operator fun plusAssign(value: SharkResource) { getPool()[value.getResourceLocation()] = value }
}

@Controller
class ResourceLoader {

    private val dataEventBus = EventBus(this::class)
    private val assetsEventBus = EventBus(this::class)

    private val assetsRepository = ResourceRepository()
    private val dataResourceRepository = ResourceRepository()

    fun getDataEventBus() = dataEventBus
    fun getAssetsEventBus() = assetsEventBus
    fun getAssetsRepository() = assetsRepository
    fun getDataRepository() = dataResourceRepository

    fun loadAssets(path: String = "", classLoader: ClassLoader = SharkBot::class.java.classLoader) {
        val map = loadAsResourceMap("$path/assets", classLoader)
        for (sharkResources in map.values) {
            for (sharkResource in sharkResources) {
                assetsRepository += sharkResource
                getAssetsEventBus().emit(ResourceLoadedEvent(sharkResource))
            }
        }
        map.forEach { getAssetsEventBus().emit(TypedResourcesLoadedEvent(it.key, it.value)) }
    }

    fun loadData(path: String = "", classLoader: ClassLoader = SharkBot::class.java.classLoader) {
        val map = loadAsResourceMap("$path/data", classLoader)
        for (sharkResources in map.values) {
            for (sharkResource in sharkResources) {
                dataResourceRepository += sharkResource
                getDataEventBus().emit(ResourceLoadedEvent(sharkResource))
            }
        }
        map.forEach { getDataEventBus().emit(TypedResourcesLoadedEvent(it.key, it.value)) }
    }

    fun loadAsResourceMap(path: String, classLoader: ClassLoader): Map<ResourceLocation, List<SharkResource>> {
        val resourceMap = hashMapOf<ResourceLocation, MutableList<SharkResource>>()
        for (resource in load(path, classLoader)) {
            resourceMap.getOrPut(resource.getResourceType(), ::mutableListOf).add(resource)
        }
        return resourceMap.mapValues { it.value.toImmutableList() }.toImmutableMap()
    }

    fun load(path: String = "", classLoader: ClassLoader): Sequence<SharkResource> = sequence {
        val resolver = PathMatchingResourcePatternResolver(classLoader)
        val baseDir = resolver.getResource(path)
        val resolved = resolver.getResources("$path/**/*.*")

        for (resource in resolved) {
            try {
                val resourcePath = resource
                    .uri
                    .toString()
                    .removePrefix(baseDir.uri.toString())
                    .split("\\")
                    .joinToString("/")
                    .split("/")
                    .let { it.subList(1, it.size) }
                    .toMutableList()

                if (resourcePath.size < 2) continue
                if (resourcePath.size == 2) resourcePath[1].let {
                    resourcePath[1] = "root"
                    resourcePath += it
                }

                val resourceLocation =
                    ResourceLocation.of(resourcePath[0], resourcePath.subList(1, resourcePath.size).joinToString("/"))
                val resourceType = ResourceLocation.of(resourcePath[0], resourcePath[1])
                val resourceFilePath = resourcePath.subList(2, resourcePath.size).joinToString("/")

                val sharkResource = object : SharkResource() {
                    override fun getResourceLocation() = resourceLocation
                    override fun getResourcePath() = resourceFilePath
                    override fun getResourceType() = resourceType
                    override fun stream() = resource.inputStream
                }

                yield(sharkResource)
            } catch (_: Throwable) {}
        }
    }

}

class ResourceLoadedEvent(private val resource: SharkResource) : Event() {

    fun getResource() = resource
    fun getResourceLocation() = resource.getResourceLocation()

}

class TypedResourcesLoadedEvent(private val type: ResourceLocation, private val resources: List<SharkResource>) : Event() {

    fun getResourceType() = type
    fun getResources() = resources

}

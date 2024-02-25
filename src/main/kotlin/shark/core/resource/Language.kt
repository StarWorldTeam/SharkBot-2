package shark.core.resource

import com.google.common.collect.HashBiMap
import net.dv8tion.jda.api.interactions.DiscordLocale
import org.springframework.beans.factory.annotation.Autowired
import shark.SharkApplicationStartedEvent
import shark.SharkBot
import shark.core.event.EventSubscriber
import shark.core.event.SubscribeEvent
import shark.network.SharkClient
import shark.network.command.CommandOptionResolvingEvent
import shark.network.command.Fragment
import shark.util.ResourceUtil
import java.util.*

object RootLanguage : Language() {

    override fun get(key: String) = key
    override fun getOrDefault(key: String, defaultValue: String) = defaultValue
    override fun put(key: String, value: String) = value
    override fun putAll(from: Map<out String, String>) {}
    override fun putIfAbsent(key: String, value: String) = value
    override fun toDiscordLocale() = DiscordLocale.UNKNOWN

}

open class Language : MutableMap<String, String> by mutableMapOf() {

    companion object {
        private val languages = HashBiMap.create<String, Language>().apply {
            put("root", RootLanguage)
        }
        fun getEntries() = languages
        fun getName(language: Language) = languages.inverse()[language]
        fun getRoot() = languages["root"]!!
        fun getLanguages() = languages
        fun getLanguageOrPut(key: String, default: () -> Language = { Language() }): Language = languages.getOrPut(key, default)
        fun getDefault() = languages[SharkBot.applicationConfig.defaultLocale] ?: languages["root"]!!
        fun getLanguage(language: String, default: Language = getDefault()) = getLanguageOrNull(language) ?: default
        fun getLanguageOrNull(language: String) = languages[language]
        fun getLanguage(language: DiscordLocale, default: Language = getDefault()) = getLanguageOrNull(language) ?: default
        fun getLanguageOrNull(language: DiscordLocale): Language? = languages.values.firstOrNull { it.toDiscordLocale() == language }
    }

    open fun getOrDefault(key: String, defaultValue: String = key) = super.getOrDefault(key, defaultValue)
    open fun getOrNull(key: String) = this[key]
    open fun getLocaleTag() = this["language.locale"]
    open fun toDiscordLocale() = DiscordLocale.values().first { it.locale == getLocaleTag() }
    open fun getCompletionRate(compare: Language = getDefault()): Double {
        return compare.keys.filter { this.contains(it) }.size.toDouble() / compare.keys.size.toDouble()
    }

    open fun format(key: String, vararg values: Any?, fallback: () -> String = { key }): String {
        val args = values.map {
            return@map when (it) {
                is Fragment -> it.asNode(this)
                else -> it
            }
        }.toTypedArray()
        return (getOrNull(key) ?: fallback()).format(Locale.ROOT, *args)
    }

}


fun DiscordLocale.toSharkLanguage(): Language = Language.getLanguage(this, Language.getRoot())

@EventSubscriber(SharkBot::class)
class LanguageResourcesProcessor {

    @Autowired
    private lateinit var assetsLoader: AssetsResourceLoader

    @SubscribeEvent
    fun onSharkApplicationStarted(event: SharkApplicationStartedEvent) {
        assetsLoader.getEventBus().on<TypedResourcesLoadedEvent> {
            if (it.getResourceType().path == "language") {
                for (language in it.getResources()) {
                    val languageName = language.getFileName(false)
                    Language.getLanguageOrPut(languageName).putAll(
                        ResourceUtil.resolveToMap(language)
                    )
                }
            }
        }
    }

}

@EventSubscriber(SharkClient::class)
class LanguageArgumentResolver {

    @SubscribeEvent
    fun onOptionResolving(event: CommandOptionResolvingEvent) {
        val option = event.getOption() ?: return
        if (event.getValue() != null) return
        val parameterType = event.getParameter().parameterType
        if (parameterType == DiscordLocale::class.java) event.setValue(
            DiscordLocale.values().firstOrNull { it.locale == option.asString }
        )
        if (parameterType == Language::class.java) event.setValue(
            Language.getLanguageOrNull(option.asString)
        )
    }

}

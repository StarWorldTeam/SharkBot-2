package shark.core.resource

import com.google.common.collect.HashBiMap
import net.dv8tion.jda.api.interactions.DiscordLocale
import shark.SharkBot
import shark.network.command.Fragment
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
        fun getLanguages() = languages
        fun getLanguageOrPut(key: String, default: () -> Language = { Language() }): Language = languages.getOrPut(key, default)
        fun getDefault() = languages[SharkBot.applicationConfig.defaultLocale] ?: languages["root"]!!
        fun getLanguage(language: String, default: Language = getDefault()) = getLanguageOrNull(language) ?: default
        fun getLanguageOrNull(language: String) = languages[language]
        fun getLanguage(language: DiscordLocale, default: Language = getDefault()) = getLanguageOrNull(language) ?: default
        fun getLanguageOrNull(language: DiscordLocale): Language? = languages.values.firstOrNull { it.toDiscordLocale() == language }
    }

    open fun getOrDefault(key: String, defaultValue: String = key) = super.getOrDefault(key, defaultValue)

    open fun toDiscordLocale() = DiscordLocale.values().first { it.locale == this["language.locale"] }

    open fun format(key: String, vararg values: Any?): String {
        val args = values.map {
            return@map when (it) {
                is Fragment -> it.asNode()
                else -> it
            }
        }.toTypedArray()
        return getOrDefault(key).format(Locale.ROOT, *args)
    }

}


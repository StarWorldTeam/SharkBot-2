package shark.core.entity

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import kodash.data.tag.CompoundTag
import kodash.data.tag.Tags
import kodash.function.memorize
import shark.SharkBotEnvironment
import shark.core.resource.Language
import shark.network.ISharkClient
import shark.util.FileStorage
import shark.util.IStorage

data class UserMeta(
    var locale: String? = null,
    var tag: Map<String, Any?> = mapOf()
)

interface IUser {
    fun getUserMeta(): UserMeta
    fun editUserMeta(block: UserMeta.() -> Unit) = this
    fun getUserLocaleOrDefault(default: Language): Language
    fun getUserLocaleOrNull(): Language?
    fun setUserLocale(language: Language)
    fun getTag(): CompoundTag
    fun editTag(block: CompoundTag.() -> Unit) = this
    fun getId(): Long
}

class User(private val storage: IStorage<UserMeta>, private val userId: Long, private val client: ISharkClient) : IUser {

    override fun getId() = userId

    companion object {

        fun of(userId: Long, client: ISharkClient) = memorize(userId, client.hashCode()) {
            User(
                FileStorage(
                    jacksonTypeRef(),
                    { UserMeta() },
                    SharkBotEnvironment.getSharkDirectory("data", "user", "$userId.bson")
                ),
                userId, client
            )
        }

        fun of(user: net.dv8tion.jda.api.entities.User, client: ISharkClient) = of(user.idLong, client)

    }

    init {
        storage.flush()
    }

    private val internalTag: CompoundTag = CompoundTag().also { tag ->
        storage.get().tag.mapValues { Tags.fromValue(it.value) }.entries.forEach {
            tag.put(it.key, it.value)
        }
    }
    override fun getTag() = internalTag
    override fun editTag(block: CompoundTag.() -> Unit) = this.also {
        block(getTag())
        editUserMeta { tag = getTag().read() }
    }

    private val internalUserMeta: UserMeta = getStorage().get()
    override fun getUserMeta() = internalUserMeta
    override fun editUserMeta(block: UserMeta.() -> Unit) = this.also {
        block(getUserMeta())
        storage.set(internalUserMeta)
    }

    fun getStorage() = storage
    override fun getUserLocaleOrDefault(default: Language) = getUserMeta().locale?.let { Language.getLanguage(it, default) } ?: default.also {
        editUserMeta {
            locale = default.getLocaleTag()
        }
    }
    override fun getUserLocaleOrNull() = getUserMeta().locale?.let { Language.getLanguageOrNull(it) }
    override fun setUserLocale(language: Language) = Unit.also {
        editUserMeta { locale = Language.getName(language) }
    }

}
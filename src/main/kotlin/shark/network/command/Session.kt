package shark.network.command

import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageEdit
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.utils.messages.MessageEditData
import shark.network.ISharkClient
import java.util.concurrent.TimeUnit

interface Session {

    fun getClient(): ISharkClient
    fun isPrivateChannel(): Boolean
    fun getChannelId(): Long?
    fun getGuildId(): Long?
    fun getUserId(): Long?

    fun getGuild(): Guild? = getGuildId()?.let {
        getClient().getGuildById(it)
    }

    fun getChannel(): Channel? = getChannelId()?.let {
        getClient().getChannelById(it)
    }

    fun getUser(): User? = getUserId()?.let {
        getClient().getUserById(it)
    }

    fun getSharkUser(): shark.core.entity.User? = getUser()?.let {
        getClient().getUser(it)
    }

}

class CommandSession(private val event: SlashCommandInteractionEvent, private val client: ISharkClient) : Session {

    fun getEvent() = event
    override fun getClient() = client
    override fun getGuildId() = event.guild?.idLong
    override fun getUserId() = event.user.idLong
    override fun getChannelId() = event.channelIdLong
    override fun isPrivateChannel() = event.channelType == ChannelType.PRIVATE || event.channel is PrivateChannel
    override fun getChannel() = event.channel
    override fun getUser() = event.user
    override fun getGuild() = event.guild


    fun editReply(block: InlineMessage<MessageEditData>.() -> Unit) {
        getEvent().reply("...").queueAfter(1, TimeUnit.MILLISECONDS) {
            it.editOriginal(MessageEdit(replace = true) { block(this) }).queue()
        }
    }

}

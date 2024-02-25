package sharkbot.command

import net.dv8tion.jda.api.interactions.DiscordLocale
import shark.core.entity.User
import shark.core.event.EventSubscriber
import shark.core.event.SubscribeEvent
import shark.core.resource.Language
import shark.network.SharkClient
import shark.network.command.*
import shark.network.interaction.InteractionAutoCompleteEvent
import java.time.LocalDate
import java.util.*
import kotlin.random.Random

@SharkCommand("language")
@EventSubscriber(SharkClient::class)
class LanguageCommand : Command() {

    @Action
    fun run(user: User, @Option("language", autoComplete = AutoCompleteMode.ENABLED) language: Language): Fragment {
        user.setUserLocale(language)
        val name = Language.getLanguages().inverse()[language]
        val displayName = language.toDiscordLocale().toLocale().getDisplayName(language.toDiscordLocale().toLocale())
        return Fragment.translatable("command.shark.language.success", name, displayName)
    }

    @Suppress("DuplicatedCode")
    @SubscribeEvent
    fun autoComplete(event: InteractionAutoCompleteEvent) {
        if (!event.isCommandOption(this, "language")) return
        val getCompletionRate = { language: Language -> String.format(Locale.ROOT, "%.2f%%", language.getCompletionRate() * 100) }
        val choices = mutableListOf<net.dv8tion.jda.api.interactions.commands.Command.Choice>()
        for (i in Language.getEntries()) {
            if (i.value.toDiscordLocale() == DiscordLocale.UNKNOWN && i.value != Language.getRoot()) continue
            choices.add(net.dv8tion.jda.api.interactions.commands.Command.Choice("${i.key} (${getCompletionRate(i.value)})", i.key))
        }
        for (i in Language.getEntries()) {
            if (i.value.toDiscordLocale() == DiscordLocale.UNKNOWN && i.value != Language.getRoot()) continue
            choices.add(net.dv8tion.jda.api.interactions.commands.Command.Choice("${i.value.toDiscordLocale().languageName} (${getCompletionRate(i.value)})", i.key))
        }
        for (i in Language.getEntries()) {
            if (i.value.toDiscordLocale() == DiscordLocale.UNKNOWN) continue
            choices.add(net.dv8tion.jda.api.interactions.commands.Command.Choice("${i.value.toDiscordLocale().nativeName} (${getCompletionRate(i.value)})", i.key))
        }
        for (i in Language.getEntries()) {
            if (i.value.toDiscordLocale() == DiscordLocale.UNKNOWN) continue
            choices.add(net.dv8tion.jda.api.interactions.commands.Command.Choice("${i.value.toDiscordLocale().toLocale().getDisplayName(i.value.toDiscordLocale().toLocale())} (${getCompletionRate(i.value)})", i.key))
        }
        event.addChoices(*choices.toTypedArray(), permanent = false)
    }

}

@SharkCommand("luck")
class LuckCommand : Command() {

    @Action
    fun run(session: CommandSession): Fragment {
        val date = LocalDate.now()
        val seed = session.getUserId().toBigInteger()
            .times(date.year.toBigInteger())
            .times(date.monthValue.toBigInteger())
            .times(date.dayOfMonth.toBigInteger())
        val random = Random(seed.toLong())
        val value = random.nextInt(0,101)
        return Fragment.translatable(getTranslation("value"), value)
    }

}

package shark.network.command

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.core.MethodParameter
import shark.core.event.Event
import shark.core.resource.Language
import shark.core.resource.toSharkLanguage
import shark.network.SharkClient
import shark.util.ConstantUtil
import java.util.*

open class CommandTranslation(private val command: Command) {

    open fun getCommand() = command
    open fun getLocation() = command.getMetaCommand().getResourceLocation()

    open fun getCommandName() = Fragment.translatable(
        "command.${getLocation().namespace}.${getLocation().path.split("/")[0]}.name"
    ) { Fragment.literal(getLocation().path.split("/")[0]) }

    open fun getOptionName(name: String) = Fragment.translatable(
        "command.${getLocation().namespace}.${getLocation().path.split("/")[0]}.option.$name.name"
    ) { Fragment.literal(name) }

    open fun getCommandFullName(): Fragment {
        val fragments = mutableListOf<Fragment>()
        fragments += getCommandName()
        val subNames = getCommandSubNames()
        if (subNames.isNotEmpty()) for (name in subNames) {
            fragments += Fragment.literal("-")
            fragments += name
        }
        return Fragment.multiple(*fragments.toTypedArray())
    }

    open fun getCommandSubNames(): Array<Fragment> {
        val location = getLocation()
        val split = getLocation().path.split("/")
        return if (split.size > 1) (
                split.subList(1, split.size).stream()
                    .map { name ->
                        Fragment.translatable(
                            "command.%s.%s.subName.%s".format(
                                Locale.ROOT,
                                location.namespace,
                                split[0],
                                name
                            )
                        ) { Fragment.literal(name) }
                    }
                    .toList()
                ).toTypedArray() else arrayOf()
    }

    open fun getCommandDescription() = Fragment.translatable("command.${getLocation().namespace}.${getLocation().path.split("/").joinToString("-")}.description") { Fragment.literal("-") }
    open fun getOptionDescription(name: String) = Fragment.translatable("command.${getLocation().namespace}.${getLocation().path.split("/").joinToString("-")}.option.$name.description") { Fragment.literal("-") }

    open fun putTranslations(event: CommandSetupEvent) {
        val translation = event.getTranslation()
        val commandData = event.getCommandData()
        for (locale in DiscordLocale.values()) {
            if (locale.toSharkLanguage() == Language.getRoot()) continue
            val sharkLocale = Language.getLanguage(locale, Language.getDefault())
            commandData.setNameLocalization(locale, translation.getCommandFullName().asText(sharkLocale))
            commandData.setDescriptionLocalization(locale, translation.getCommandDescription().asText(sharkLocale))
            for (option in commandData.options) {
                option.setNameLocalization(locale, translation.getOptionName(option.name).asText(sharkLocale))
                option.setDescriptionLocalization(locale, translation.getOptionDescription(option.name).asText(sharkLocale))
            }
        }
    }

}

open class CommandSetupEvent(private val commandData: SlashCommandData, private val client: SharkClient): Event() {

    private var translation = CommandTranslation(MetaCommand[commandData]!!)
    fun getTranslation() = translation
    fun setTranslation(translation: CommandTranslation) { this.translation = translation }

    fun getCommandData() = commandData
    fun getClient() = client

    fun addOption(name: String, description: String = ConstantUtil.emptyCommandDescription, required: Boolean = false, autoComplete: Boolean = false, optionType: OptionType = OptionType.STRING) {
        getCommandData().addOption(optionType, name, description, required, autoComplete)
    }

}

class CommandArgumentResolvingEvent(
    private val parameter: MethodParameter,
    private val event: CommandInteractionEvent,
    private val action: MetaCommandAction,
    private var value: Any? = null
) : Event() {

    fun setValue(value: Any?) = ::value.set(value)
    fun getValue() = value
    fun getAction() = action
    fun getSlashCommandInteractionEvent() = event.getInteractionEvent()
    fun getCommandInteractionEvent() = event
    fun getParameter() = parameter


}

class CommandResultResolvingEvent(private val action: MetaCommandAction, private val event: CommandInteractionEvent, private var value: Any? = null) : Event() {

    fun setValue(value: Any?) = ::value.set(value)
    fun getValue() = value
    fun getAction() = action
    fun getSlashCommandInteractionEvent() = event.getInteractionEvent()
    fun getCommandInteractionEvent() = event

}

class CommandOptionResolvingEvent(
    private val parameter: MethodParameter,
    private val event: CommandInteractionEvent,
    private val action: MetaCommandAction,
    private var value: Any? = null,
) : Event() {

    fun getOption() = getSlashCommandInteractionEvent().getOption(Command.Option.getName(parameter.parameter))
    fun getAnnotation(): Command.Option = parameter.parameter.getAnnotation(Command.Option::class.java)
    fun setValue(value: Any?) = ::value.set(value)
    fun getValue() = value
    fun getAction() = action
    fun getSlashCommandInteractionEvent() = event.getInteractionEvent()
    fun getCommandInteraction() = event
    fun getParameter() = parameter


}

class CommandInteractionEvent(private val interactionEvent: SlashCommandInteractionEvent, private val client: SharkClient) : Event() {

    private val session = CommandSession(interactionEvent, client)

    fun getClient() = client
    fun getInteractionEvent() = interactionEvent
    fun getSession() = session

}

package shark.network.interaction

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload
import shark.core.event.Event
import shark.network.command.Command
import shark.network.command.MetaCommand

open class CommandInteractionPayloadEvent <T : CommandInteractionPayload> (private val interaction: T) : Event() {

    fun getInteraction() = interaction
    fun getCommand() = MetaCommand[getInteraction().name]

}

class CommandInteractionEvent (event: SlashCommandInteractionEvent, private val result: Any?) : CommandInteractionPayloadEvent<SlashCommandInteractionEvent>(event) {

    fun getResult(): Any? = result

}

class InteractionAutoCompleteEvent (event: CommandAutoCompleteInteractionEvent) : CommandInteractionPayloadEvent<CommandAutoCompleteInteractionEvent>(event) {

    private val choices = mutableListOf<Iterable<Pair<Boolean, Choice>>>()

    fun isCommandOption(command: Command, option: String) = getCommand().getSharkCommand() == command && getFocusedOption().name == option

    fun ifOption(command: Command, option: String, block: InteractionAutoCompleteEvent.() -> Unit) {
        if (isCommandOption(command, option)) block()
    }

    fun getChoices(): MutableList<Iterable<Pair<Boolean, Choice>>> = choices

    fun getFocusedOption() = getInteraction().focusedOption

    fun addChoices(vararg choices: Choice, permanent: Boolean = false) {
        addChoices(*choices.map { permanent to it }.toTypedArray())
    }

    fun addChoices(vararg iterable: Iterable<Pair<Boolean, Choice>>) {
        choices.addAll(iterable)
    }

    fun addChoices(vararg choices: Pair<Boolean, Choice>) {
        this.choices.add(choices.toList())
    }

}

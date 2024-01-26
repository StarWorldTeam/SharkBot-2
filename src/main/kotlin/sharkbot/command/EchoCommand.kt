package sharkbot.command

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import shark.network.command.Command
import shark.network.command.SharkCommandAction
import shark.network.command.SharkCommandController

@SharkCommandController("echo")
class EchoCommand : Command() {

    @SharkCommandAction
    fun run(event: SlashCommandInteractionEvent) {
        event.reply("Hello, World!").queue()
    }

}
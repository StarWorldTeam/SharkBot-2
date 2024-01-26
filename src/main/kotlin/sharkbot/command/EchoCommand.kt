package sharkbot.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import shark.network.command.*

@SharkCommandController("echo")
class EchoCommand : Command() {

    @SharkCommandAction
    fun run(event: SlashCommandInteractionEvent, scope: CoroutineScope) = scope.async {
        fragmentMessage {
            addContent("Hello, World!")
        }
    }

}
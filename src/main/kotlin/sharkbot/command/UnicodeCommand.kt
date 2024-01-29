package sharkbot.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.beans.factory.annotation.Autowired
import shark.network.SharkClient
import shark.network.command.*
import shark.network.interaction.InteractionAutoCompleteEvent

@SharkCommandController("unicode")
class UnicodeCommand : Command() {

    @SharkCommandAction
    fun run(
        @SharkCommandOption("unicode", autoComplete = AutoCompleteMode.ENABLED) character: Char,
        scope: CoroutineScope,
    ) = scope.async {
        fragmentMessage {
            addContent("[${character.code}] $character\n\n")
            val items = mutableSetOf<String>()
            for (method in Character::class.java.methods) {
                if (!method.name.startsWith("is")) continue
                val item = method.name.substring(2)
                try {
                    if (method.invoke(null, character) == true) items.add(item)
                } catch (_: Throwable) {}
            }
            addContent(items.joinToString(separator = "  ") { "[$it]" })
        }
    }

    @Autowired
    private lateinit var client: SharkClient

    override fun setup(data: SlashCommandData) {
        client.getEventBus().on<InteractionAutoCompleteEvent> {
            val event = it
            event.ifOption(this@UnicodeCommand, "unicode") {
                val value = event.getFocusedOption().value
                if (value.startsWith("=")) {
                    val number = value.toCharArray().getOrNull(1)?.code!!
                    addChoices(
                        true to net.dv8tion.jda.api.interactions.commands.Command.Choice(
                            "[${number}] ${number.toChar()}",
                            number.toLong()
                        )
                    )
                } else {
                    val number: Int? = if (value.startsWith("0x")) value.substring(2).toIntOrNull(16)
                    else value.toIntOrNull()
                    if (number != null) addChoices(
                        true to net.dv8tion.jda.api.interactions.commands.Command.Choice(
                            "[$number] ${number.toChar()}",
                            number.toLong(),
                        )
                    )
                }
            }
        }
    }

}
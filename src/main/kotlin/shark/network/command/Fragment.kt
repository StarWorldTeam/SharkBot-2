package shark.network.command

import dev.minn.jda.ktx.messages.MessageCreate
import dev.minn.jda.ktx.messages.MessageEdit
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.safety.Safelist
import shark.core.resource.Language
import shark.util.BrowserUtil
import java.util.concurrent.TimeUnit

interface Fragment {

    fun asText(): String = asText(Language.getRoot())
    fun asMessage(): MessageCreateData = asMessage(Language.getRoot())
    fun asNode(): Node = asNode(Language.getRoot())
    fun asText(language: Language): String
    fun asMessage(language: Language): MessageCreateData = MessageCreate(asText(language))
    fun asNode(language: Language): Node = TextNode(asText())

    companion object {

        fun text(text: String): Fragment = object : Fragment {
            override fun asText(language: Language) = text
        }

        fun literal(content: String): Fragment = object : Fragment {
            override fun asNode(language: Language) = Jsoup.parse(content, "", Parser.xmlParser())
            override fun asText(language: Language) = asNode(language).also {
                it.select("shark-text").toList().forEach { node ->
                    node.after(TextNode(node.attr("text")))
                    node.remove()
                }
                it.select("shark-html").toList().forEach { node ->
                    node.after(Jsoup.parse(node.attr("html"), "", Parser.xmlParser()).text())
                    node.remove()
                }
            }.html().let {
                val outputSettings = Document.OutputSettings().prettyPrint(false)
                Jsoup.clean(it, "", Safelist.none(), outputSettings)
            }
        }

        fun message(data: MessageCreateData): Fragment = object : Fragment {
            override fun asMessage(language: Language) = data
            override fun asText(language: Language) = data.content
        }

        fun node(node: Node): Fragment = object : Fragment {
            override fun asNode(language: Language) = node
            override fun asText(language: Language) = literal(asNode(language).toString()).asText(language)
        }

        fun newLine(): Fragment = object : Fragment {
            override fun asText(language: Language) = "\n"
            override fun asNode(language: Language) = Element("br")
        }

        fun multiple(vararg fragments: Fragment): Fragment = object : Fragment {
            override fun asText(language: Language) = fragments.joinToString("") { it.asText(language) }
            override fun asMessage(language: Language) = MessageCreate(tts = fragments.firstOrNull { it.asMessage(language).isTTS } != null) {
                for (fragment in fragments) {
                    val message = fragment.asMessage(language)
                    this.content += message.content
                    this.embeds += message.embeds
                    this.components += message.components
                    this.files += message.files
                    this.mentions {
                        message.mentionedRoles.forEach(this::role)
                        message.mentionedUsers.forEach(this::user)
                    }
                }
            }
            override fun asNode(language: Language) = Element("div").appendChildren(fragments.map { it.asNode(language) })
        }

        fun translatable(key: String, vararg args: Any?, fallback: () -> Fragment = { literal(key) }): Fragment = object : Fragment {
            override fun asText(language: Language) = literal(language.format(key, *args, fallback = { fallback().asText(language) })).asText(language)
            override fun asNode(language: Language): Node {
                var state = false
                val literal = literal(language.format(key, *args, fallback = { state = true; literal(key).asText() })).asNode(language)
                return if (state) fallback().asNode(language)
                else literal
            }
            override fun asMessage(language: Language): MessageCreateData {
                var state = false
                val literal = literal(language.format(key, *args, fallback = { state = true; literal(key).asText() })).asMessage(language)
                return if (state) fallback().asMessage(language)
                else literal
            }
        }

    }

}

enum class MessageFragmentMode {
    NODE, TEXT, MESSAGE
}

class MessageFragment(private val fragment: Fragment, private val mode: MessageFragmentMode) {

    fun getFragment() = fragment
    fun getMode() = mode

    fun reply(event: IReplyCallback, language: Language = Language.getDefault()): Any = when (getMode()) {
        MessageFragmentMode.NODE -> event.reply("...").queueAfter(1, TimeUnit.MILLISECONDS) {
            it.editOriginal(
                MessageEdit {
                    files += BrowserUtil.getImage(getFragment().asNode(language))
                }
            ).queue()
        }
        MessageFragmentMode.MESSAGE -> {
            val message = getFragment().asMessage(language)
            if (message.files.isEmpty()) event.reply(getFragment().asMessage(language)).queue()
            else event.reply("...").queueAfter(1, TimeUnit.MILLISECONDS) {
                it.editOriginal(
                    MessageEdit(message.content, message.embeds, message.files, message.components, replace = true) {
                        mentions {
                            message.mentionedUsers.forEach { mentioned -> user(mentioned) }
                            message.mentionedRoles.forEach { mentioned -> role(mentioned) }
                        }
                    }
                ).queue()
            }
        }
        MessageFragmentMode.TEXT -> event.reply(getFragment().asText(language)).queue()
    }

}

fun fragment(text: String, parse: Boolean = false) = MessageFragment(if (parse) Fragment.literal(text) else Fragment.text(text), mode=MessageFragmentMode.TEXT)
fun fragmentMessage(block: MessageCreateBuilder.() -> Unit) = MessageFragment(Fragment.message(MessageCreateBuilder().also(block).build()), MessageFragmentMode.MESSAGE)
fun fragmentDocument(block: Document.() -> Unit) = MessageFragment(Fragment.node(Document("").also(block)), MessageFragmentMode.NODE)

package shark.network.command

import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import shark.util.BrowserUtil

interface Fragment {

    fun asText(): String
    fun asMessage(): MessageCreateData = MessageCreate(asText())
    fun asNode(): Node = TextNode(asText())

    companion object {

        fun text(text: String): Fragment = object : Fragment {
            override fun asText() = text
        }

        fun literal(content: String): Fragment = object : Fragment {
            override fun asNode() = Jsoup.parse(content, "", Parser.xmlParser())
            override fun asText() = asNode().text()
        }

        fun message(data: MessageCreateData): Fragment = object : Fragment {
            override fun asMessage() = data
            override fun asText() = data.content
        }

        fun node(node: Node): Fragment = object : Fragment {
            override fun asNode() = node
            override fun asText() = Jsoup.parse(asNode().toString(), "", Parser.xmlParser()).text()
        }

    }

}

enum class MessageFragmentMode {
    NODE, TEXT, MESSAGE
}

class MessageFragment(private val fragment: Fragment, private val mode: MessageFragmentMode) {

    fun getFragment() = fragment
    fun getMode() = mode

    fun reply(event: IReplyCallback) = when (getMode()) {
        MessageFragmentMode.NODE -> event.reply(MessageCreateData.fromFiles(BrowserUtil.getImage(getFragment().asNode())))
        MessageFragmentMode.MESSAGE -> event.reply(getFragment().asMessage())
        MessageFragmentMode.TEXT -> event.reply(getFragment().asText())
    }

}

fun fragment(text: String, parse: Boolean = false) = MessageFragment(if (parse) Fragment.literal(text) else Fragment.text(text), mode=MessageFragmentMode.TEXT)
fun fragmentMessage(block: MessageCreateBuilder.() -> Unit) = MessageFragment(Fragment.message(MessageCreateBuilder().also(block).build()), MessageFragmentMode.MESSAGE)
fun fragmentDocument(block: Document.() -> Unit) = MessageFragment(Fragment.node(Document("").also(block)), MessageFragmentMode.NODE)

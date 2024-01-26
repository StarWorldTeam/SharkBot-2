package shark.util

import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.ScreenshotType
import net.dv8tion.jda.api.utils.FileUpload
import org.jsoup.Jsoup
import org.jsoup.nodes.Node

object BrowserUtil {

    fun makeScreenShot(html: String?): ByteArray {
        val doc = Jsoup.parse(html!!)
        val playwright = Playwright.create()
        val browser = playwright.firefox().launch()
        val page = browser.newPage()
        if (doc.attr("min-width").isNotEmpty()) page.setViewportSize(
            (doc.attr("min-width").ifEmpty { "100" }).toInt(),
            (doc.attr("min-height").ifEmpty { "100" }).toInt()
        )
        page.navigate("about:blank")
        page.setContent(html)
        val screenshot = page.screenshot(Page.ScreenshotOptions().setType(ScreenshotType.PNG))
        playwright.close()
        return screenshot
    }

    fun makeScreenShot(tag: Node): ByteArray {
        return makeScreenShot(tag.toString())
    }

    fun getImage(image: ByteArray?, name: String?): FileUpload {
        return FileUpload.fromData(image!!, name!!)
    }

    fun getImage(image: ByteArray?): FileUpload {
        return FileUpload.fromData(image!!, "image.png")
    }

    fun getImage(renderable: Node): FileUpload {
        return getImage(makeScreenShot(renderable.toString()))
    }

}
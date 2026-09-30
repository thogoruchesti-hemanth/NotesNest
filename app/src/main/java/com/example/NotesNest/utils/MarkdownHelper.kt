package com.example.NotesNest.utils

import android.content.Context
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

object MarkdownHelper {

    private var markwonInstance: Markwon? = null

    @JvmStatic
    fun getMarkwon(context: Context): Markwon {
        return markwonInstance ?: synchronized(this) {
            val instance = Markwon.builder(context.applicationContext)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(context.applicationContext))
                .build()
            markwonInstance = instance
            instance
        }
    }

    /**
     * Render Markdown or legacy HTML content into a TextView safely.
     */
    @JvmStatic
    fun renderMarkdown(textView: TextView, content: String?) {
        if (content.isNullOrEmpty()) {
            textView.text = ""
            return
        }

        val rawText = if (isHtmlContent(content)) {
            convertHtmlToMarkdown(content)
        } else {
            content
        }

        getMarkwon(textView.context).setMarkdown(textView, rawText)
    }

    /**
     * Detects if content contains legacy HTML tags.
     */
    @JvmStatic
    fun isHtmlContent(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("<p") || lower.contains("<div") ||
                lower.contains("<b") || lower.contains("<i") ||
                lower.contains("<ul") || lower.contains("<ol") ||
                lower.contains("<h1") || lower.contains("<h2") ||
                lower.contains("<input") || lower.contains("<span") ||
                lower.contains("<br")
    }

    /**
     * Converts legacy HTML formatting tags to standard Markdown.
     */
    @JvmStatic
    fun convertHtmlToMarkdown(html: String): String {
        var text = html

        // Remove scripts and style tags if any
        text = text.replace(Regex("(?s)<script.*?>.*?</script>"), "")
        text = text.replace(Regex("(?s)<style.*?>.*?</style>"), "")

        // Headings
        text = text.replace(Regex("(?i)<h1.*?>(.*?)</h1>"), "# $1\n\n")
        text = text.replace(Regex("(?i)<h2.*?>(.*?)</h2>"), "## $1\n\n")

        // Bold & Italic
        text = text.replace(Regex("(?i)<(b|strong).*?>(.*?)</(b|strong)>"), "**$2**")
        text = text.replace(Regex("(?i)<(i|em).*?>(.*?)</(i|em)>"), "*$2*")

        // Checkboxes
        text = text.replace(Regex("(?i)<input[^>]*checked[^>]*>"), "- [x] ")
        text = text.replace(Regex("(?i)<input[^>]*>"), "- [ ] ")

        // List items
        text = text.replace(Regex("(?i)<li.*?>(.*?)</li>"), "- $1\n")

        // Paragraphs & Line breaks
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)<p.*?>(.*?)</p>"), "$1\n\n")
        text = text.replace(Regex("(?i)<div.*?>(.*?)</div>"), "$1\n")

        // Strip any remaining XML/HTML tags
        text = text.replace(Regex("<[^>]+>"), "")

        // Unescape standard HTML entities
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")

        return text.trim()
    }
}

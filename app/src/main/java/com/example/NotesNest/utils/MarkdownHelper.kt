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

        var rawText = if (isHtmlContent(content)) {
            convertHtmlToMarkdown(content)
        } else {
            content
        }

        // Convert single newlines to Markdown hard line breaks ("  \n") so each typed line appears on its own line
        rawText = rawText.replace(Regex("(?<!\n)\n(?!\n)"), "  \n")

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

    /**
     * Toggles a checklist item at lineIndex if present. Returns updated markdown string, or null if line is not a checklist item.
     */
    @JvmStatic
    fun toggleChecklistItemAtLine(content: String?, lineIndex: Int): String? {
        if (content.isNullOrEmpty() || lineIndex < 0) return null
        val rawText = if (isHtmlContent(content)) convertHtmlToMarkdown(content) else content
        val lines = rawText.split("\n").toMutableList()
        if (lineIndex >= lines.size) return null

        val line = lines[lineIndex]
        val trimmed = line.trimStart()
        val leading = line.takeWhile { it == ' ' || it == '\t' }

        val updatedLine = when {
            trimmed.startsWith("- [ ] ") -> leading + "- [x] " + trimmed.substring(6)
            trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> leading + "- [ ] " + trimmed.substring(6)
            trimmed.startsWith("* [ ] ") -> leading + "* [x] " + trimmed.substring(6)
            trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> leading + "* [ ] " + trimmed.substring(6)
            trimmed.startsWith("[ ] ") -> leading + "[x] " + trimmed.substring(4)
            trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ") -> leading + "[ ] " + trimmed.substring(4)
            else -> null
        }

        if (updatedLine != null) {
            lines[lineIndex] = updatedLine
            return lines.joinToString("\n")
        }
        return null
    }

    /**
     * Converts Markdown content into a rich Spanned CharSequence for Android RemoteViews / Widgets.
     */
    @JvmStatic
    fun renderMarkdownForWidget(markdown: String?): CharSequence {
        if (markdown.isNullOrEmpty()) {
            return ""
        }

        var html = if (isHtmlContent(markdown)) {
            convertHtmlToMarkdown(markdown)
        } else {
            markdown
        }

        // Convert H2 headings: "## Heading" -> "<big><b>Heading</b></big>"
        html = html.replace(Regex("(?m)^##\\s+(.*)$"), "<big><b>$1</b></big><br/>")

        // Convert H1 headings: "# Heading" -> "<big><big><b>Heading</b></big></big>"
        html = html.replace(Regex("(?m)^#\\s+(.*)$"), "<big><big><b>$1</b></big></big><br/>")

        // Convert Bold + Italic: "***text***" -> "<b><i>text</i></b>"
        html = html.replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "<b><i>$1</i></b>")

        // Convert Bold: "**text**" -> "<b>text</b>"
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")

        // Convert Italic: "*text*" -> "<i>text</i>"
        html = html.replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")

        // Convert Checkboxes: "- [x]" / "- [X]" -> "☑ ", "- [ ]" -> "☐ "
        html = html.replace(Regex("(?m)^-\\s*\\[[xX]\\]\\s*"), "☑ ")
        html = html.replace(Regex("(?m)^-\\s*\\[\\s*\\]\\s*"), "☐ ")

        // Convert List items: "- " or "* " -> "• "
        html = html.replace(Regex("(?m)^[\\-\\*]\\s+"), "• ")

        // Convert single newlines to <br/>
        html = html.replace("\n", "<br/>")

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(html)
        }
    }
}

import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.graphics.Typeface

fun main() {
    val text = "Hemanth software engineer "
    val editable = SpannableStringBuilder(text)
    
    // software (8..16) -> BOLD
    editable.setSpan(StyleSpan(Typeface.BOLD), 8, 17, 33)
    // engineer (17..25) -> BOLD_ITALIC
    editable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 17, 26, 33)

    val spans = editable.getSpans(0, editable.length, StyleSpan::class.java)
    val styles = IntArray(editable.length)
    for (span in spans) {
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        for (i in start until end) {
            if (span.style == Typeface.BOLD) styles[i] = styles[i] or 1
            else if (span.style == Typeface.ITALIC) styles[i] = styles[i] or 2
            else if (span.style == Typeface.BOLD_ITALIC) styles[i] = styles[i] or 3
        }
    }

    val sb = StringBuilder()
    var currentStyle = 0
    var chunkStart = 0

    for (i in 0..editable.length) {
        val style = if (i < editable.length) styles[i] else 0
        if (style != currentStyle || i == editable.length) {
            if (chunkStart < i) {
                val chunk = editable.substring(chunkStart, i)
                val leadingSpaces = chunk.takeWhile { it.isWhitespace() }
                val trailingSpaces = chunk.takeLastWhile { it.isWhitespace() }
                val trimmedChunk = chunk.trim()

                sb.append(leadingSpaces)
                if (trimmedChunk.isNotEmpty()) {
                    val prefix = when (currentStyle) {
                        1 -> "**"
                        2 -> "*"
                        3 -> "***"
                        else -> ""
                    }
                    sb.append(prefix).append(trimmedChunk).append(prefix)
                }
                sb.append(trailingSpaces)
            }
            currentStyle = style
            chunkStart = i
        }
    }
    
    println("Generated Markdown: " + sb.toString())
}

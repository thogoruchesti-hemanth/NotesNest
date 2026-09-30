import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.graphics.Typeface

fun main() {
    val text = "Hemanth software engineer"
    val editable = SpannableStringBuilder(text)
    
    // software (8..16) -> BOLD
    editable.setSpan(StyleSpan(Typeface.BOLD), 8, 16, 33)
    // engineer (17..25) -> BOLD_ITALIC
    editable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 17, 25, 33)

    val sb = StringBuilder()
    val styleSpans = editable.getSpans(0, editable.length, StyleSpan::class.java)

    for (i in 0..editable.length) {
        val endingSpans = styleSpans.filter { editable.getSpanEnd(it) == i }
        for (span in endingSpans.sortedByDescending { editable.getSpanEnd(it) - editable.getSpanStart(it) }) {
            when (span.style) {
                Typeface.BOLD -> sb.append("**")
                Typeface.ITALIC -> sb.append("*")
                Typeface.BOLD_ITALIC -> sb.append("***")
            }
        }

        val startingSpans = styleSpans.filter { editable.getSpanStart(it) == i }
        for (span in startingSpans.sortedByDescending { editable.getSpanEnd(it) - editable.getSpanStart(it) }) {
            when (span.style) {
                Typeface.BOLD -> sb.append("**")
                Typeface.ITALIC -> sb.append("*")
                Typeface.BOLD_ITALIC -> sb.append("***")
            }
        }

        if (i < editable.length) {
            sb.append(editable[i])
        }
    }
    
    println("Generated Markdown: " + sb.toString())
}

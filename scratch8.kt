import android.text.SpannableStringBuilder
import android.text.Spannable
import android.text.style.StyleSpan
import android.graphics.Typeface

fun main() {
    val rawText = "engineer"
    val spannable = SpannableStringBuilder(rawText)
    spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, 8, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    
    // Simulate toggleBold() removing bold from 0..8
    val spans = spannable.getSpans(0, 8, StyleSpan::class.java)
    for (span in spans) {
        if (span.style == Typeface.BOLD_ITALIC) {
            val sStart = spannable.getSpanStart(span)
            val sEnd = spannable.getSpanEnd(span)
            val flags = spannable.getSpanFlags(span)
            spannable.removeSpan(span)
            spannable.setSpan(StyleSpan(Typeface.ITALIC), sStart, sEnd, flags)
        }
    }
}

import android.text.SpannableStringBuilder  
import android.text.Spannable  
import android.text.style.StyleSpan  
import android.graphics.Typeface  
fun main() {  
    val rawText = \" "engineer\  
    val editable = SpannableStringBuilder(rawText)  
    editable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, 8, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)  
    val sb = StringBuilder()  
    val styleSpans = editable.getSpans(0, editable.length, StyleSpan::class.java)  
    for (i in 0..editable.length) {  
        val endingSpans = styleSpans.filter { editable.getSpanEnd(it) == i }  
        for (span in endingSpans.sortedBy { editable.getSpanEnd(it) - editable.getSpanStart(it) }) {  
            when (span.style) {  
                Typeface.BOLD -> sb.append(\**\)  
                Typeface.ITALIC -> sb.append(\*\)  
                Typeface.BOLD_ITALIC -> sb.append(\***\)  
            }  
        }  
        val startingSpans = styleSpans.filter { editable.getSpanStart(it) == i }  
        for (span in startingSpans.sortedByDescending { editable.getSpanEnd(it) - editable.getSpanStart(it) }) {  
            when (span.style) {  
                Typeface.BOLD -> sb.append(\**\)  
                Typeface.ITALIC -> sb.append(\*\)  
                Typeface.BOLD_ITALIC -> sb.append(\***\)  
            }  
        }  
        if (i < editable.length) {  
            sb.append(editable[i])  
        }  
    }  
    println(sb.toString())  
}  

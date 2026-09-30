import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.graphics.Typeface

fun main() {
    val rawText = "Hemanth **software** ***engineer***"
    val spannable = SpannableStringBuilder(rawText)

    // Parse ***bold italic***
    val boldItalicRegex = Regex("\\*\\*\\*(.*?)\\*\\*\\*")
    var match = boldItalicRegex.find(spannable)
    while (match != null) {
        val start = match.range.first
        val end = match.range.last + 1
        val innerText = match.groupValues[1]
        spannable.replace(start, end, innerText)
        spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, start + innerText.length, 33)
        match = boldItalicRegex.find(spannable, start + innerText.length)
    }

    // Parse **bold**
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
    match = boldRegex.find(spannable)
    while (match != null) {
        val start = match.range.first
        val end = match.range.last + 1
        val innerText = match.groupValues[1]
        spannable.replace(start, end, innerText)
        spannable.setSpan(StyleSpan(Typeface.BOLD), start, start + innerText.length, 33)
        match = boldRegex.find(spannable, start + innerText.length)
    }

    // Parse *italic*
    val italicRegex = Regex("\\*(.*?)\\*")
    match = italicRegex.find(spannable)
    while (match != null) {
        val start = match.range.first
        val end = match.range.last + 1
        val innerText = match.groupValues[1]
        spannable.replace(start, end, innerText)
        spannable.setSpan(StyleSpan(Typeface.ITALIC), start, start + innerText.length, 33)
        match = italicRegex.find(spannable, start + innerText.length)
    }
    
    println(spannable.toString())
}

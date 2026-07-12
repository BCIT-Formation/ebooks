package com.ebooks.reader.util

/**
 * Converts reader chapter HTML to plain text suitable for text-to-speech or
 * excerpt sharing. Pure Kotlin (no android.text.Html) so it is unit-testable
 * on the JVM.
 */
fun htmlToPlainText(html: String): String {
    var text = html
    // Drop non-content blocks entirely (injected reader CSS lives in <style>)
    text = text.replace(Regex("(?is)<(style|script|head|title)[^>]*>.*?</\\1>"), " ")
    text = text.replace(Regex("(?is)<!--.*?-->"), " ")
    // Block-level boundaries become line breaks so sentences don't run together
    text = text.replace(Regex("(?i)<(br|/p|/div|/h[1-6]|/li|/tr|/blockquote|/section|/article)[^>]*>"), "\n")
    // Strip all remaining tags
    text = text.replace(Regex("<[^>]+>"), " ")
    // Decode numeric entities (&#8217; etc.)
    text = text.replace(Regex("&#x?([0-9a-fA-F]+);")) { match ->
        val raw = match.groupValues[1]
        val code = if (match.value.startsWith("&#x", ignoreCase = true)) {
            raw.toIntOrNull(16)
        } else {
            raw.toIntOrNull()
        }
        code?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: " "
    }
    // Decode the named entities that matter for speech
    text = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
    // Collapse runs of spaces, then runs of blank lines
    text = text.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
    text = text.replace(Regex(" ?\\n ?"), "\n")
    text = text.replace(Regex("\\n{3,}"), "\n\n")
    return text.trim()
}

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

private val IMG_TAG_REGEX = Regex("(?is)<img\\b[^>]*/?>")
private val WIDTH_ATTR_REGEX = Regex("(?i)\\bwidth\\s*=\\s*[\"']?\\s*(\\d+)")
private val HEIGHT_ATTR_REGEX = Regex("(?i)\\bheight\\s*=\\s*[\"']?\\s*(\\d+)")
private val STYLE_WIDTH_REGEX = Regex("(?i)width\\s*:\\s*(\\d+)px")
private val STYLE_HEIGHT_REGEX = Regex("(?i)height\\s*:\\s*(\\d+)px")

/** src/class/alt fragments that mark an image as decorative rather than content. */
private val DECORATIVE_MARKERS = listOf(
    "emoji", "smilie", "smiley", "sticker", "gravatar", "avatar", "badge",
    "tracking-pixel", "pixel.gif", "spacer.gif", "blank.gif", "beacon", "stat-counter"
)

/**
 * Drops <img> tags too small to be real content — emoji glyphs rendered as
 * images, feed-footer tracking pixels, social share icons — so RSS articles
 * aren't cluttered by decoration alongside real photos/illustrations. Images
 * without a detectable size (the common case for real content images) are
 * always kept.
 */
fun stripTinyImages(html: String, minDimensionPx: Int = 40): String {
    if (html.isBlank()) return html
    return IMG_TAG_REGEX.replace(html) { match ->
        if (isDecorativeImage(match.value, minDimensionPx)) "" else match.value
    }
}

private fun isDecorativeImage(imgTag: String, minDimensionPx: Int): Boolean {
    val width = dimension(imgTag, WIDTH_ATTR_REGEX, STYLE_WIDTH_REGEX)
    val height = dimension(imgTag, HEIGHT_ATTR_REGEX, STYLE_HEIGHT_REGEX)
    if (width != null && width <= minDimensionPx) return true
    if (height != null && height <= minDimensionPx) return true

    val haystack = imgTag.lowercase()
    if (DECORATIVE_MARKERS.any { haystack.contains(it) }) return true

    val alt = attrValue(imgTag, "alt")
    return !alt.isNullOrBlank() && isEmojiOnly(alt)
}

private fun dimension(tag: String, attrRegex: Regex, styleRegex: Regex): Int? {
    attrRegex.find(tag)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    val style = attrValue(tag, "style") ?: return null
    return styleRegex.find(style)?.groupValues?.get(1)?.toIntOrNull()
}

private fun attrValue(tag: String, name: String): String? {
    val match = Regex("(?i)\\b$name\\s*=\\s*\"([^\"]*)\"|\\b$name\\s*=\\s*'([^']*)'").find(tag) ?: return null
    return match.groupValues[1].ifEmpty { match.groupValues[2] }
}

/** True when [text] is short and made up entirely of emoji/symbol glyphs, e.g. an alt-text emoji. */
private fun isEmojiOnly(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 8) return false
    return trimmed.codePoints().allMatch { cp ->
        !Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp)
    }
}

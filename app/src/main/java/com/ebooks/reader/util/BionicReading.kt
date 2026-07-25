package com.ebooks.reader.util

/** Raw-text containers whose contents must never be word-wrapped. */
private val RAW_TEXT_TAGS = setOf("head", "style", "script", "title")

/**
 * Converts chapter HTML to "Bionic Reading" HTML: the leading ~40% of each
 * word's characters are wrapped in `<b>` so the eye can anchor on the bolded
 * fixation points. Pure Kotlin (unit-testable on the JVM) and markup-aware:
 *
 *  - tags (`<…>`) are copied through untouched,
 *  - the contents of `<head>`, `<style>`, `<script>` and `<title>` blocks are
 *    never altered (the injected reader CSS lives in `<style>`),
 *  - character entities (`&amp;` …) are kept intact and act as word boundaries.
 */
fun bionicHtml(html: String): String {
    val sb = StringBuilder(html.length + html.length / 4)
    var i = 0
    val n = html.length
    while (i < n) {
        val c = html[i]
        when {
            c == '<' -> {
                val end = html.indexOf('>', i)
                if (end == -1) {
                    sb.append(html, i, n)
                    break
                }
                sb.append(html, i, end + 1)
                val tag = openingTagName(html, i)
                i = end + 1
                if (tag != null && tag in RAW_TEXT_TAGS) {
                    // Copy verbatim until the matching close tag.
                    val close = html.indexOf("</$tag", i, ignoreCase = true)
                    val stop = if (close == -1) n else close
                    sb.append(html, i, stop)
                    i = stop
                }
            }
            c == '&' -> {
                // Copy a character entity through as an unbreakable unit.
                val semi = html.indexOf(';', i)
                val stop = if (semi != -1 && semi - i <= 10) semi + 1 else i + 1
                sb.append(html, i, stop)
                i = stop
            }
            c.isLetter() -> {
                var j = i
                while (j < n && (html[j].isLetter() || html[j] == '\'' || html[j] == '\u2019')) j++
                appendBionicWord(sb, html, i, j)
                i = j
            }
            else -> {
                sb.append(c)
                i++
            }
        }
    }
    return sb.toString()
}

/** Lower-cased name of the tag starting at [start] (pointing at `<`), or null for closing tags. */
private fun openingTagName(html: String, start: Int): String? {
    var i = start + 1
    if (i < html.length && html[i] == '/') return null
    val sb = StringBuilder()
    while (i < html.length && html[i].isLetter()) {
        sb.append(html[i].lowercaseChar())
        i++
    }
    return sb.toString().ifEmpty { null }
}

private fun appendBionicWord(sb: StringBuilder, html: String, start: Int, end: Int) {
    val len = end - start
    // ceil(0.4 * len), with short words anchoring on their first character.
    val bold = if (len <= 3) 1 else (len * 2 + 4) / 5
    sb.append("<b>").append(html, start, start + bold).append("</b>").append(html, start + bold, end)
}

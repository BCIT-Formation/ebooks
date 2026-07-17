package com.ebooks.reader.util

/**
 * Bionic Reading algorithm implementation
 * Automatically bolds the first part of words (>3 letters) to improve reading speed
 */
object BionicReading {

    private val WORD_PATTERN = Regex("\\s+")
    private val PUNCTUATION_CHARS = charArrayOf(',', '.', '!', '?', ':', ';', '"', '\'')

    /**
     * Convert plain text to Bionic Reading format (HTML with <b> tags)
     * Only words with more than 3 characters get the treatment
     */
    fun toHtml(text: String): String {
        val builder = StringBuilder()
        val words = text.split(WORD_PATTERN)

        for ((index, word) in words.withIndex()) {
            if (index > 0) builder.append(" ")
            builder.append(procesWord(word))
        }

        return builder.toString()
    }

    /**
     * Convert plain text to Bionic Reading format (annotated text with spans)
     * Returns a list of (text, isBold) pairs
     */
    fun toAnnotatedText(text: String): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        val words = text.split(WORD_PATTERN)

        for ((index, word) in words.withIndex()) {
            if (index > 0) {
                segments.add(TextSegment(" ", isBold = false))
            }
            segments.addAll(processWordToSegments(word))
        }

        return segments
    }

    /**
     * Parsed word structure: bold part, normal part, and trailing punctuation
     */
    private data class WordParts(
        val bold: String,
        val normal: String,
        val punctuation: String,
        val isProcessed: Boolean
    )

    private fun parseWord(word: String): WordParts {
        // Find where the actual word ends (no punctuation)
        val wordEnd = word.indexOfAny(PUNCTUATION_CHARS)
            .let { if (it > 0) it else word.length }

        val actualWord = word.substring(0, wordEnd)
        val punctuation = word.substring(wordEnd)

        if (actualWord.length <= 3) return WordParts("", "", "", isProcessed = false)

        val boldLength = (actualWord.length + 1) / 2
        return WordParts(
            bold = actualWord.substring(0, boldLength),
            normal = actualWord.substring(boldLength),
            punctuation = punctuation,
            isProcessed = true
        )
    }

    /**
     * Process a single word for Bionic Reading
     * Returns HTML string with <b> tags
     */
    private fun procesWord(word: String): String {
        val parts = parseWord(word)
        return if (parts.isProcessed) {
            "<b>${parts.bold}</b>${parts.normal}${parts.punctuation}"
        } else {
            word
        }
    }

    /**
     * Process a single word into text segments
     */
    private fun processWordToSegments(word: String): List<TextSegment> {
        val parts = parseWord(word)
        if (!parts.isProcessed) {
            return listOf(TextSegment(word, isBold = false))
        }

        val segments = mutableListOf<TextSegment>()
        segments.add(TextSegment(parts.bold, isBold = true))
        if (parts.normal.isNotEmpty()) {
            segments.add(TextSegment(parts.normal, isBold = false))
        }
        if (parts.punctuation.isNotEmpty()) {
            segments.add(TextSegment(parts.punctuation, isBold = false))
        }
        return segments
    }

    /**
     * Process entire HTML content (EPUB/FB2 WebView)
     * Wraps word beginnings in <b> tags while preserving existing markup
     */
    fun processHtmlContent(html: String): String {
        // Simple regex-based approach: find words outside of tags
        val result = StringBuilder()
        val regex = Regex("(?<=>)([^<]+)(?=<|$)")

        var lastEnd = 0
        regex.findAll(html).forEach { match ->
            val textStart = match.range.first
            val textEnd = match.range.last + 1

            // Append content before this text
            result.append(html.substring(lastEnd, textStart))

            // Process the text content
            val text = match.groupValues[1]
            result.append(toHtml(text))

            lastEnd = textEnd
        }

        // Append remaining content
        if (lastEnd < html.length) {
            result.append(html.substring(lastEnd))
        }

        return result.toString()
    }

    /**
     * JavaScript code to apply Bionic Reading to WebView content
     * Can be injected into the WebView for dynamic processing
     */
    fun getJavaScriptCode(): String {
        return """
            (function() {
                function bionicReading(text) {
                    return text.split(/\\s+/).map(word => {
                        if (word.length <= 3) return word;

                        // Handle punctuation
                        let match = word.match(/^([^.,!?:;"'""]+)(.*)$/);
                        if (!match) return word;

                        let actualWord = match[1];
                        let punctuation = match[2] || '';

                        if (actualWord.length <= 3) return word;

                        let boldLength = Math.ceil(actualWord.length / 2);
                        let bold = actualWord.substring(0, boldLength);
                        let normal = actualWord.substring(boldLength);

                        return '<b>' + bold + '</b>' + normal + punctuation;
                    }).join(' ');
                }

                function processNode(node) {
                    if (node.nodeType === 3) { // TEXT_NODE
                        let html = bionicReading(node.textContent);
                        let span = document.createElement('span');
                        span.innerHTML = html;
                        node.parentNode.replaceChild(span, node);
                    } else if (node.nodeType === 1 && !['SCRIPT', 'STYLE'].includes(node.nodeName)) {
                        for (let child of node.childNodes) {
                            processNode(child);
                        }
                    }
                }

                processNode(document.body);
            })();
        """.trimIndent()
    }
}

data class TextSegment(
    val text: String,
    val isBold: Boolean = false
)

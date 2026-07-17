package com.ebooks.reader.util

object AnnotationMarkdownBuilder {

    data class AnnotationItem(
        val textContent: String?,
        val metadata: String?
    )

    fun buildMarkdown(items: List<AnnotationItem>, title: String, includeHeader: Boolean = false): String {
        return buildString {
            if (includeHeader) {
                append("# ").append(title).append("\n\n")
            }

            items.forEach { item ->
                if (!item.textContent.isNullOrBlank()) {
                    append("> ").append(item.textContent.replace("\n", "\n> ")).append("\n\n")
                }
                if (!item.metadata.isNullOrBlank()) {
                    append("**Note:** ").append(item.metadata).append("\n\n")
                }
                append("---\n\n")
            }
        }
    }
}

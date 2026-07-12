package com.ebooks.reader.data.sync

import org.json.JSONArray
import org.json.JSONObject

/** Serializes a snapshot to the JSON exchanged via cloud folders / WebDAV. */
fun ProgressSnapshot.toJson(): String {
    val root = JSONObject()
    root.put("version", ProgressSnapshot.VERSION)
    root.put("exportedAt", exportedAt)
    val books = JSONArray()
    entries.forEach { entry ->
        books.put(
            JSONObject()
                .put("title", entry.title)
                .put("author", entry.author)
                .put("chapterIndex", entry.chapterIndex)
                .put("scrollPosition", entry.scrollPosition)
                .put("lastReadAt", entry.lastReadAt)
                .put("readingStatus", entry.readingStatus)
        )
    }
    root.put("books", books)
    return root.toString(2)
}

/** Parses a snapshot; returns null when the payload is malformed or unsupported. */
fun parseProgressSnapshot(json: String): ProgressSnapshot? = runCatching {
    val root = JSONObject(json)
    if (root.optInt("version", -1) > ProgressSnapshot.VERSION) return null
    val books = root.optJSONArray("books") ?: return null
    val entries = buildList {
        for (i in 0 until books.length()) {
            val item = books.getJSONObject(i)
            add(
                ProgressEntry(
                    title = item.getString("title"),
                    author = item.optString("author"),
                    chapterIndex = item.optInt("chapterIndex", 0),
                    scrollPosition = item.optInt("scrollPosition", 0),
                    lastReadAt = item.optLong("lastReadAt", 0L),
                    readingStatus = item.optString("readingStatus", "READING")
                )
            )
        }
    }
    ProgressSnapshot(exportedAt = root.optLong("exportedAt", 0L), entries = entries)
}.getOrNull()

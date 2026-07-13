package com.ebooks.reader.data.dict

import com.ebooks.reader.data.net.readTextLimited
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WordMeaning(val partOfSpeech: String, val definitions: List<String>)
data class WordDefinition(val word: String, val phonetic: String?, val meanings: List<WordMeaning>)

/**
 * Looks up English word definitions from the free, keyless dictionaryapi.dev
 * over HTTPS (ADR-006: user-initiated, encrypted, no telemetry). Returns null
 * when the word is unknown or the network fails — callers offer translate/search
 * as a fallback.
 */
class DictionaryClient {

    fun lookup(word: String): WordDefinition? = runCatching {
        val clean = word.trim().lowercase()
        if (clean.isBlank()) return null
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/$encoded"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "EbookReader")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != 200) return null
            val json = connection.inputStream.use { it.readTextLimited(2L * 1024 * 1024) }
            parse(json, clean)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun parse(json: String, fallbackWord: String): WordDefinition? {
        val entries = JSONArray(json)
        if (entries.length() == 0) return null
        val first = entries.getJSONObject(0)
        val word = first.optString("word", fallbackWord)
        val phonetic = first.optString("phonetic").takeIf { it.isNotBlank() }
        val meanings = buildList {
            val meaningsArr = first.optJSONArray("meanings") ?: return@buildList
            for (i in 0 until meaningsArr.length()) {
                val m = meaningsArr.getJSONObject(i)
                val pos = m.optString("partOfSpeech")
                val defsArr = m.optJSONArray("definitions")
                val defs = buildList {
                    if (defsArr != null) for (j in 0 until minOf(defsArr.length(), 3)) {
                        defsArr.getJSONObject(j).optString("definition").takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                if (defs.isNotEmpty()) add(WordMeaning(pos, defs))
            }
        }
        return if (meanings.isEmpty()) null else WordDefinition(word, phonetic, meanings)
    }
}

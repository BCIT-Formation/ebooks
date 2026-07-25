package com.ebooks.reader.data.dict

import android.content.Context

/**
 * Word lookup used by the readers: user-imported offline StarDict
 * dictionaries first, then the online dictionaryapi.dev fallback
 * ([DictionaryClient], ADR-006). Blocking — call from Dispatchers.IO.
 */
class DictionaryLookup(context: Context) {

    private val starDict = StarDictManager(context)
    private val online = DictionaryClient()

    fun lookup(word: String): WordDefinition? {
        starDict.lookup(word)?.let { offline ->
            val lines = offline.definition.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(MAX_OFFLINE_LINES)
                .toList()
            if (lines.isNotEmpty()) {
                // Reuse the existing sheet layout: the dictionary's name takes
                // the part-of-speech slot, each definition line a bullet.
                return WordDefinition(word, null, listOf(WordMeaning(offline.dictionaryName, lines)))
            }
        }
        return online.lookup(word)
    }

    private companion object {
        const val MAX_OFFLINE_LINES = 8
    }
}

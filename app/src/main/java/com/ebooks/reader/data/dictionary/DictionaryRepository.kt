package com.ebooks.reader.data.dictionary

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Repository for managing offline dictionaries
 * Handles loading, caching, and lookups
 */
class DictionaryRepository(private val context: Context) {

    private val parser = StarDictParser(context)
    private val loadedDictionaries = mutableMapOf<String, StarDictDictionary>()

    private val _isLookingUp = MutableStateFlow(false)
    val isLookingUp: StateFlow<Boolean> = _isLookingUp

    private val _lastLookupResult = MutableStateFlow<DictionaryLookupResult?>(null)
    val lastLookupResult: StateFlow<DictionaryLookupResult?> = _lastLookupResult

    suspend fun loadDictionary(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dictionary = parser.loadDictionary(uri) ?: return@withContext false
                loadedDictionaries[dictionary.name] = dictionary
                true
            } catch (e: Exception) {
                android.util.Log.e("DictionaryRepository", "Error loading dictionary", e)
                false
            }
        }
    }

    suspend fun lookup(word: String, dictionaryName: String? = null): DictionaryLookupResult {
        return withContext(Dispatchers.IO) {
            _isLookingUp.value = true
            try {
                val dictionary = if (dictionaryName != null) {
                    loadedDictionaries[dictionaryName]
                } else {
                    // Try all loaded dictionaries
                    loadedDictionaries.values.firstOrNull()
                }

                if (dictionary == null) {
                    _lastLookupResult.value = DictionaryLookupResult.Error("No dictionary loaded")
                    return@withContext DictionaryLookupResult.Error("No dictionary loaded")
                }

                val definition = dictionary.lookup(word)

                val result = if (definition != null) {
                    DictionaryLookupResult.Success(
                        word = word,
                        definition = definition,
                        dictionaryName = dictionary.name
                    )
                } else {
                    DictionaryLookupResult.NotFound(word)
                }

                _lastLookupResult.value = result
                result
            } catch (e: Exception) {
                val error = DictionaryLookupResult.Error(e.message ?: "Unknown error")
                _lastLookupResult.value = error
                error
            } finally {
                _isLookingUp.value = false
            }
        }
    }

    fun getLoadedDictionaries(): List<StarDictDictionary> {
        return loadedDictionaries.values.toList()
    }

    fun unloadDictionary(dictionaryName: String) {
        loadedDictionaries.remove(dictionaryName)
    }

    fun clearAll() {
        loadedDictionaries.clear()
        _lastLookupResult.value = null
    }

    companion object {
        @Volatile
        private var instance: DictionaryRepository? = null

        fun getInstance(context: Context): DictionaryRepository {
            return instance ?: synchronized(this) {
                instance ?: DictionaryRepository(context.applicationContext)
                    .also { instance = it }
            }
        }
    }
}

sealed class DictionaryLookupResult {
    data class Success(
        val word: String,
        val definition: String,
        val dictionaryName: String
    ) : DictionaryLookupResult()

    data class NotFound(val word: String) : DictionaryLookupResult()
    data class Error(val message: String) : DictionaryLookupResult()
}

package com.ebooks.reader.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private const val LAST_UTTERANCE_ID = "tts-last"
// Stay well under TextToSpeech.getMaxSpeechInputLength() (4000 on AOSP)
private const val MAX_CHUNK_CHARS = 3500

/**
 * Thin state holder around [TextToSpeech] for reading chapter text aloud.
 * The engine is created lazily on the first [speak] call and must be
 * released via [release] (handled by [rememberTtsSpeaker]).
 */
class TtsSpeaker(private val context: Context) {

    var isSpeaking by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null
    private var engineReady = false
    private var initFailed = false
    /** Text queued while the engine is still initializing. */
    private var pendingText: String? = null
    private var pendingLanguage: String? = null

    fun toggle(text: String, languageTag: String? = null) {
        if (isSpeaking) stop() else speak(text, languageTag)
    }

    fun speak(text: String, languageTag: String? = null) {
        if (text.isBlank() || initFailed) return
        val engine = tts
        if (engine != null && engineReady) {
            speakNow(engine, text, languageTag)
            return
        }
        pendingText = text
        pendingLanguage = languageTag
        if (engine == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    engineReady = true
                    val queued = pendingText
                    pendingText = null
                    tts?.let { t ->
                        installProgressListener(t)
                        if (queued != null) speakNow(t, queued, pendingLanguage)
                    }
                } else {
                    initFailed = true
                    tts = null
                }
            }
        }
    }

    fun stop() {
        pendingText = null
        tts?.stop()
        isSpeaking = false
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        engineReady = false
    }

    private fun installProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == LAST_UTTERANCE_ID) isSpeaking = false
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
            }
        })
    }

    private fun speakNow(engine: TextToSpeech, text: String, languageTag: String?) {
        languageTag
            ?.takeIf { it.isNotBlank() }
            ?.let { tag -> runCatching { engine.setLanguage(Locale.forLanguageTag(tag)) } }
        val chunks = chunkText(text)
        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val id = if (index == chunks.lastIndex) LAST_UTTERANCE_ID else "tts-$index"
            engine.speak(chunk, queueMode, null, id)
        }
        isSpeaking = true
    }

    /** Splits long text at sentence/paragraph boundaries so every piece fits the TTS input limit. */
    private fun chunkText(text: String, maxChars: Int = MAX_CHUNK_CHARS): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxChars).coerceAtMost(text.length)
            if (end < text.length) {
                val breakAt = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ' '), end - 1)
                if (breakAt > start) end = breakAt + 1
            }
            chunks.add(text.substring(start, end))
            start = end
        }
        return chunks
    }
}

/** A [TtsSpeaker] scoped to the composition — the engine is shut down on dispose. */
@Composable
fun rememberTtsSpeaker(): TtsSpeaker {
    val context = LocalContext.current
    val speaker = remember(context) { TtsSpeaker(context) }
    DisposableEffect(speaker) {
        onDispose { speaker.release() }
    }
    return speaker
}

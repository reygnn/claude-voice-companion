package com.claudecompanion.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SpeakingState {
    data object Silent : SpeakingState
    data object Speaking : SpeakingState
    data object Paused : SpeakingState
}

data class VoiceConfig(
    val engine: TtsEngine = TtsEngine.ANDROID_LOCAL,
    val voiceId: String = "",
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f
)

enum class TtsEngine { ANDROID_LOCAL, ELEVENLABS, GOOGLE_CLOUD }

interface VoiceOutputManager {
    val state: StateFlow<SpeakingState>
    fun speakSentence(text: String)
    suspend fun speakStreaming(textFlow: Flow<String>)
    fun stop()
    fun setVoice(config: VoiceConfig)
}

@Singleton
class VoiceOutputManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : VoiceOutputManager {

    private val _state = MutableStateFlow<SpeakingState>(SpeakingState.Silent)
    override val state: StateFlow<SpeakingState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val sentenceQueue = ArrayDeque<String>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US

                // Try warm male voices in order of preference
                val preferredVoices = listOf(
                    "en-us-x-tpd-network",  // natural male neural voice
                    "en-us-x-sfg-network",  // warm deep male
                    "en-us-x-iom-network",  // clear male
                    "en-us-x-tpd-local",    // offline fallback
                    "en-us-x-sfg-local",
                    "en-us-x-iom-local"
                )

                val availableVoices = tts?.voices.orEmpty()
                availableVoices.forEach {
                    android.util.Log.d("TTS", "Available: ${it.name} (${it.locale})")
                }

                val selected = preferredVoices.firstNotNullOfOrNull { preferred ->
                    availableVoices.firstOrNull { it.name == preferred }
                }

                if (selected != null) {
                    tts?.voice = selected
                    android.util.Log.d("TTS", "Selected voice: ${selected.name}")
                } else {
                    android.util.Log.w("TTS", "No preferred voice found, using default")
                }

                tts?.setPitch(0.9f)
                tts?.setSpeechRate(0.95f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = SpeakingState.Speaking
                    }

                    override fun onDone(utteranceId: String?) {
                        val next = sentenceQueue.removeFirstOrNull()
                        if (next != null) {
                            speakInternal(next)
                        } else {
                            _state.value = SpeakingState.Silent
                        }
                    }

                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?) {
                        _state.value = SpeakingState.Silent
                    }
                })
                isReady = true
            }
        }
    }

    override fun speakSentence(text: String) {
        if (!isReady) return
        if (_state.value == SpeakingState.Speaking) {
            sentenceQueue.addLast(text)
        } else {
            speakInternal(text)
        }
    }

    override suspend fun speakStreaming(textFlow: Flow<String>) {
        val buffer = StringBuilder()
        val sentenceEnders = charArrayOf('.', '!', '?', '\n')

        textFlow.collect { delta ->
            buffer.append(delta)
            val text = buffer.toString()
            val lastEnder = text.indexOfLast { it in sentenceEnders }
            if (lastEnder >= 0) {
                val sentence = text.substring(0, lastEnder + 1).trim()
                if (sentence.isNotBlank()) speakSentence(sentence)
                buffer.clear()
                if (lastEnder + 1 < text.length) {
                    buffer.append(text.substring(lastEnder + 1))
                }
            }
        }
        // Speak remaining buffer
        val remaining = buffer.toString().trim()
        if (remaining.isNotBlank()) speakSentence(remaining)
    }

    override fun stop() {
        sentenceQueue.clear()
        tts?.stop()
        _state.value = SpeakingState.Silent
    }

    override fun setVoice(config: VoiceConfig) {
        tts?.setSpeechRate(config.speed)
        tts?.setPitch(config.pitch)
    }

    private fun speakInternal(text: String) {
        _state.value = SpeakingState.Speaking
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
}

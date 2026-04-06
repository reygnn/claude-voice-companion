package com.claudecompanion.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ListeningState {
    data object Idle : ListeningState
    data object Listening : ListeningState
    data object Processing : ListeningState
    data class Error(val message: String) : ListeningState
}

interface VoiceInputManager {
    val state: StateFlow<ListeningState>
    val partialResults: SharedFlow<String>
    val finalResult: SharedFlow<String>
    var holdMode: Boolean
    fun startListening(language: Locale = Locale.US)
    fun stopListening()
    fun cancel()
    fun releaseHold()
}

@Singleton
class VoiceInputManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : VoiceInputManager {

    private val _state = MutableStateFlow<ListeningState>(ListeningState.Idle)
    override val state: StateFlow<ListeningState> = _state.asStateFlow()

    private val _partialResults = MutableSharedFlow<String>(extraBufferCapacity = 10)
    override val partialResults: SharedFlow<String> = _partialResults

    private val _finalResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val finalResult: SharedFlow<String> = _finalResult

    private var speechRecognizer: SpeechRecognizer? = null
    private var lastIntent: Intent? = null
    private var didFallback = false
    override var holdMode = false
    private val collectedText = StringBuilder()

    override fun startListening(language: Locale) {
        cancel()
        didFallback = false
        collectedText.clear()
        speechRecognizer = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            android.util.Log.d("VoiceInput", "Using ON-DEVICE recognizer")
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            android.util.Log.d("VoiceInput", "Using CLOUD recognizer (on-device not available)")
            SpeechRecognizer.createSpeechRecognizer(context)
        }.apply {
            setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        lastIntent = intent

        _state.value = ListeningState.Listening
        speechRecognizer?.startListening(intent)
    }

    override fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = ListeningState.Processing
    }

    override fun cancel() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        holdMode = false
        collectedText.clear()
        _state.value = ListeningState.Idle
    }

    override fun releaseHold() {
        holdMode = false
        val text = collectedText.toString().trim()
        collectedText.clear()
        if (text.isNotBlank()) {
            android.util.Log.d("VoiceInput", "releaseHold: emitting '$text'")
            _finalResult.tryEmit(text)
        }
        stopListening()
    }

    private fun fallbackToCloud() {
        android.util.Log.w("VoiceInput", "Falling back to cloud recognizer")
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }
        lastIntent?.let { speechRecognizer?.startListening(it) }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            android.util.Log.d("VoiceInput", "onResults: '$text'")
            if (text.isNotBlank()) {
                if (holdMode) {
                    collectedText.append(" ").append(text)
                    _partialResults.tryEmit(collectedText.toString().trim())
                    // Restart listening to keep collecting
                    lastIntent?.let { speechRecognizer?.startListening(it) }
                } else {
                    val final = if (collectedText.isNotBlank()) {
                        collectedText.append(" ").append(text).toString().trim()
                    } else text
                    collectedText.clear()
                    _finalResult.tryEmit(final)
                    _state.value = ListeningState.Idle
                }
            } else {
                if (holdMode) {
                    lastIntent?.let { speechRecognizer?.startListening(it) }
                } else {
                    _state.value = ListeningState.Idle
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                val display = if (collectedText.isNotBlank()) {
                    "${collectedText.toString().trim()} $text"
                } else text
                android.util.Log.d("VoiceInput", "onPartial: '$display'")
                _partialResults.tryEmit(display)
            }
        }

        override fun onError(error: Int) {
            // In hold mode, restart on silence-related errors
            if (holdMode && (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                android.util.Log.d("VoiceInput", "Hold mode: restarting after silence")
                lastIntent?.let { speechRecognizer?.startListening(it) }
                return
            }
            if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED && !didFallback) {
                didFallback = true
                fallbackToCloud()
                return
            }
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
                else -> "Error ($error)"
            }
            android.util.Log.e("VoiceInput", "onError: $error ($msg)")
            _state.value = ListeningState.Error(msg)
        }

        override fun onReadyForSpeech(params: Bundle?) {
            android.util.Log.d("VoiceInput", "Ready for speech")
        }
        override fun onBeginningOfSpeech() {
            android.util.Log.d("VoiceInput", "Speech started")
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            android.util.Log.d("VoiceInput", "Speech ended")
            if (!holdMode) {
                _state.value = ListeningState.Processing
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
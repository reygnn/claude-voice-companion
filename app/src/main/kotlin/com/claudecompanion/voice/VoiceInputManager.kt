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
    fun startListening(language: Locale = Locale.US)
    fun stopListening()
    fun cancel()
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

    override fun startListening(language: Locale) {
        cancel()
        didFallback = false
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
        _state.value = ListeningState.Idle
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
                _finalResult.tryEmit(text)
            }
            _state.value = ListeningState.Idle
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                android.util.Log.d("VoiceInput", "onPartial: '$text'")
                _partialResults.tryEmit(text)
            }
        }

        override fun onError(error: Int) {
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
            _state.value = ListeningState.Processing
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
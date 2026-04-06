package com.claudecompanion.voice

import com.claudecompanion.data.local.ApiKeyStore
import com.claudecompanion.data.remote.AnthropicStreamingClient
import com.claudecompanion.data.remote.StreamEvent
import com.claudecompanion.data.repository.ConversationRepository
import com.claudecompanion.domain.model.DEFAULT_SYSTEM_PROMPT
import com.claudecompanion.domain.model.Message
import com.claudecompanion.domain.model.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AppState {
    data object Idle : AppState
    data object Listening : AppState
    data class Transcribing(val partial: String) : AppState
    data object ClaudeThinking : AppState
    data class ClaudeSpeaking(val text: String) : AppState
    data class Error(val message: String) : AppState
}

@Singleton
class ConversationOrchestrator @Inject constructor(
    private val ear: VoiceInputManager,
    private val voice: VoiceOutputManager,
    private val apiClient: AnthropicStreamingClient,
    private val repository: ConversationRepository,
    private val apiKeyStore: ApiKeyStore
) {
    private val _state = MutableStateFlow<AppState>(AppState.Idle)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private val _responseStream = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private var conversationId: Long? = null
    private val messageHistory = mutableListOf<Message>()
    private var systemPrompt = DEFAULT_SYSTEM_PROMPT
    private var streamJob: Job? = null

    fun start(scope: CoroutineScope) {
        // Listen for final speech results
        scope.launch {
            ear.finalResult.collect { text ->
                onSpeechResult(scope, text)
            }
        }
        // Relay partial results to state
        scope.launch {
            ear.partialResults.collect { partial ->
                if (_state.value is AppState.Listening || _state.value is AppState.Transcribing) {
                    _state.value = AppState.Transcribing(partial)
                }
            }
        }
    }

    suspend fun loadOrCreateConversation(id: Long? = null) {
        if (id != null) {
            conversationId = id
            val messages = repository.getMessages(id)
            messageHistory.clear()
            messageHistory.addAll(messages)
            repository.getConversation(id)?.let { systemPrompt = it.systemPrompt }
        } else {
            val newId = repository.createConversation(systemPrompt)
            conversationId = newId
            messageHistory.clear()
        }
        _currentResponse.value = ""
        _state.value = AppState.Idle
    }

    fun onMicrophoneTapped(scope: CoroutineScope) {
        when (_state.value) {
            is AppState.ClaudeSpeaking -> {
                // Interrupt Claude
                voice.stop()
                streamJob?.cancel()
                startListening()
            }
            is AppState.Listening, is AppState.Transcribing -> {
                ear.stopListening()
            }
            else -> {
                startListening()
            }
        }
    }

    fun cancelListening() {
        ear.cancel()
        _state.value = AppState.Idle
    }

    fun setHoldMode(enabled: Boolean) {
        ear.holdMode = enabled
        if (!enabled) {
            ear.releaseHold()
        }
    }

    private fun startListening() {
        _state.value = AppState.Listening
        ear.startListening(Locale.US)
    }

    private fun onSpeechResult(scope: CoroutineScope, text: String) {
        _state.value = AppState.ClaudeThinking
        _currentResponse.value = ""

        val apiKey = apiKeyStore.get() ?: run {
            _state.value = AppState.Error("No API key set")
            return
        }

        val userMessage = Message(role = Role.USER, content = text)
        messageHistory.add(userMessage)

        scope.launch {
            conversationId?.let { repository.addMessage(it, Role.USER, text) }
        }

        streamJob = scope.launch {
            val responseBuilder = StringBuilder()
            val textDeltas = MutableSharedFlow<String>(extraBufferCapacity = 64)

            // Launch TTS streaming in parallel
            val ttsJob = launch {
                voice.speakStreaming(textDeltas)
            }

            apiClient.streamMessage(apiKey, messageHistory, systemPrompt).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        responseBuilder.append(event.text)
                        _currentResponse.value = responseBuilder.toString()
                        textDeltas.emit(event.text)
                        if (_state.value is AppState.ClaudeThinking) {
                            _state.value = AppState.ClaudeSpeaking(responseBuilder.toString())
                        } else if (_state.value is AppState.ClaudeSpeaking) {
                            _state.value = AppState.ClaudeSpeaking(responseBuilder.toString())
                        }
                    }
                    is StreamEvent.Done -> {
                        val fullResponse = responseBuilder.toString()
                        messageHistory.add(Message(role = Role.ASSISTANT, content = fullResponse))
                        conversationId?.let {
                            repository.addMessage(it, Role.ASSISTANT, fullResponse)
                        }
                    }
                    is StreamEvent.Error -> {
                        _state.value = AppState.Error(event.message)
                    }
                }
            }

            ttsJob.join()
            if (_state.value !is AppState.Error) {
                _state.value = AppState.Idle
            }
        }
    }

    fun dismiss() {
        ear.cancel()
        voice.stop()
        streamJob?.cancel()
        _state.value = AppState.Idle
    }
}
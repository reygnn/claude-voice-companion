package com.claudecompanion.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudecompanion.data.local.ApiKeyStore
import com.claudecompanion.data.repository.ConversationRepository
import com.claudecompanion.domain.model.Message
import com.claudecompanion.voice.AppState
import com.claudecompanion.voice.ConversationOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val orchestrator: ConversationOrchestrator,
    private val repository: ConversationRepository,
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    val appState: StateFlow<AppState> = orchestrator.state
    val currentResponse: StateFlow<String> = orchestrator.currentResponse

    private val _conversationId = MutableStateFlow<Long?>(null)

    val messages: StateFlow<List<Message>> = _conversationId
        .filterNotNull()
        .flatMapLatest { repository.observeMessages(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasApiKey: Boolean get() = !apiKeyStore.get().isNullOrBlank()

    init {
        orchestrator.start(viewModelScope)
        viewModelScope.launch {
            orchestrator.loadOrCreateConversation()
        }
    }

    fun loadConversation(id: Long) {
        viewModelScope.launch {
            orchestrator.loadOrCreateConversation(id)
            _conversationId.value = id
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            orchestrator.loadOrCreateConversation(null)
        }
    }

    fun onMicrophoneTapped() {
        orchestrator.onMicrophoneTapped(viewModelScope)
    }

    fun onCancelListening() {
        orchestrator.cancelListening()
    }

    fun onHoldToggle(enabled: Boolean) {
        orchestrator.setHoldMode(enabled)
    }

    fun dismissError() {
        orchestrator.dismiss()
    }

    override fun onCleared() {
        orchestrator.dismiss()
        super.onCleared()
    }
}
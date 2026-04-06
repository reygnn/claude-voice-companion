package com.claudecompanion.ui.settings

import androidx.lifecycle.ViewModel
import com.claudecompanion.data.local.ApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    private val _apiKey = MutableStateFlow(apiKeyStore.get().orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun updateApiKey(key: String) {
        _apiKey.value = key
        _saved.value = false
    }

    fun saveApiKey() {
        val key = _apiKey.value.trim()
        if (key.isNotBlank()) {
            apiKeyStore.save(key)
            _saved.value = true
        }
    }

    fun clearApiKey() {
        apiKeyStore.clear()
        _apiKey.value = ""
        _saved.value = false
    }
}

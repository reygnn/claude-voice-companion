package com.claudecompanion.ui.conversation

import androidx.lifecycle.viewModelScope
import com.claudecompanion.MainDispatcherRule
import com.claudecompanion.data.local.ApiKeyStore
import com.claudecompanion.data.repository.ConversationRepository
import com.claudecompanion.voice.AppState
import com.claudecompanion.voice.ConversationOrchestrator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val orchestrator = mockk<ConversationOrchestrator>(relaxed = true) {
        every { state } returns MutableStateFlow(AppState.Idle)
        every { currentResponse } returns MutableStateFlow("")
    }
    private val repository = mockk<ConversationRepository>(relaxed = true)
    private val apiKeyStore = mockk<ApiKeyStore>(relaxed = true)

    @Test
    fun `hasApiKey returns true when key exists`() {
        every { apiKeyStore.get() } returns "sk-test"
        val vm = ConversationViewModel(orchestrator, repository, apiKeyStore)
        assertTrue(vm.hasApiKey)
    }

    @Test
    fun `hasApiKey returns false when key is null`() {
        every { apiKeyStore.get() } returns null
        val vm = ConversationViewModel(orchestrator, repository, apiKeyStore)
        assertFalse(vm.hasApiKey)
    }

    @Test
    fun `onMicrophoneTapped delegates to orchestrator`() = runTest {
        val vm = ConversationViewModel(orchestrator, repository, apiKeyStore)
        vm.onMicrophoneTapped()
        verify { orchestrator.onMicrophoneTapped(any()) }
    }

    @Test
    fun `clearing viewmodel dismisses orchestrator`() {
        val vm = ConversationViewModel(orchestrator, repository, apiKeyStore)
        // Simuliert ViewModel.onCleared() intern
        vm.viewModelScope.cancel()
        verify { orchestrator.dismiss() }
    }
}

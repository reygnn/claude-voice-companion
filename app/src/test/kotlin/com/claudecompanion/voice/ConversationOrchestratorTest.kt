package com.claudecompanion.voice

import com.claudecompanion.MainDispatcherRule
import com.claudecompanion.data.local.ApiKeyStore
import com.claudecompanion.data.remote.AnthropicStreamingClient
import com.claudecompanion.data.remote.StreamEvent
import com.claudecompanion.data.repository.ConversationRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationOrchestratorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ear = mockk<VoiceInputManager>(relaxed = true)
    private val voice = mockk<VoiceOutputManager>(relaxed = true)
    private val apiClient = mockk<AnthropicStreamingClient>(relaxed = true)
    private val repository = mockk<ConversationRepository>(relaxed = true)
    private val apiKeyStore = mockk<ApiKeyStore>(relaxed = true)

    private lateinit var orchestrator: ConversationOrchestrator

    @Before
    fun setup() {
        every { ear.state } returns MutableStateFlow(ListeningState.Idle)
        every { ear.partialResults } returns MutableSharedFlow()
        every { ear.finalResult } returns MutableSharedFlow()
        every { voice.state } returns MutableStateFlow(SpeakingState.Silent)
        every { apiKeyStore.get() } returns "sk-test-key"

        orchestrator = ConversationOrchestrator(ear, voice, apiClient, repository, apiKeyStore)
    }

    @Test
    fun `initial state is Idle`() {
        assertIs<AppState.Idle>(orchestrator.state.value)
    }

    @Test
    fun `onMicrophoneTapped starts listening when idle`() = runTest {
        orchestrator.onMicrophoneTapped(this)

        verify { ear.startListening(any()) }
        assertEquals(AppState.Listening, orchestrator.state.value)
    }

    @Test
    fun `onMicrophoneTapped interrupts Claude when speaking`() = runTest {
        every { voice.state } returns MutableStateFlow(SpeakingState.Speaking)
        // Simulate speaking state
        orchestrator.apply {
            // Use reflection or direct state manipulation in real tests
        }

        // When Claude is speaking and mic is tapped
        val stateField = orchestrator.javaClass.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateField.get(orchestrator) as MutableStateFlow<AppState>).value =
            AppState.ClaudeSpeaking("some text")

        orchestrator.onMicrophoneTapped(this)

        verify { voice.stop() }
        verify { ear.startListening(any()) }
    }

    @Test
    fun `missing API key produces error state`() = runTest {
        every { apiKeyStore.get() } returns null

        val finalResult = MutableSharedFlow<String>()
        every { ear.finalResult } returns finalResult

        orchestrator.start(this)
        finalResult.emit("Hallo Claude")

        assertIs<AppState.Error>(orchestrator.state.value)
    }

    @Test
    fun `successful response updates currentResponse`() = runTest {
        val finalResult = MutableSharedFlow<String>()
        every { ear.finalResult } returns finalResult
        coEvery { repository.createConversation(any()) } returns 1L
        coEvery { repository.addMessage(any(), any(), any()) } returns 1L

        every { apiClient.streamMessage(any(), any(), any()) } returns flowOf(
            StreamEvent.TextDelta("Hallo "),
            StreamEvent.TextDelta("Welt!"),
            StreamEvent.Done
        )

        orchestrator.start(this)
        orchestrator.loadOrCreateConversation()
        finalResult.emit("Test")

        assertEquals("Hallo Welt!", orchestrator.currentResponse.value)
    }
}

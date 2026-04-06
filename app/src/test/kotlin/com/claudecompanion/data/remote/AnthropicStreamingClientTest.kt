package com.claudecompanion.data.remote

import app.cash.turbine.test
import com.claudecompanion.MainDispatcherRule
import com.claudecompanion.domain.model.Message
import com.claudecompanion.domain.model.Role
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AnthropicStreamingClientTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `parseStreamEvent extracts text delta`() {
        val client = AnthropicStreamingClient(OkHttpClient())
        val method = client.javaClass.getDeclaredMethod("parseStreamEvent", String::class.java)
        method.isAccessible = true

        val json = """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"}}"""
        val result = method.invoke(client, json) as StreamEvent?

        assertIs<StreamEvent.TextDelta>(result)
        assertEquals("Hello", result.text)
    }

    @Test
    fun `parseStreamEvent returns null for unknown types`() {
        val client = AnthropicStreamingClient(OkHttpClient())
        val method = client.javaClass.getDeclaredMethod("parseStreamEvent", String::class.java)
        method.isAccessible = true

        val json = """{"type":"ping"}"""
        val result = method.invoke(client, json)
        assertEquals(null, result)
    }

    @Test
    fun `parseStreamEvent handles message_stop`() {
        val client = AnthropicStreamingClient(OkHttpClient())
        val method = client.javaClass.getDeclaredMethod("parseStreamEvent", String::class.java)
        method.isAccessible = true

        val json = """{"type":"message_stop"}"""
        val result = method.invoke(client, json) as StreamEvent?
        assertIs<StreamEvent.Done>(result)
    }
}

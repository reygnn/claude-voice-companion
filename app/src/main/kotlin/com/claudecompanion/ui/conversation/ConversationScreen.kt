package com.claudecompanion.ui.conversation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claudecompanion.domain.model.Role
import com.claudecompanion.ui.components.MicrophoneButton
import com.claudecompanion.ui.components.VoiceVisualizer
import com.claudecompanion.voice.AppState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }

    val keepScreenOn = appState is AppState.Listening
            || appState is AppState.Transcribing
            || appState is AppState.ClaudeThinking
            || appState is AppState.ClaudeSpeaking

    val view = LocalView.current
    LaunchedEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -40) showHistory = true
                    if (dragAmount > 40) showHistory = false
                }
            }
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNavigateToHistory) {
                Icon(Icons.Default.History, "History", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        // Center: Visualizer + status text
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = !showHistory) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VoiceVisualizer(
                        appState = appState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    // Status text
                    val statusText = when (appState) {
                        is AppState.Idle -> "Tap the microphone"
                        is AppState.Listening -> "Listening…"
                        is AppState.Transcribing -> (appState as AppState.Transcribing).partial
                        is AppState.ClaudeThinking -> "Thinking…"
                        is AppState.ClaudeSpeaking -> ""
                        is AppState.Error -> (appState as AppState.Error).message
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    // Current response text (scrollable)
                    if (currentResponse.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            val scrollState = rememberScrollState()
                            LaunchedEffect(currentResponse) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                            Text(
                                text = currentResponse,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .verticalScroll(scrollState),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // History panel (swipe up)
            AnimatedVisibility(
                visible = showHistory,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == Role.USER
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isUser)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (isUser) "You" else "Claude",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom: Cancel + Hold + Mic
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Cancel button
            AnimatedVisibility(
                visible = appState is AppState.Listening || appState is AppState.Transcribing
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.onCancelListening() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Hold toggle
            AnimatedVisibility(
                visible = appState is AppState.Listening || appState is AppState.Transcribing
            ) {
                var holdActive by remember { mutableStateOf(false) }

                // Reset hold state when leaving listening
                LaunchedEffect(appState) {
                    if (appState !is AppState.Listening && appState !is AppState.Transcribing) {
                        holdActive = false
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (holdActive) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable {
                            holdActive = !holdActive
                            viewModel.onHoldToggle(holdActive)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.PanTool,
                        contentDescription = "Hold",
                        tint = if (holdActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            MicrophoneButton(
                appState = appState,
                onClick = { viewModel.onMicrophoneTapped() }
            )
        }
    }
}
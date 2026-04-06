package com.claudecompanion.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.claudecompanion.voice.AppState

@Composable
fun MicrophoneButton(
    appState: AppState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isActive = appState is AppState.Listening || appState is AppState.Transcribing

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val bgColor by animateColorAsState(
        targetValue = when (appState) {
            is AppState.Listening, is AppState.Transcribing -> MaterialTheme.colorScheme.primary
            is AppState.ClaudeSpeaking -> MaterialTheme.colorScheme.tertiary
            is AppState.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(300),
        label = "bg"
    )

    val icon = when (appState) {
        is AppState.Listening, is AppState.Transcribing -> Icons.Default.Mic
        is AppState.ClaudeSpeaking -> Icons.Default.Stop
        is AppState.Error -> Icons.Default.MicOff
        else -> Icons.Default.Mic
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(80.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Microphone",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(36.dp)
        )
    }
}

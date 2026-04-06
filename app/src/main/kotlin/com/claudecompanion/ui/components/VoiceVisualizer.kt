package com.claudecompanion.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.claudecompanion.voice.AppState
import kotlin.math.sin

@Composable
fun VoiceVisualizer(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (appState) {
                    is AppState.Listening -> 1200
                    is AppState.Transcribing -> 1000
                    is AppState.ClaudeThinking -> 2000
                    is AppState.ClaudeSpeaking -> 800
                    else -> 3000
                },
                easing = LinearEasing
            )
        ),
        label = "phase"
    )

    val amplitude by animateFloatAsState(
        targetValue = when (appState) {
            is AppState.Listening -> 0.6f
            is AppState.Transcribing -> 0.8f
            is AppState.ClaudeThinking -> 0.3f
            is AppState.ClaudeSpeaking -> 0.7f
            is AppState.Error -> 0.1f
            else -> 0.15f
        },
        animationSpec = tween(500),
        label = "amplitude"
    )

    val color = when (appState) {
        is AppState.Listening, is AppState.Transcribing -> primary
        is AppState.ClaudeThinking -> secondary
        is AppState.ClaudeSpeaking -> primary
        is AppState.Error -> Color(0xFFCF6679)
        else -> secondary.copy(alpha = 0.5f)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerY = size.height / 2f
        val barCount = 48
        val barWidth = size.width / (barCount * 2f)
        val maxBarHeight = size.height * 0.4f

        for (i in 0 until barCount) {
            val x = (size.width / barCount) * i + barWidth
            val normalizedX = i.toFloat() / barCount
            val wave = sin(phase + normalizedX * 4f * Math.PI.toFloat())
            val secondWave = sin(phase * 0.7f + normalizedX * 6f * Math.PI.toFloat()) * 0.3f
            val barHeight = maxBarHeight * amplitude * (wave + secondWave).coerceIn(-1f, 1f)

            drawLine(
                color = color.copy(alpha = 0.5f + 0.5f * amplitude),
                start = Offset(x, centerY - barHeight),
                end = Offset(x, centerY + barHeight),
                strokeWidth = barWidth * 0.8f,
                cap = StrokeCap.Round
            )
        }
    }
}

package com.claudecompanion.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// Evening palette – golden warmth, wine red, cigar smoke
val Gold = Color(0xFFD4A843)
val WarmGold = Color(0xFFE8C566)
val WineRed = Color(0xFF8B2252)
val DeepWine = Color(0xFF5C1634)
val CigarAmber = Color(0xFFB8860B)
val MidnightBrown = Color(0xFF1A1210)
val DarkLeather = Color(0xFF2A1F1A)
val SmokeyGray = Color(0xFF8C7B72)
val CreamWhite = Color(0xFFF5EDE4)
val SoftEmber = Color(0xFFD4A574)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = MidnightBrown,
    secondary = WineRed,
    onSecondary = CreamWhite,
    tertiary = CigarAmber,
    background = MidnightBrown,
    surface = DarkLeather,
    onBackground = CreamWhite,
    onSurface = CreamWhite,
    error = Color(0xFFCF6679),
    outline = SmokeyGray,
    surfaceVariant = DeepWine,
    onSurfaceVariant = SoftEmber
)

@Composable
fun ClaudeCompanionTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MidnightBrown.toArgb()
            window.navigationBarColor = MidnightBrown.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
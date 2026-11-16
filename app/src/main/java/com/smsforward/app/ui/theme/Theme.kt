package com.smsforward.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 0.2.5 的视觉：浅灰底 + 纯白卡片，深色为纯黑底 + 深灰卡片。
 */
private val LightColors = lightColorScheme(
    primary = MiuixBlue,
    onPrimary = Color.White,
    primaryContainer = MiuixBlueSoft,
    onPrimaryContainer = Color(0xFF0A2B66),
    secondary = MiuixGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F8EE),
    onSecondaryContainer = Color(0xFF04351F),
    tertiary = MiuixViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFEAFF),
    onTertiaryContainer = Color(0xFF1D0F4D),
    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF191919),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191919),
    surfaceVariant = Color(0xFFF5F6F8),
    onSurfaceVariant = Color(0xFF7A7A7A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF5F6F8),
    surfaceContainerHigh = Color(0xFFEFF1F4),
    surfaceContainerHighest = Color(0xFFE9EBEF),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFEDEDED),
    error = MiuixRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEA),
    onErrorContainer = Color(0xFF4A0B08),
)

private val DarkColors = darkColorScheme(
    primary = MiuixBlueLight,
    onPrimary = Color(0xFF06224D),
    primaryContainer = Color(0xFF15305C),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF3DDC97),
    onSecondary = Color(0xFF00331F),
    secondaryContainer = Color(0xFF0B4A32),
    onSecondaryContainer = Color(0xFFC9F5DF),
    tertiary = Color(0xFFB7A6FF),
    onTertiary = Color(0xFF1C0F4A),
    tertiaryContainer = Color(0xFF33265F),
    onTertiaryContainer = Color(0xFFE9E0FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF9A9A9E),
    surfaceContainerLowest = Color(0xFF0C0C0D),
    surfaceContainerLow = Color(0xFF151516),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF242426),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2A2A2C),
    error = Color(0xFFFF6961),
    onError = Color(0xFF3A0906),
    errorContainer = Color(0xFF5C1310),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun SmsForwardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

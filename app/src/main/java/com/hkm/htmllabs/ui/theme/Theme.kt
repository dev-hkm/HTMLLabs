package com.hkm.htmllabs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hkm.htmllabs.data.AppSettings

private val DarkColorScheme = darkColorScheme(
    primary = White,
    secondary = LightGray,
    tertiary = White,
    background = Black,
    surface = Gray,
    onPrimary = Black,
    onSecondary = White,
    onTertiary = Black,
    onBackground = White,
    onSurface = White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF15171C),
    secondary = Color(0xFFE9ECF3),
    tertiary = Color(0xFF15171C),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF15171C),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111318),
    onSurface = Color(0xFF111318),
)

@Composable
fun HTMLLabsTheme(
    themeMode: AppSettings.ThemeMode = AppSettings.ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        AppSettings.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppSettings.ThemeMode.LIGHT -> false
        AppSettings.ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}

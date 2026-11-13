package com.example.tinawarehouse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Accent, onPrimary = Color.White, background = Color(0xFF0B1220), surface = Color(0xFF111B2E),
    onBackground = Color.White, onSurface = Color.White, error = DupRed,
)
private val Light = lightColorScheme(
    primary = Accent, onPrimary = Color.White, background = Color(0xFFF4F6FA), surface = Color.White,
    onBackground = Navy, onSurface = Navy, error = DupRed,
)

/** [mode]: "system" | "light" | "dark". Màn đăng nhập luôn tối (forceDark). */
@Composable
fun TinaWarehouseTheme(mode: String = "system", forceDark: Boolean = false, content: @Composable () -> Unit) {
    val dark = forceDark || when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography, content = content)
}

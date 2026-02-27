package com.blegod.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.blegod.app.data.SettingsManager

/**
 * BleGod Theme — Material You with dynamic wallpaper-based colors.
 *
 * Now respects the user's dark/light mode preference from Settings.
 * SYSTEM = follow system default, DARK = always dark, LIGHT = always light.
 */
@Composable
fun BleGodTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }

    val isDark = when (settings.themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

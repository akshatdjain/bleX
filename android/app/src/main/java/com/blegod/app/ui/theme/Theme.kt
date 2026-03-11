package com.blegod.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.blegod.app.data.SettingsManager

// ── Google Fonts Provider ───────────────────────────────────────
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.blegod.app.R.array.com_google_android_gms_fonts_certs
)

// ── Inter Font Family ───────────────────────────────────────────
private val InterFont = GoogleFont("Inter")
val InterFontFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Bold)
)

// ── Custom Typography ───────────────────────────────────────────
private val BleXTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = InterFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = InterFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = InterFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = InterFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = InterFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = InterFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = InterFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = InterFontFamily)
    )
}

/**
 * BleX Theme — Material You with:
 *  - Dynamic wallpaper-based colors
 *  - Google Fonts (Inter) for premium typography
 *  - Respects user dark/light mode preference
 */
@Composable
fun BleXTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val themeMode by settings.themeModeFlow.collectAsState()

    val isDark = when (themeMode) {
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
        typography = BleXTypography,
        content = content
    )
}

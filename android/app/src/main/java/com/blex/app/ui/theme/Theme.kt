package com.blex.app.ui.theme

import android.os.Build
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
import com.blex.app.data.SettingsManager

// ── Google Fonts Provider ───────────────────────────────────────
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.blex.app.R.array.com_google_android_gms_fonts_certs
)

// ── Inter (body / labels) ───────────────────────────────────────
private val InterFont = GoogleFont("Inter")
val InterFontFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Bold)
)

// ── Sora (headings / display) ───────────────────────────────────
private val SoraFont = GoogleFont("Sora")
val SoraFontFamily = FontFamily(
    Font(googleFont = SoraFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = SoraFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = SoraFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = SoraFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = SoraFont, fontProvider = provider, weight = FontWeight.ExtraBold)
)

// ── Typography — Sora headlines, Inter body ─────────────────────
private val BleXTypography = Typography().let { base ->
    Typography(
        displayLarge   = base.displayLarge.copy(fontFamily = SoraFontFamily),
        displayMedium  = base.displayMedium.copy(fontFamily = SoraFontFamily),
        displaySmall   = base.displaySmall.copy(fontFamily = SoraFontFamily),
        headlineLarge  = base.headlineLarge.copy(fontFamily = SoraFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = SoraFontFamily),
        headlineSmall  = base.headlineSmall.copy(fontFamily = SoraFontFamily),
        titleLarge     = base.titleLarge.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium    = base.titleMedium.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Medium),
        titleSmall     = base.titleSmall.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Medium),
        bodyLarge      = base.bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium     = base.bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall      = base.bodySmall.copy(fontFamily = InterFontFamily),
        labelLarge     = base.labelLarge.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium),
        labelMedium    = base.labelMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium),
        labelSmall     = base.labelSmall.copy(fontFamily = InterFontFamily)
    )
}

// ── BleX Theme — Material You dynamic colors ────────────────────
// Colors come from the user's wallpaper via Android 12+ dynamic color.
// On Android 11 and below, falls back to a neutral Material 3 baseline.
@Composable
fun BleXTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val themeMode by settings.themeModeFlow.collectAsState()

    val isDark = when (themeMode) {
        "DARK"  -> true
        "LIGHT" -> false
        else    -> isSystemInDarkTheme()
    }

    // Dynamic Material You — Android 12+ (API 31+)
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context)
            else        dynamicLightColorScheme(context)
        }
        // Fallback for older Android — neutral Material 3 baseline
        isDark -> darkColorScheme()
        else   -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = BleXTypography,
        content     = content
    )
}

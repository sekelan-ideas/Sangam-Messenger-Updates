package com.sangam.messenger.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sangam.messenger.Ui

private val SangamColorScheme = lightColorScheme(
    primary = SangamTealDark,
    onPrimary = Color.White,
    primaryContainer = SangamTeal,
    onPrimaryContainer = Color.White,
    secondary = SangamGreen,
    onSecondary = Color.White,
    tertiary = SangamOrange,
    onTertiary = Color.White,
    background = Color.White,
    onBackground = SangamText,
    surface = Color.White,
    onSurface = SangamText,
    surfaceVariant = SangamSurfaceVariant,
    onSurfaceVariant = Color(0xFF5A6B72),
    outline = Color(0xFFB8CDD6)
)

private val SangamDarkScheme = darkColorScheme(
    primary = SangamTeal,
    onPrimary = Color(0xFF00323D),
    primaryContainer = SangamTealDark,
    onPrimaryContainer = Color.White,
    secondary = SangamGreen,
    onSecondary = Color(0xFF0A2E17),
    tertiary = SangamOrange,
    onTertiary = Color(0xFF3A1B00),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE2ECEE),
    surface = Color(0xFF161B1E),
    onSurface = Color(0xFFE2ECEE),
    surfaceVariant = Color(0xFF223035),
    onSurfaceVariant = Color(0xFF9FB3BA),
    outline = Color(0xFF46565E)
)

private val SangamTypography = Typography()

@Composable
fun SangamTheme(content: @Composable () -> Unit) {
    val dark = when (Ui.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) SangamDarkScheme else SangamColorScheme,
        typography = SangamTypography,
        shapes = SangamShapes,
        content = content
    )
}

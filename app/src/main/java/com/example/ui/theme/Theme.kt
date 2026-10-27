package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// The dark scheme is deliberately built from the surface ladder rather than
// from outlines: M3 roles (surface / surfaceVariant / outline) map onto the
// charcoal steps so any Material component dropped into the app inherits the
// same tonal hierarchy the hand-written components use.
private val NoxaDarkColorScheme = darkColorScheme(
    primary = NoxaLime,
    onPrimary = NoxaBlack,
    primaryContainer = NoxaSurfaceSecondary,
    onPrimaryContainer = NoxaLime,
    secondary = NoxaLime,
    onSecondary = NoxaBlack,
    secondaryContainer = NoxaSurfaceElevated,
    onSecondaryContainer = NoxaWhite,
    tertiary = NoxaLime,
    onTertiary = NoxaBlack,
    background = NoxaBlack,
    onBackground = NoxaWhite,
    surface = NoxaSurface,
    onSurface = NoxaTextPrimary,
    surfaceVariant = NoxaSurfaceElevated,
    onSurfaceVariant = NoxaTextSecondary,
    outline = NoxaBorder,
    outlineVariant = NoxaBorderSubtle,
    error = NoxaError,
    onError = NoxaWhite
)

@Composable
fun NoxaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NoxaBlack.toArgb()
                // Both control surfaces float *above* the gesture inset now, so
                // the strip behind them is plain canvas — the system bar matches
                // the app background instead of a dock that no longer exists.
                window.navigationBarColor = NoxaBlack.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                // Dark background -> light status icons (false for isAppearanceLightStatusBars)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = NoxaDarkColorScheme,
        typography = Typography,
        // Shape roles come from the same scale as every hand-built surface
        // (see Shape.kt), so Material components never round corners
        // differently from the rest of the app.
        shapes = NoxaShapes,
        content = content
    )
}

// Backward compatibility alias for template references
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NoxaTheme(content = content)
}

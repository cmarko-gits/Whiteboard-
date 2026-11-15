package dev.whiteboard.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import dev.whiteboard.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF315D4C), onPrimary = Color.White, primaryContainer = Color(0xFFDEEBDD), onPrimaryContainer = Color(0xFF234436),
    background = Color(0xFFFAFBF7), onBackground = Color(0xFF242B25), surface = Color(0xFFFAFBF7), onSurface = Color(0xFF242B25),
    surfaceVariant = Color(0xFFEDF0E8), onSurfaceVariant = Color(0xFF626B60), outline = Color(0xFF74826E),
    secondary = Color(0xFF536B50), secondaryContainer = Color(0xFFE2E9DB), onSecondaryContainer = Color(0xFF293D27),
    surfaceContainer = Color(0xFFF0F3EA), surfaceContainerHigh = Color(0xFFEBEFE5), surfaceContainerHighest = Color(0xFFE5EADF),
    surfaceTint = Color(0xFF315D4C)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5D7BE), onPrimary = Color(0xFF173B2C), primaryContainer = Color(0xFF304F3F),
    background = Color(0xFF171C18), onBackground = Color(0xFFE3E9DF), surface = Color(0xFF171C18), onSurface = Color(0xFFE3E9DF),
    surfaceVariant = Color(0xFF293129), onSurfaceVariant = Color(0xFFB5C0B1), outline = Color(0xFF879580),
    secondary = Color(0xFFBDD0B7), secondaryContainer = Color(0xFF394B35), onSecondaryContainer = Color(0xFFDCEAD6),
    surfaceContainer = Color(0xFF202820), surfaceContainerHigh = Color(0xFF283128), surfaceContainerHighest = Color(0xFF313B30),
    surfaceTint = Color(0xFFB5D7BE)
)
private val LocalDarkAppearance = staticCompositionLocalOf { false }

@Composable
fun WhiteboardTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.SYSTEM && isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        CompositionLocalProvider(LocalDarkAppearance provides dark) {
            MatchWindowSystemBars()
            content()
        }
    }
}

/** Dialogs own a separate Window; update its icons when the inherited app theme changes. */
@Composable
internal fun MatchWindowSystemBars() {
    val dark = LocalDarkAppearance.current
    val view = LocalView.current
    SideEffect {
        view.ownedWindow()?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

private fun View.ownedWindow(): Window? {
    var owner: ViewParent? = parent
    while (owner != null) {
        if (owner is DialogWindowProvider) return owner.window
        owner = owner.parent
    }
    return context.activity()?.window
}
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

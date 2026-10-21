package com.motionloop.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colors that change between the light and the dark theme. */
@Immutable
data class MlPalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val mint: Color,
    val coral: Color,
    val isDark: Boolean,
)

val DarkPalette = MlPalette(
    background = Color(0xFF0E0F1A),
    surface = Color(0xFF181A2A),
    surfaceHigh = Color(0xFF262A45),
    textPrimary = Color(0xFFF2F3FA),
    textSecondary = Color(0xFFB0B4CC),
    mint = Color(0xFF2EE6C5),
    coral = Color(0xFFFF6B7A),
    isDark = true,
)

// Accents are darker here: the bright mint and coral fail contrast on white.
val LightPalette = MlPalette(
    background = Color(0xFFF5F6FB),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE6E8F3),
    textPrimary = Color(0xFF15172B),
    textSecondary = Color(0xFF51567A),
    mint = Color(0xFF087F6B),
    coral = Color(0xFFC62F45),
    isDark = false,
)

val LocalMlPalette = staticCompositionLocalOf { DarkPalette }

object MlColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalMlPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalMlPalette.current.surface
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalMlPalette.current.surfaceHigh
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalMlPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalMlPalette.current.textSecondary
    val Mint: Color @Composable @ReadOnlyComposable get() = LocalMlPalette.current.mint
    val Coral: Color @Composable @ReadOnlyComposable get() = LocalMlPalette.current.coral

    /** Same in both themes: white text reads well on it. */
    val Violet = Color(0xFF6B4EFF)

    /** Near-black, for text on bright accents. */
    val Ink = Color(0xFF0E0F1A)

    /** Deeper violet for gradients behind white text. */
    val VioletDeep = Color(0xFF3A2E8C)

    /** Text on a mint button: dark on the bright dark-theme mint, white on the deeper light-theme one. */
    val OnMint: Color @Composable @ReadOnlyComposable get() = if (LocalMlPalette.current.isDark) Ink else Color.White

    /** Arrow overlay color in the editor. Drawn on the photo, so it does not follow the theme. */
    val Arrow = Color(0xFF2EE6C5)

    /** Anchor and freeze overlay color, on the photo. */
    val Freeze = Color(0xFFFF6B7A)
}

private fun scheme(p: MlPalette) = if (p.isDark) {
    darkColorScheme(
        primary = MlColors.Violet,
        onPrimary = Color.White,
        secondary = p.mint,
        onSecondary = MlColors.Ink,
        tertiary = p.coral,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textSecondary,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHigh,
        outline = p.textSecondary,
    )
} else {
    lightColorScheme(
        primary = MlColors.Violet,
        onPrimary = Color.White,
        secondary = p.mint,
        onSecondary = Color.White,
        tertiary = p.coral,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textSecondary,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHigh,
        outline = p.textSecondary,
    )
}

private val typography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun MotionLoopTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalMlPalette provides palette) {
        MaterialTheme(colorScheme = scheme(palette), typography = typography) {
            // Text without an explicit color takes this. Without it, it falls back to black,
            // which made titles unreadable on the dark background.
            CompositionLocalProvider(LocalContentColor provides palette.textPrimary, content = content)
        }
    }
}

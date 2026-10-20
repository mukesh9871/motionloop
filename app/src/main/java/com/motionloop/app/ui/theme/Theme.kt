package com.motionloop.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object MlColors {
    val Background = Color(0xFF0E0F1A)
    val Surface = Color(0xFF181A2A)
    val SurfaceHigh = Color(0xFF232640)
    val Violet = Color(0xFF7C5CFF)
    val Mint = Color(0xFF2EE6C5)
    val Coral = Color(0xFFFF6B7A)
    val TextPrimary = Color(0xFFF2F3FA)
    val TextSecondary = Color(0xFFA3A7C2)

    /** Arrow overlay color in the editor. */
    val Arrow = Mint

    /** Anchor and freeze overlay color. */
    val Freeze = Coral
}

private val colors = darkColorScheme(
    primary = MlColors.Violet,
    onPrimary = Color.White,
    secondary = MlColors.Mint,
    onSecondary = Color(0xFF00201A),
    tertiary = MlColors.Coral,
    background = MlColors.Background,
    onBackground = MlColors.TextPrimary,
    surface = MlColors.Surface,
    onSurface = MlColors.TextPrimary,
    surfaceVariant = MlColors.SurfaceHigh,
    onSurfaceVariant = MlColors.TextSecondary,
    surfaceContainer = MlColors.Surface,
    surfaceContainerHigh = MlColors.SurfaceHigh,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun MotionLoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

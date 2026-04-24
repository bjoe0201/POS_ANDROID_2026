package com.pos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PosColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardHover: Color,
    val border: Color,
    val accent: Color,
    val accentHov: Color,
    val accentDim: Color,
    val accentDim2: Color,
    val text: Color,
    val textSub: Color,
    val textMuted: Color,
    val occupied: Color,
    val occupiedBg: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val topbar: Color,
    val chartBars: List<Color>,
)

val DefaultPosColors = PosColors(
    bg          = PosBackground,
    surface     = PosSurface,
    card        = PosCard,
    cardHover   = PosCardHover,
    border      = PosBorder,
    accent      = PosAccent,
    accentHov   = PosAccentHov,
    accentDim   = PosAccentDim,
    accentDim2  = PosAccentDim2,
    text        = PosText,
    textSub     = PosTextSub,
    textMuted   = PosTextMuted,
    occupied    = PosOccupied,
    occupiedBg  = PosOccupiedBg,
    success     = PosSuccess,
    warning     = PosWarning,
    error       = PosError,
    topbar      = PosTopbar,
    chartBars   = listOf(
        Color(0xFFC62828), Color(0xFFEF5350), Color(0xFFFF8A80),
        Color(0xFFFFCDD2), Color(0xFFB71C1C)
    ),
)

val LocalPosColors = staticCompositionLocalOf { DefaultPosColors }

private val DarkColors = darkColorScheme(
    primary            = PosAccent,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF4A0A0A),
    onPrimaryContainer = PosText,
    secondary          = PosOccupied,
    onSecondary        = Color.White,
    background         = PosBackground,
    onBackground       = PosText,
    surface            = PosSurface,
    onSurface          = PosText,
    surfaceVariant     = PosCard,
    onSurfaceVariant   = PosTextSub,
    outline            = PosBorder,
    error              = PosError,
    onError            = Color.White,
)

@Composable
fun PosAndroidTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPosColors provides DefaultPosColors) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = Typography,
            content = content
        )
    }
}

package com.pos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Red700,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Red100,
    secondary = Orange700,
    secondaryContainer = Amber50,
    background = Grey200,
    surface = androidx.compose.ui.graphics.Color.White,
    error = Red500
)

@Composable
fun PosAndroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}

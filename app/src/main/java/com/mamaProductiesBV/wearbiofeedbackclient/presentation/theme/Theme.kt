package com.mamaProductiesBV.wearbiofeedbackclient.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors

private val wearColorPalette = Colors(
    primary = Color(0xFF4CAF50),     // green
    secondary = Color(0xFF9E9E9E),   // gray
    error = Color(0xFFF44336),       // red
    background = Color.Black,
    surface = Color.Black,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun WearBiofeedbackClientTheme(
    content: @Composable () -> Unit
) {
    /**
     * Empty theme to customize for your app.
     * See: https://developer.android.com/jetpack/compose/designsystems/custom
     */
    MaterialTheme(
        colors = wearColorPalette,
        content = content
    )
}
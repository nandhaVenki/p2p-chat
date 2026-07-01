package com.example.p2pchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.Color

@Composable
fun P2PChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = EmeraldGreen,
            secondary = DeepEmerald,
            background = DarkBackgroundColor,
            surface = DarkPeerBubbleColor
        )
    } else {
        lightColorScheme(
            primary = EmeraldGreen,
            secondary = DeepEmerald,
            background = BackgroundColor,
            surface = Color.White
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

package com.github.walma.rtpplayer.sample.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RtpColorScheme = darkColorScheme(
    primary = Accent,
    secondary = AccentDark,
    background = ScreenBackground,
    surface = SurfacePanel,
    surfaceVariant = SurfacePanel,
)

@Composable
fun RtpPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RtpColorScheme,
        content = content,
    )
}

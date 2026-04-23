package com.github.walma.rtpplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun RtpPlayerTheme(
    colors: RtpPlayerColors = RtpPlayerDefaults.colors(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colors.accent,
            secondary = colors.accentDark,
            background = colors.screenBackground,
            surface = colors.surfacePanel,
            surfaceVariant = colors.surfacePanel,
        ),
        content = content,
    )
}

package com.github.walma.rtpplayer.ui.theme

import androidx.compose.ui.graphics.Color

data class RtpPlayerColors(
    val screenBackground: Color = Color(0xFF050B14),
    val surfacePanel: Color = Color(0xFF102338),
    val accent: Color = Color(0xFF56C2FF),
    val accentDark: Color = Color(0xFF1A7DB8),
)

object RtpPlayerDefaults {
    fun colors(
        screenBackground: Color = Color(0xFF050B14),
        surfacePanel: Color = Color(0xFF102338),
        accent: Color = Color(0xFF56C2FF),
        accentDark: Color = Color(0xFF1A7DB8),
    ): RtpPlayerColors = RtpPlayerColors(
        screenBackground = screenBackground,
        surfacePanel = surfacePanel,
        accent = accent,
        accentDark = accentDark,
    )
}

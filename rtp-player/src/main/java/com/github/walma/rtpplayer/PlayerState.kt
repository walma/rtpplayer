package com.github.walma.rtpplayer

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isRecording: Boolean = false,
    val errorMessage: String? = null,
    val debugMessage: String? = null,
)

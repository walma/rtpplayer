package com.github.walma.rtpplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.github.walma.rtpplayer.PlayerState
import com.github.walma.rtpplayer.RtpVlcPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class RtpPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    val player = RtpVlcPlayer(
        context = application,
        onStateChanged = { _playerState.value = it },
    )

    override fun onCleared() {
        player.release()
    }
}

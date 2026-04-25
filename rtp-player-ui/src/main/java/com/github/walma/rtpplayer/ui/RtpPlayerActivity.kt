package com.github.walma.rtpplayer.ui

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.walma.rtpplayer.PlayerState
import com.github.walma.rtpplayer.ui.theme.RtpPlayerColors
import com.github.walma.rtpplayer.ui.theme.RtpPlayerDefaults
import com.github.walma.rtpplayer.ui.theme.RtpPlayerTheme
import kotlinx.coroutines.launch

open class RtpPlayerActivity : ComponentActivity() {

    internal val viewModel: RtpPlayerViewModel by viewModels()

    private var isInPipMode by mutableStateOf(false)

    interface PiPMediaCallback {
        fun onPlayRequested()
        fun onPauseRequested()
        fun onStopRequested()
    }

    private var pipCallback: PiPMediaCallback? = null

    fun setPiPMediaCallback(callback: PiPMediaCallback?) {
        pipCallback = callback
    }

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PIP_PLAY -> pipCallback?.onPlayRequested()
                ACTION_PIP_STOP -> pipCallback?.onStopRequested()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPipMode = isInPictureInPictureMode
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY)
            addAction(ACTION_PIP_STOP)
        }
        ContextCompat.registerReceiver(this, pipActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collect { state ->
                    if (isInPipMode) {
                        setPictureInPictureParams(buildPipParams(state.isPlaying || state.isBuffering))
                    }
                }
            }
        }

        setContent {
            RtpPlayerTheme(colors = provideThemeColors()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val screenConfig = provideScreenConfig(intent)
                    RtpPlayerScreen(
                        viewModel = viewModel,
                        isInPipMode = isInPipMode,
                        onEnterPip = { enterPipModeCompat() },
                        initialRecordFileName = screenConfig.initialRecordFileName,
                        initialStreamUri = screenConfig.initialStreamUri,
                        initialNetworkCaching = screenConfig.initialNetworkCaching,
                        initialClockJitter = screenConfig.initialClockJitter,
                        initialClockSynchro = screenConfig.initialClockSynchro,
                        initialDemux = screenConfig.initialDemux,
                        uiConfig = screenConfig.uiConfig,
                        topStartContent = screenConfig.topStartContent,
                        bottomStartContent = screenConfig.bottomStartContent,
                    )
                }
            }
        }
    }

    protected open fun provideThemeColors(): RtpPlayerColors = RtpPlayerDefaults.colors()

    protected open fun provideScreenConfig(intent: Intent): RtpPlayerScreenConfig {
        val titleText = intent.getStringExtra(EXTRA_TOP_START_TEXT) ?: "Cam1"
        val statusPrefixText = intent.getStringExtra(EXTRA_BOTTOM_START_TEXT)
        val useTextControls = intent.getBooleanExtra(EXTRA_USE_TEXT_CONTROLS, false)
        val showRecordButton = intent.getBooleanExtra(EXTRA_SHOW_RECORD_BUTTON, true)

        return RtpPlayerScreenConfig(
            initialRecordFileName = intent.getStringExtra(EXTRA_RECORD_FILE_NAME),
            initialStreamUri = intent.getStringExtra(EXTRA_STREAM_URI) ?: "udp://@:5004",
            initialNetworkCaching = intent.getStringExtra(EXTRA_NETWORK_CACHING) ?: "500",
            initialClockJitter = intent.getStringExtra(EXTRA_CLOCK_JITTER) ?: "0",
            initialClockSynchro = intent.getStringExtra(EXTRA_CLOCK_SYNCHRO) ?: "0",
            initialDemux = intent.getStringExtra(EXTRA_DEMUX) ?: "ts",
            uiConfig = RtpPlayerUiConfig(
                titleText = titleText,
                statusPrefixText = statusPrefixText,
                controlsStyle = if (useTextControls) {
                    RtpPlayerControlsStyle.Text
                } else {
                    RtpPlayerControlsStyle.Icons
                },
                showRecordButton = showRecordButton,
            ),
        )
    }

    private fun enterPipModeCompat() {
        Log.d(TAG, "enterPipModeCompat called")
        if (supportsPictureInPictureCompat()) {
            try {
                val isPlaying = viewModel.playerState.value.let { it.isPlaying || it.isBuffering }
                Log.d(TAG, "enterPictureInPictureMode: entering PiP, isPlaying=$isPlaying")
                enterPictureInPictureMode(buildPipParams(isPlaying))
            } catch (_: IllegalStateException) {
                Log.d(TAG, "enterPictureInPictureMode: IllegalStateException")
            }
        }
    }

    private fun buildPipParams(isPlaying: Boolean): PictureInPictureParams {
        val actions = if (isPlaying) {
            val intent = PendingIntent.getBroadcast(
                this, REQUEST_CODE_STOP,
                Intent(ACTION_PIP_STOP).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            listOf(
                RemoteAction(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Stop", "Stop stream", intent,
                ),
            )
        } else {
            val intent = PendingIntent.getBroadcast(
                this, REQUEST_CODE_PLAY,
                Intent(ACTION_PIP_PLAY).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            listOf(
                RemoteAction(
                    Icon.createWithResource(this, android.R.drawable.ic_media_play),
                    "Play", "Play stream", intent,
                ),
            )
        }

        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)
            .build()
    }

    private fun supportsPictureInPictureCompat(): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return false
        }
        val activityInfo = packageManager.getActivityInfo(componentName, 0)
        return activityInfo.flags and SUPPORTS_PICTURE_IN_PICTURE_FLAG != 0
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        Log.d(TAG, "onPictureInPictureModeChanged: isInPiP=$isInPictureInPictureMode")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        Log.d(TAG, "onUserLeaveHint called")
        super.onUserLeaveHint()
        if (!isInPipMode) {
            enterPipModeCompat()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        unregisterReceiver(pipActionReceiver)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RtpPlayerActivity"
        private const val SUPPORTS_PICTURE_IN_PICTURE_FLAG = 0x00400000
        private const val ACTION_PIP_PLAY = "com.github.walma.rtpplayer.pip.PLAY"
        private const val ACTION_PIP_STOP = "com.github.walma.rtpplayer.pip.STOP"
        private const val REQUEST_CODE_PLAY = 1
        private const val REQUEST_CODE_STOP = 2

        const val EXTRA_RECORD_FILE_NAME = "extra_record_file_name"
        const val EXTRA_TOP_START_TEXT = "extra_top_start_text"
        const val EXTRA_BOTTOM_START_TEXT = "extra_bottom_start_text"
        const val EXTRA_USE_TEXT_CONTROLS = "extra_use_text_controls"
        const val EXTRA_SHOW_RECORD_BUTTON = "extra_show_record_button"
        const val EXTRA_STREAM_URI = "extra_stream_uri"
        const val EXTRA_NETWORK_CACHING = "extra_network_caching"
        const val EXTRA_CLOCK_JITTER = "extra_clock_jitter"
        const val EXTRA_CLOCK_SYNCHRO = "extra_clock_synchro"
        const val EXTRA_DEMUX = "extra_demux"
    }
}

data class RtpPlayerScreenConfig(
    val initialRecordFileName: String? = null,
    val initialStreamUri: String = "udp://@:5004",
    val initialNetworkCaching: String = "500",
    val initialClockJitter: String = "0",
    val initialClockSynchro: String = "0",
    val initialDemux: String = "ts",
    val uiConfig: RtpPlayerUiConfig = RtpPlayerUiConfig(),
    val topStartContent: (@Composable BoxScope.() -> Unit)? = null,
    val bottomStartContent: (@Composable BoxScope.(PlayerState) -> Unit)? = null,
)

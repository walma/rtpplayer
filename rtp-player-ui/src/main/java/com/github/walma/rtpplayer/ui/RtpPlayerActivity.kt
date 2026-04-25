package com.github.walma.rtpplayer.ui

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Rational
import androidx.activity.ComponentActivity
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.walma.rtpplayer.PlayerState
import com.github.walma.rtpplayer.ui.theme.RtpPlayerColors
import com.github.walma.rtpplayer.ui.theme.RtpPlayerDefaults
import com.github.walma.rtpplayer.ui.theme.RtpPlayerTheme

open class RtpPlayerActivity : ComponentActivity() {

    private var isInPipMode by mutableStateOf(false)

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
    }

    override fun onDetachedFromWindow() {
        Log.d(TAG, "onDetachedFromWindow called")
        super.onDetachedFromWindow()
    }

    override fun onRestart() {
        Log.d(TAG, "onRestart called")
        super.onRestart()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        Log.d(TAG, "onRestoreInstanceState called")
        super.onRestoreInstanceState(savedInstanceState, persistentState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RtpPlayerTheme(colors = provideThemeColors()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val screenConfig = provideScreenConfig(intent)
                    RtpPlayerScreen(
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
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                Log.d(TAG, "enterPictureInPictureMode: entering PiP")
                enterPictureInPictureMode(params)
            } catch (_: IllegalStateException) {
                Log.d(TAG, "enterPictureInPictureMode: IllegalStateException")
            }
        }
    }

    private fun supportsPictureInPictureCompat(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

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

    override fun onPause() {
        Log.d(TAG, "onPause called")
        super.onPause()
    }

    override fun onResume() {
        Log.d(TAG, "onResume called")
        super.onResume()
    }

    override fun onStop() {
        Log.d(TAG, "onStop called")
        super.onStop()
    }

    override fun onStart() {
        Log.d(TAG, "onStart called")
        super.onStart()
    }

    companion object {
        private const val TAG = "RtpPlayerActivity"
        private const val SUPPORTS_PICTURE_IN_PICTURE_FLAG = 0x00400000
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

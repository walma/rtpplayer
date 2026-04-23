package com.github.walma.rtpplayer.sample

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.github.walma.rtpplayer.PlayerSettings
import com.github.walma.rtpplayer.PlayerState
import com.github.walma.rtpplayer.RtpVlcPlayer
import com.github.walma.rtpplayer.sample.ui.theme.RtpPlayerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var playerInstance: RtpVlcPlayer? = null
    private var isInPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRecordFileName = intent.getStringExtra(EXTRA_RECORD_FILE_NAME)
        setContent {
            RtpPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RtpPlayerScreen(
                        onPlayerCreated = { playerInstance = it },
                        isInPipMode = isInPipMode,
                        onEnterPip = { enterPipMode() },
                        initialRecordFileName = initialRecordFileName,
                    )
                }
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerInstance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode()
        }
    }

    companion object {
        const val EXTRA_RECORD_FILE_NAME = "extra_record_file_name"
    }
}

@Composable
private fun RtpPlayerScreen(
    onPlayerCreated: (RtpVlcPlayer) -> Unit,
    isInPipMode: Boolean,
    onEnterPip: () -> Unit,
    initialRecordFileName: String? = null,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    var playerState by remember { mutableStateOf(PlayerState()) }
    var streamUri by rememberSaveable { mutableStateOf(DEFAULT_STREAM_URI) }
    var networkCaching by rememberSaveable { mutableStateOf("500") }
    var clockJitter by rememberSaveable { mutableStateOf("0") }
    var clockSynchro by rememberSaveable { mutableStateOf("0") }
    var selectedDemux by rememberSaveable { mutableStateOf("ts") }

    val player = remember {
        if (isPreview) {
            null
        } else {
            RtpVlcPlayer(
                context = context.applicationContext,
                onStateChanged = { playerState = it },
            ).also { onPlayerCreated(it) }
        }
    }

    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(playerState.isRecording) {
        if (wasRecording && !playerState.isRecording) {
            player?.currentRecordPath?.let { path ->
                delay(1000)
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> }
                Toast.makeText(context, "Recording saved", Toast.LENGTH_LONG).show()
            }
        }
        wasRecording = playerState.isRecording
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    RtpPlayerContent(
        playerState = playerState,
        isInPipMode = isInPipMode,
        streamUri = streamUri,
        onStreamUriChange = { streamUri = it },
        networkCaching = networkCaching,
        onNetworkCachingChange = { networkCaching = it },
        clockJitter = clockJitter,
        onClockJitterChange = { clockJitter = it },
        clockSynchro = clockSynchro,
        onClockSynchroChange = { clockSynchro = it },
        selectedDemux = selectedDemux,
        onDemuxChange = { selectedDemux = it },
        onPlay = {
            player?.play(
                uri = streamUri.trim(),
                settings = PlayerSettings(
                    networkCaching = networkCaching.toIntOrNull() ?: 500,
                    clockJitter = clockJitter.toIntOrNull() ?: 0,
                    clockSynchro = clockSynchro.toIntOrNull() ?: 0,
                    demux = selectedDemux,
                ),
            )
        },
        onStop = { player?.stop() },
        onRecord = {
            val fileName = if (!initialRecordFileName.isNullOrBlank()) {
                if (initialRecordFileName.endsWith(".mp4")) {
                    initialRecordFileName
                } else {
                    "$initialRecordFileName.mp4"
                }
            } else {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                "RTP_Player_$timeStamp.mp4"
            }
            val storageDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val file = File(storageDir, fileName)

            player?.play(
                uri = streamUri.trim(),
                settings = PlayerSettings(
                    networkCaching = networkCaching.toIntOrNull() ?: 500,
                    clockJitter = clockJitter.toIntOrNull() ?: 0,
                    clockSynchro = clockSynchro.toIntOrNull() ?: 0,
                    demux = selectedDemux,
                ),
                recordPath = file.absolutePath,
            )
        },
        onEnterPip = onEnterPip,
        videoSurface = { modifier ->
            if (isPreview) {
                Box(
                    modifier = modifier.background(Color.DarkGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Video Surface", color = Color.LightGray)
                }
            } else {
                AndroidView(
                    modifier = modifier,
                    factory = { ctx -> SurfaceView(ctx).also { player?.setSurfaceView(it) } },
                    onRelease = { player?.setSurfaceView(null) },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RtpPlayerContent(
    playerState: PlayerState,
    isInPipMode: Boolean,
    streamUri: String,
    onStreamUriChange: (String) -> Unit,
    networkCaching: String,
    onNetworkCachingChange: (String) -> Unit,
    clockJitter: String,
    onClockJitterChange: (String) -> Unit,
    clockSynchro: String,
    onClockSynchroChange: (String) -> Unit,
    selectedDemux: String,
    onDemuxChange: (String) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onEnterPip: () -> Unit,
    videoSurface: @Composable (Modifier) -> Unit,
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (!isInPipMode) Modifier.safeDrawingPadding() else Modifier),
    ) {
        videoSurface(Modifier.fillMaxSize())

        if (!isInPipMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    .padding(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = statusText(playerState), color = Color.White)
                    if (playerState.isRecording) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(Color.Red, MaterialTheme.shapes.extraSmall),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        MaterialTheme.shapes.medium,
                    ),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                }

                if (playerState.isPlaying) {
                    Button(onClick = onEnterPip) { Text("PiP") }
                }

                if (playerState.isPlaying || playerState.isBuffering) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Stop")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPlay) { Text("Play") }
                        Button(
                            onClick = onRecord,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        ) {
                            Text("REC")
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (showSettingsDialog) {
            SettingsDialog(
                streamUri = streamUri,
                onStreamUriChange = onStreamUriChange,
                networkCaching = networkCaching,
                onNetworkCachingChange = onNetworkCachingChange,
                clockJitter = clockJitter,
                onClockJitterChange = onClockJitterChange,
                clockSynchro = clockSynchro,
                onClockSynchroChange = onClockSynchroChange,
                selectedDemux = selectedDemux,
                onDemuxChange = onDemuxChange,
                onDismiss = { showSettingsDialog = false },
            )
        }

        if (playerState.isBuffering && !isInPipMode) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    streamUri: String,
    onStreamUriChange: (String) -> Unit,
    networkCaching: String,
    onNetworkCachingChange: (String) -> Unit,
    clockJitter: String,
    onClockJitterChange: (String) -> Unit,
    clockSynchro: String,
    onClockSynchroChange: (String) -> Unit,
    selectedDemux: String,
    onDemuxChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Stream Settings", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = streamUri,
                    onValueChange = onStreamUriChange,
                    label = { Text("Stream URI") },
                    modifier = Modifier.fillMaxWidth(),
                )

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selectedDemux,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Demuxer") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        listOf("auto", "ts", "h264", "avcodec").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onDemuxChange(option)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = networkCaching,
                        onValueChange = onNetworkCachingChange,
                        label = { Text("Cache") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = clockJitter,
                        onValueChange = onClockJitterChange,
                        label = { Text("Jitter") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = clockSynchro,
                    onValueChange = onClockSynchroChange,
                    label = { Text("Clock Synchro") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

private fun statusText(state: PlayerState): String = when {
    state.errorMessage != null -> "Error"
    state.isPlaying -> "Playing"
    state.isBuffering -> "Buffering..."
    else -> "Idle"
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
private fun PreviewRtpPlayerContent() {
    RtpPlayerTheme {
        RtpPlayerContent(
            playerState = PlayerState(isPlaying = true),
            isInPipMode = false,
            streamUri = DEFAULT_STREAM_URI,
            onStreamUriChange = {},
            networkCaching = "500",
            onNetworkCachingChange = {},
            clockJitter = "0",
            onClockJitterChange = {},
            clockSynchro = "0",
            onClockSynchroChange = {},
            selectedDemux = "ts",
            onDemuxChange = {},
            onPlay = {},
            onStop = {},
            onRecord = {},
            onEnterPip = {},
            videoSurface = { modifier ->
                Box(
                    modifier = modifier.background(Color.DarkGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Video Preview", color = Color.White)
                }
            },
        )
    }
}

private const val DEFAULT_STREAM_URI = "udp://@:5004"

package com.github.walma.rtpplayer.ui

import android.media.MediaScannerConnection
import android.net.Uri
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.github.walma.rtpplayer.ui.theme.RtpPlayerDefaults
import com.github.walma.rtpplayer.ui.theme.RtpPlayerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RtpPlayerControlsStyle {
    Icons,
    Text,
}

data class RtpPlayerUiConfig(
    val titleText: String = "Cam1",
    val statusPrefixText: String? = null,
    val controlsStyle: RtpPlayerControlsStyle = RtpPlayerControlsStyle.Icons,
    val showRecordButton: Boolean = true,
)

@Composable
fun RtpPlayerScreen(
    modifier: Modifier = Modifier,
    isInPipMode: Boolean,
    onEnterPip: () -> Unit,
    initialRecordFileName: String? = null,
    initialStreamUri: String = DEFAULT_STREAM_URI,
    initialNetworkCaching: String = "500",
    initialClockJitter: String = "0",
    initialClockSynchro: String = "0",
    initialDemux: String = "ts",
    uiConfig: RtpPlayerUiConfig = RtpPlayerUiConfig(),
    topStartContent: (@Composable BoxScope.() -> Unit)? = null,
    bottomStartContent: (@Composable BoxScope.(PlayerState) -> Unit)? = null,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    var playerState by remember { mutableStateOf(PlayerState()) }
    var streamUri by rememberSaveable { mutableStateOf(initialStreamUri) }
    var networkCaching by rememberSaveable { mutableStateOf(initialNetworkCaching) }
    var clockJitter by rememberSaveable { mutableStateOf(initialClockJitter) }
    var clockSynchro by rememberSaveable { mutableStateOf(initialClockSynchro) }
    var selectedDemux by rememberSaveable { mutableStateOf(initialDemux) }
    var recordingsFolderUri by rememberSaveable { mutableStateOf(RecordingStorage.getSelectedFolderUri(context)) }
    var pendingRecording by remember { mutableStateOf<PendingRecording?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        runCatching {
            RecordingStorage.persistSelectedFolder(context, uri)
        }.onSuccess {
            recordingsFolderUri = uri
        }.onFailure {
            Toast.makeText(
                context,
                it.message ?: "Failed to access selected folder",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val player = remember {
        if (isPreview) {
            null
        } else {
            RtpVlcPlayer(
                context = context.applicationContext,
                onStateChanged = { playerState = it },
            )
        }
    }

    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(playerState.isRecording) {
        if (wasRecording && !playerState.isRecording) {
            pendingRecording?.let { recording ->
                delay(1000)
                val saveResult = withContext(Dispatchers.IO) {
                    RecordingStorage.saveRecording(
                        context = context,
                        sourceFile = recording.tempFile,
                        fileName = recording.fileName,
                        destinationFolderUri = recording.destinationFolderUri,
                    )
                }

                when (saveResult) {
                    is SaveResult.FilePath -> {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(saveResult.file.absolutePath),
                            null,
                        ) { _, _ -> }
                        Toast.makeText(
                            context,
                            "Recording saved: ${saveResult.file.name}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }

                    is SaveResult.Document -> {
                        Toast.makeText(context, "Recording saved", Toast.LENGTH_LONG).show()
                    }

                    is SaveResult.Error -> {
                        Toast.makeText(
                            context,
                            saveResult.message,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }

                pendingRecording = null
            }
        }
        wasRecording = playerState.isRecording
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    RtpPlayerContent(
        modifier = modifier,
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
            val fileName = resolveRecordFileName(initialRecordFileName)
            val storageDir = RecordingStorage.defaultRecordingDirectory(context)
            val file = File(storageDir, fileName)
            pendingRecording = PendingRecording(
                tempFile = file,
                fileName = fileName,
                destinationFolderUri = recordingsFolderUri,
            )

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
        uiConfig = uiConfig,
        recordingsFolderLabel = RecordingStorage.folderLabel(context, recordingsFolderUri),
        onSelectRecordingsFolder = {
            folderPickerLauncher.launch(recordingsFolderUri)
        },
        onResetRecordingsFolder = {
            RecordingStorage.clearSelectedFolder(context)
            recordingsFolderUri = null
        },
        topStartContent = topStartContent,
        bottomStartContent = bottomStartContent,
        videoSurface = { surfaceModifier ->
            if (isPreview) {
                Box(
                    modifier = surfaceModifier.background(Color.DarkGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Video Surface", color = Color.LightGray)
                }
            } else {
                AndroidView(
                    modifier = surfaceModifier,
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
    modifier: Modifier,
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
    uiConfig: RtpPlayerUiConfig,
    recordingsFolderLabel: String,
    onSelectRecordingsFolder: () -> Unit,
    onResetRecordingsFolder: () -> Unit,
    topStartContent: (@Composable BoxScope.() -> Unit)?,
    bottomStartContent: (@Composable BoxScope.(PlayerState) -> Unit)?,
    videoSurface: @Composable (Modifier) -> Unit,
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (!isInPipMode) Modifier.safeDrawingPadding() else Modifier),
    ) {
        videoSurface(Modifier.fillMaxSize())

        if (!isInPipMode) {
            if (topStartContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    content = topStartContent,
                )
            } else {
                DefaultTopStartOverlay(text = uiConfig.titleText)
            }

            if (bottomStartContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    bottomStartContent(playerState)
                }
            } else {
                DefaultBottomStartOverlay(
                    playerState = playerState,
                    statusPrefixText = uiConfig.statusPrefixText,
                )
            }

            ControlsColumn(
                playerState = playerState,
                controlsStyle = uiConfig.controlsStyle,
                showRecordButton = uiConfig.showRecordButton,
                onSettings = { showSettingsDialog = true },
                onEnterPip = onEnterPip,
                onPlay = onPlay,
                onStop = onStop,
                onRecord = onRecord,
            )
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
                recordingsFolderLabel = recordingsFolderLabel,
                onSelectRecordingsFolder = onSelectRecordingsFolder,
                onResetRecordingsFolder = onResetRecordingsFolder,
                onDismiss = { showSettingsDialog = false },
            )
        }

        if (playerState.isBuffering && !isInPipMode) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun BoxScope.DefaultTopStartOverlay(text: String) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(8.dp),
    ) {
        Text(text, color = Color.White)
    }
}

@Composable
private fun BoxScope.DefaultBottomStartOverlay(
    playerState: PlayerState,
    statusPrefixText: String?,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val baseStatus = statusText(playerState)
            val text = if (statusPrefixText.isNullOrBlank()) baseStatus else "$statusPrefixText: $baseStatus"
            Text(text = text, color = Color.White)
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
}

@Composable
private fun BoxScope.ControlsColumn(
    playerState: PlayerState,
    controlsStyle: RtpPlayerControlsStyle,
    showRecordButton: Boolean,
    onSettings: () -> Unit,
    onEnterPip: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerActionButton(
            style = controlsStyle,
            label = "Settings",
            icon = {
                Icon(Icons.Default.Settings, contentDescription = null)
            },
            onClick = onSettings,
        )

        if (playerState.isPlaying) {
            PlayerActionButton(
                style = controlsStyle,
                label = "PiP",
                icon = {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = null)
                },
                onClick = onEnterPip,
            )
        }

        if (playerState.isPlaying || playerState.isBuffering) {
            PlayerActionButton(
                style = controlsStyle,
                label = "Stop",
                icon = {
                    Icon(Icons.Default.Stop, contentDescription = null)
                },
                onClick = onStop,
            )
        } else {
            PlayerActionButton(
                style = controlsStyle,
                label = "Play",
                icon = {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                },
                onClick = onPlay,
            )

            if (showRecordButton) {
                if (controlsStyle == RtpPlayerControlsStyle.Text) {
                    Button(
                        onClick = onRecord,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    ) {
                        Text("Record")
                    }
                } else {
                    Button(
                        onClick = onRecord,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    ) {
                        Text("REC")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    style: RtpPlayerControlsStyle,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    when (style) {
        RtpPlayerControlsStyle.Icons -> {
            IconButton(
                onClick = onClick,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    MaterialTheme.shapes.medium,
                ),
            ) {
                icon()
            }
        }

        RtpPlayerControlsStyle.Text -> {
            OutlinedButton(onClick = onClick) {
                Text(label)
            }
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
    recordingsFolderLabel: String,
    onSelectRecordingsFolder: () -> Unit,
    onResetRecordingsFolder: () -> Unit,
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
                OutlinedTextField(
                    value = recordingsFolderLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Recordings Folder") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSelectRecordingsFolder,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Choose Folder")
                    }
                    OutlinedButton(
                        onClick = onResetRecordingsFolder,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Default Folder")
                    }
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

private fun resolveRecordFileName(initialRecordFileName: String?): String {
    return if (!initialRecordFileName.isNullOrBlank()) {
        if (initialRecordFileName.endsWith(".mp4")) {
            initialRecordFileName
        } else {
            "$initialRecordFileName.mp4"
        }
    } else {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        "RTP_Player_$timeStamp.mp4"
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
    RtpPlayerTheme(colors = RtpPlayerDefaults.colors()) {
        RtpPlayerScreen(
            isInPipMode = false,
            onEnterPip = {},
        )
    }
}

private const val DEFAULT_STREAM_URI = "udp://@:5004"

private data class PendingRecording(
    val tempFile: File,
    val fileName: String,
    val destinationFolderUri: Uri?,
)

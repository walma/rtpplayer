package com.github.walma.rtpplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.github.walma.rtpplayer.ui.theme.RtpPlayerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
                        initialRecordFileName = initialRecordFileName
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RtpPlayerScreen(
    onPlayerCreated: (RtpVlcPlayer) -> Unit,
    isInPipMode: Boolean,
    onEnterPip: () -> Unit,
    initialRecordFileName: String? = null
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    
    var playerState by remember { mutableStateOf(PlayerState()) }
    var streamUri by rememberSaveable { mutableStateOf(DEFAULT_STREAM_URI) }
    
    var networkCaching by rememberSaveable { mutableStateOf("500") }
    var clockJitter by rememberSaveable { mutableStateOf("0") }
    var clockSynchro by rememberSaveable { mutableStateOf("0") }
    var selectedDemux by rememberSaveable { mutableStateOf("ts") }
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val player = remember {
        if (isPreview) null else {
            RtpVlcPlayer(
                context = context.applicationContext,
                onStateChanged = { playerState = it }
            ).also { onPlayerCreated(it) }
        }
    }

    // Единая точка обработки завершения записи (и по кнопке STOP, и при обрыве)
    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(playerState.isRecording) {
        if (wasRecording && !playerState.isRecording) {
            player?.currentRecordPath?.let { path ->
                // Ждем 1 секунду, чтобы VLC закрыл файл корректно
                kotlinx.coroutines.delay(1000)
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { scannedPath, _ ->
                    Log.d("RTP_REC", "File finalized and scanned: $scannedPath")
                }
                Toast.makeText(context, "Recording saved to Downloads", Toast.LENGTH_LONG).show()
            }
        }
        wasRecording = playerState.isRecording
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    // Слушатель ошибок для уведомлений
    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (!isInPipMode) Modifier.safeDrawingPadding() else Modifier),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { androidContext ->
                SurfaceView(androidContext).also { surfaceView ->
                    player?.setSurfaceView(surfaceView)
                }
            },
            onRelease = { player?.setSurfaceView(null) }
        )

        if (!isInPipMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = statusText(playerState), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (playerState.isRecording) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(Color.Red, MaterialTheme.shapes.small))
                        Spacer(Modifier.width(4.dp))
                        Text("REC", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                    }
                }
                playerState.debugMessage?.let {
                    Text(text = it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
                
                if (playerState.isPlaying) {
                    Button(onClick = onEnterPip) { Text("PiP") }
                }

                if (playerState.isPlaying || playerState.isBuffering) {
                    Button(
                        onClick = { 
                            val path = player?.currentRecordPath
                            player?.stop()
                            if (path != null) {
                                MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> }
                                Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Stop") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val uri = streamUri.trim()
                                if (uri.isNotBlank()) {
                                    player?.play(
                                        uri = uri,
                                        settings = PlayerSettings(
                                            networkCaching = networkCaching.toIntOrNull() ?: 500,
                                            clockJitter = clockJitter.toIntOrNull() ?: 0,
                                            clockSynchro = clockSynchro.toIntOrNull() ?: 0,
                                            demux = selectedDemux
                                        )
                                    )
                                }
                            }
                        ) { Text("Play") }

                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                            onClick = {
                                val uri = streamUri.trim()
                                if (uri.isNotBlank()) {
                                    val fileName = if (!initialRecordFileName.isNullOrBlank()) {
                                        if (initialRecordFileName.endsWith(".mp4")) initialRecordFileName else "$initialRecordFileName.mp4"
                                    } else {
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        "RTP_Player_$timeStamp.mp4"
                                    }
                                    
                                    // Используем публичную папку Downloads
                                    val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    if (!storageDir.exists()) storageDir.mkdirs()
                                    val file = File(storageDir, fileName)
                                    
                                    player?.play(
                                        uri = uri,
                                        settings = PlayerSettings(
                                            networkCaching = networkCaching.toIntOrNull() ?: 500,
                                            clockJitter = clockJitter.toIntOrNull() ?: 0,
                                            clockSynchro = clockSynchro.toIntOrNull() ?: 0,
                                            demux = selectedDemux
                                        ),
                                        recordPath = file.absolutePath
                                    )
                                    Toast.makeText(context, "Recording started...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) { Text("REC") }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showSettingsDialog) {
            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Stream Settings", style = MaterialTheme.typography.titleLarge)
                        
                        OutlinedTextField(
                            value = streamUri,
                            onValueChange = { streamUri = it },
                            label = { Text("Stream URI") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Demux Dropdown
                        var expanded by remember { mutableStateOf(false) }
                        val demuxOptions = listOf("auto", "ts", "h264", "avcodec")
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedDemux,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Demuxer") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                demuxOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedDemux = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = networkCaching,
                                onValueChange = { if (it.all { char -> char.isDigit() }) networkCaching = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Cache") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = clockJitter,
                                onValueChange = { if (it.all { char -> char.isDigit() }) clockJitter = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Jitter") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = clockSynchro,
                            onValueChange = { if (it.isEmpty() || it == "-" || it.toIntOrNull() != null) clockSynchro = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Clock Synchro") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Button(
                            onClick = { showSettingsDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Done") }
                    }
                }
            }
        }

        if (playerState.isBuffering && !isInPipMode) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun statusText(state: PlayerState): String {
    return when {
        state.errorMessage != null -> "Error"
        state.isPlaying -> "Playing"
        state.isBuffering -> "Buffering..."
        else -> "Idle"
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewRtpPlayerScreen() {
    RtpPlayerTheme {
        RtpPlayerScreen(
            onPlayerCreated = {},
            isInPipMode = false,
            onEnterPip = {},
            initialRecordFileName = null
        )
    }
}

private const val DEFAULT_STREAM_URI = "udp://@:5004"

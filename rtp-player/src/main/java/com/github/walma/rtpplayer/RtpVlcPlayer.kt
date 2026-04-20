package com.github.walma.rtpplayer

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class RtpVlcPlayer(
    context: Context,
    private val onStateChanged: (PlayerState) -> Unit,
) {
    private val libVlc = LibVLC(context, VLC_OPTIONS)
    private val mediaPlayer = MediaPlayer(libVlc)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = Runnable {
        if (isPlayingActive) {
            dispatchState(PlayerState(errorMessage = "Stream timeout (no data)"))
            stop()
        }
    }

    private val multicastLock: WifiManager.MulticastLock? = try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.createMulticastLock("RtpVlcPlayerLock").apply { setReferenceCounted(true) }
    } catch (e: Exception) { null }

    private var isReleased = false
    private var lastUri: String? = null
    private var isPlayingActive = false 
    private var currentSettings = PlayerSettings()
    var currentRecordPath: String? = null
        private set

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            val vout = mediaPlayer.vlcVout
            vout.setVideoSurface(holder.surface, holder)
            vout.attachViews()
            if (isPlayingActive) {
                lastUri?.let { internalPlay(it, currentSettings) }
            }
        }
        override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
            mediaPlayer.vlcVout.setWindowSize(w, h)
        }
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (isPlayingActive) {
                mediaPlayer.stop()
            }
            mediaPlayer.vlcVout.detachViews()
        }
    }

    init {
        mediaPlayer.setEventListener(::handlePlayerEvent)
    }

    fun setSurfaceView(surfaceView: SurfaceView?) {
        if (isReleased) return
        mediaPlayer.vlcVout.detachViews()
        if (surfaceView != null) {
            surfaceView.holder.addCallback(surfaceCallback)
            if (surfaceView.holder.surface?.isValid == true) {
                surfaceCallback.surfaceCreated(surfaceView.holder)
            }
        }
    }

    fun play(uri: String, settings: PlayerSettings, recordPath: String? = null) {
        if (isReleased) return
        isPlayingActive = true
        lastUri = uri
        currentSettings = settings
        this.currentRecordPath = recordPath
        
        resetWatchdog(15000L)
        internalPlay(uri, settings)
    }

    private fun internalPlay(uri: String, settings: PlayerSettings) {
        try { if (multicastLock?.isHeld == false) multicastLock.acquire() } catch (e: Exception) {}

        try {
            val playUri = if (uri.startsWith("udp://") && !uri.contains("@")) {
                val port = uri.substringAfterLast(":", "5004")
                "udp://@:$port"
            } else uri

            val media = Media(libVlc, Uri.parse(playUri)).apply {
                if (settings.demux != "auto") {
                    addOption(":demux=${settings.demux}")
                }
                addOption(":network-caching=${settings.networkCaching}")
                addOption(":clock-jitter=${settings.clockJitter}")
                addOption(":clock-synchro=${settings.clockSynchro}")
                
                addOption(":rtp-timeout=5")
                addOption(":udp-timeout=5")

                currentRecordPath?.let { path ->
                    addOption(":sout=#duplicate{dst=display,dst=std{access=file,mux=mp4{movflags=frag_keyframe+empty_moov+default_base_moof},dst='$path'}}")
                    addOption(":sout-keep")
                }

                setHWDecoderEnabled(true, false)
            }

            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            mediaPlayer.scale = 0f
            mediaPlayer.aspectRatio = null
            
            dispatchState(PlayerState(
                isBuffering = true, 
                isRecording = currentRecordPath != null,
                debugMessage = "Connecting..."
            ))
        } catch (error: Throwable) {
            dispatchState(PlayerState(errorMessage = "Play error: ${error.message}"))
        }
    }

    fun stop() {
        val wasRecordingActive = currentRecordPath != null // Проверяем, была ли активна запись
        isPlayingActive = false
        watchdogHandler.removeCallbacks(watchdogRunnable)
        if (isReleased) return
        try {
            // Если была запись, даем VLC немного времени на финализацию файла
            if (wasRecordingActive) {
                Thread.sleep(500) // 500 мс - разумная задержка
            }
            mediaPlayer.stop()
            if (multicastLock?.isHeld == true) {
                try { multicastLock.release() } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        
        // Обнуляем currentRecordPath только после остановки и возможной задержки
        currentRecordPath = null
        dispatchState(PlayerState()) // Это установит isRecording = false
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        stop()
        mediaPlayer.setEventListener(null)
        mediaPlayer.vlcVout.detachViews()
        mediaPlayer.release()
        libVlc.release()
    }

    private fun handlePlayerEvent(event: MediaPlayer.Event) {
        if (isReleased) return
        when (event.type) {
            MediaPlayer.Event.TimeChanged -> {
                resetWatchdog(10000L) 
            }
            MediaPlayer.Event.Buffering -> {
                val p = event.buffering.toInt()
                if (p > 0) resetWatchdog(10000L) 
                
                dispatchState(PlayerState(
                    isBuffering = p < 100, 
                    isPlaying = p >= 100, 
                    isRecording = currentRecordPath != null,
                    debugMessage = "Buffering $p%"
                ))
            }
            MediaPlayer.Event.Playing -> {
                resetWatchdog(10000L)
                dispatchState(PlayerState(
                    isPlaying = true, 
                    isRecording = currentRecordPath != null,
                    debugMessage = "Active"
                ))
            }
            MediaPlayer.Event.EncounteredError -> {
                watchdogHandler.removeCallbacks(watchdogRunnable)
                dispatchState(PlayerState(errorMessage = "Stream lost", isRecording = false))
                stop() 
            }
            MediaPlayer.Event.EndReached -> {
                watchdogHandler.removeCallbacks(watchdogRunnable)
                dispatchState(PlayerState(errorMessage = "Stream ended", isRecording = false))
                stop()
            }
            else -> {}
        }
    }

    private fun resetWatchdog(timeoutMs: Long) {
        watchdogHandler.removeCallbacks(watchdogRunnable)
        watchdogHandler.postDelayed(watchdogRunnable, timeoutMs)
    }

    private fun dispatchState(state: PlayerState) {
        if (!isReleased) mainHandler.post { onStateChanged(state) }
    }

    companion object {
        private val VLC_OPTIONS = arrayListOf(
            "--ipv4", 
            "--aout=opensles", 
            "--no-stats",
            "--drop-late-frames",
            "--skip-frames"
        )
    }
}

data class PlayerSettings(
    val networkCaching: Int = 500,
    val clockJitter: Int = 0,
    val clockSynchro: Int = 0,
    val demux: String = "ts"
)

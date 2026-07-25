package dev.chardoncs.ezmpv.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "Player"
private const val MPV_EVENT_END_FILE = 7
private const val MPV_EVENT_FILE_LOADED = 8

class Player(private val context: Context) {

    var onTrackEnd: ((Int?) -> Unit)? = null
    private var eofHandled = false
    private var playbackEnded = false
    private var awaitingFileLoaded = false
    private var surfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null
    private var voAttached = false
    private var videoDecodeEnabled = true

    private var mpv: MPVLib? = null
    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {}
        override fun eventProperty(property: String, value: Long) {
            if (property == "time-pos") {
                _state.update { it.copy(positionMs = value * 1000) }
            }
        }
        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> if (!playbackEnded || value) {
                    _state.update { it.copy(isPlaying = !value) }
                }
                "eof-reached" -> if (value && !awaitingFileLoaded) handleEndFile()
            }
        }
        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> _state.update { it.copy(positionMs = (value * 1000).toLong()) }
                "duration" -> _state.update { it.copy(durationMs = (value * 1000).toLong()) }
            }
        }
        override fun eventProperty(property: String, value: String) {}
        override fun event(eventId: Int) {
            when (eventId) {
                MPV_EVENT_END_FILE -> if (!awaitingFileLoaded) handleEndFile()
                MPV_EVENT_FILE_LOADED -> awaitingFileLoaded = false
            }
        }
    }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val isCreated: Boolean get() = mpv != null

    fun start() {
        if (mpv != null) return
        val m = MPVLib.create(context) ?: run {
            Log.e(TAG, "MPVLib.create returned null")
            return
        }
        try {
            m.setOptionString("config", "yes")
            m.setOptionString("force-window", "no")
            m.setOptionString("vo", "null")
            m.setOptionString("vid", "auto")
            m.setOptionString("aid", "auto")
            m.setOptionString("keepaspect", "yes")
            m.setOptionString("keep-open", "yes")
            m.setOptionString("idle", "yes")
            m.init()
            m.addObserver(observer)
            m.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            m.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            m.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            m.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv = m
            Log.i(TAG, "player started")
            ensureVideoOutput()
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            runCatching { m.destroy() }
        }
    }

    fun stop() {
        runCatching { videoSurface?.release() }
        runCatching { surfaceTexture?.release() }
        videoSurface = null
        surfaceTexture = null
        voAttached = false
        mpv?.let { m ->
            runCatching { m.removeObserver(observer) }
            runCatching { m.destroy() }
        }
        mpv = null
        eofHandled = false
        playbackEnded = false
        awaitingFileLoaded = false
        _state.update {
            PlayerState(
                playlist = it.playlist,
                selectedFolders = it.selectedFolders,
                audioOnly = it.audioOnly,
            )
        }
    }

    fun loadFile(path: String, index: Int) {
        val m = mpv ?: return
        eofHandled = false
        playbackEnded = false
        awaitingFileLoaded = true
        _state.update { it.copy(currentIndex = index, positionMs = 0, durationMs = 0) }
        m.command(arrayOf("loadfile", path, "replace"))
        m.setPropertyBoolean("pause", false)
    }

    fun setPlaylist(items: List<MediaItem>) {
        _state.update { it.copy(playlist = items) }
    }

    fun playPause() {
        val m = mpv ?: return
        val paused = m.getPropertyBoolean("pause") ?: false
        if (paused) {
            if (playbackEnded) m.command(arrayOf("seek", "0", "absolute"))
            playbackEnded = false
            eofHandled = false
        }
        m.setPropertyBoolean("pause", !paused)
    }

    fun seekTo(ms: Long) {
        playbackEnded = false
        eofHandled = false
        val seconds = ms / 1000.0
        mpv?.command(arrayOf("seek", "%.3f".format(seconds), "absolute"))
        _state.update { it.copy(positionMs = ms) }
    }

    fun setVideoRightMarginRatio(ratio: Float) {
        val m = mpv ?: return
        runCatching { m.setPropertyDouble("video-margin-ratio-right", ratio.coerceIn(0f, 0.95f).toDouble()) }
    }

    fun setAudioOnly(audioOnly: Boolean) {
        val m = mpv ?: return
        _state.update { it.copy(audioOnly = audioOnly) }
        if (audioOnly) {
            m.setPropertyString("vid", "no")
        } else {
            ensureVideoOutput()
            if (voAttached && videoDecodeEnabled) m.setPropertyString("vid", "auto")
        }
    }

    fun setVideoDecodeEnabled(enabled: Boolean) {
        videoDecodeEnabled = enabled
        val m = mpv ?: return
        if (_state.value.audioOnly || !voAttached) return
        m.setPropertyString("vid", if (enabled) "auto" else "no")
    }

    fun acquireVideoTexture(): SurfaceTexture {
        surfaceTexture?.takeUnless { it.isReleased }?.let { return it }
        runCatching { videoSurface?.release() }
        val st = SurfaceTexture(0)
        runCatching { st.detachFromGLContext() }
        st.setDefaultBufferSize(1280, 720)
        surfaceTexture = st
        videoSurface = Surface(st)
        voAttached = false
        ensureVideoOutput()
        return st
    }

    fun resizeVideoSurface(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        surfaceTexture?.setDefaultBufferSize(width, height)
        mpv?.let { runCatching { it.setPropertyString("android-surface-size", "${width}x$height") } }
    }

    private fun ensureVideoOutput() {
        val m = mpv ?: return
        val s = videoSurface ?: return
        if (voAttached || _state.value.audioOnly) return
        m.attachSurface(s)
        m.setPropertyString("vo", "gpu")
        m.setPropertyString("vid", if (videoDecodeEnabled) "auto" else "no")
        m.setPropertyString("force-window", "yes")
        voAttached = true
    }

    private fun handleEndFile() {
        if (eofHandled) return
        eofHandled = true
        playbackEnded = true
        val current = _state.value
        val next = current.currentIndex + 1
        mpv?.setPropertyBoolean("pause", true)
        _state.update { it.copy(isPlaying = false) }
        onTrackEnd?.invoke(next.takeIf { it in current.playlist.indices })
    }
}

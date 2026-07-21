package dev.chardoncs.ezmpv.player

import android.content.Context
import android.util.Log
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "Player"

class Player(private val context: Context) {

    var onTrackEnd: (() -> Unit)? = null
    private var eofHandled = false
    private var surface: Surface? = null
    private var hasSurfaceAttached = false

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
                "pause" -> _state.update { it.copy(isPlaying = !value) }
                "eof-reached" -> if (value && !eofHandled) {
                    eofHandled = true
                    onEof()
                }
            }
        }
        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> _state.update { it.copy(positionMs = (value * 1000).toLong()) }
                "duration" -> _state.update { it.copy(durationMs = (value * 1000).toLong()) }
            }
        }
        override fun eventProperty(property: String, value: String) {}
        override fun event(eventId: Int) {}
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
            m.setOptionString("force-window", "auto")
            m.setOptionString("vo", "gpu")
            m.setOptionString("vid", "auto")
            m.setOptionString("aid", "auto")
            m.setOptionString("idle", "yes")
            m.init()
            m.addObserver(observer)
            m.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            m.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            m.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            m.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv = m
            Log.i(TAG, "player started")
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            runCatching { m.destroy() }
        }
    }

    fun stop() {
        surface = null
        hasSurfaceAttached = false
        mpv?.let { m ->
            runCatching { m.removeObserver(observer) }
            runCatching { m.destroy() }
        }
        mpv = null
        eofHandled = false
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
        _state.update { it.copy(currentIndex = index, positionMs = 0, durationMs = 0) }
        m.command(arrayOf("loadfile", path, "replace"))
    }

    fun setPlaylist(items: List<MediaItem>) {
        _state.update { it.copy(playlist = items) }
    }

    fun playPause() {
        val m = mpv ?: return
        val paused = m.getPropertyBoolean("pause") ?: false
        m.setPropertyBoolean("pause", !paused)
    }

    fun seekTo(ms: Long) {
        val seconds = ms / 1000.0
        mpv?.command(arrayOf("seek", "%.3f".format(seconds), "absolute"))
        _state.update { it.copy(positionMs = ms) }
    }

    fun setAudioOnly(audioOnly: Boolean) {
        val m = mpv ?: return
        _state.update { it.copy(audioOnly = audioOnly) }
        if (audioOnly) {
            if (hasSurfaceAttached) {
                m.detachSurface()
                hasSurfaceAttached = false
            }
            m.setPropertyString("vid", "no")
            m.setPropertyString("vo", "null")
            m.setPropertyString("force-window", "no")
        } else {
            m.setPropertyString("vo", "gpu")
            m.setPropertyString("vid", "auto")
            m.setPropertyString("force-window", "auto")
            surface?.let {
                m.attachSurface(it)
                m.setOptionString("force-window", "yes")
                hasSurfaceAttached = true
            }
        }
    }

    fun attachSurface(s: Surface) {
        val m = mpv ?: return
        surface = s
        if (!_state.value.audioOnly) {
            m.setPropertyString("vo", "gpu")
            m.setPropertyString("vid", "auto")
            m.attachSurface(s)
            m.setOptionString("force-window", "yes")
            hasSurfaceAttached = true
        }
    }

    fun detachSurface() {
        val m = mpv ?: return
        if (hasSurfaceAttached) {
            m.setPropertyString("vo", "null")
            m.setPropertyString("force-window", "no")
            m.detachSurface()
            hasSurfaceAttached = false
        }
        surface = null
    }

    private fun onEof() {
        val current = _state.value
        val next = current.currentIndex + 1
        if (next in current.playlist.indices) {
            _state.update { it.copy(currentIndex = next) }
            onTrackEnd?.invoke()
        } else {
            _state.update { it.copy(isPlaying = false) }
        }
    }
}
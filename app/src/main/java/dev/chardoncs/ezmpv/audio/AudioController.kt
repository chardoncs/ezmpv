package dev.chardoncs.ezmpv.audio

import android.content.Context
import android.util.Log
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "AudioController"

class AudioController(private val context: Context) {

    var onTrackEnd: (() -> Unit)? = null

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
                "eof-reached" -> if (value) onEof()
            }
        }
        override fun eventProperty(property: String, value: Double) {
            if (property == "time-pos") {
                _state.update { it.copy(positionMs = (value * 1000).toLong()) }
            } else if (property == "duration") {
                _state.update { it.copy(durationMs = (value * 1000).toLong()) }
            }
        }
        override fun eventProperty(property: String, value: String) {}
        override fun event(eventId: Int) {}
    }

    private val _state = MutableStateFlow(
        AudioUiState(
            isPlaying = false,
            positionMs = 0,
            durationMs = 0,
            currentIndex = -1,
        )
    )
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

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
            m.setOptionString("vid", "no")
            m.setOptionString("aid", "auto")
            m.setOptionString("idle", "once")
            m.init()
            m.addObserver(observer)
            m.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            m.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            m.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            m.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv = m
            Log.i(TAG, "audio controller started")
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            runCatching { m.destroy() }
        }
    }

    fun stop() {
        mpv?.let { m ->
            runCatching { m.removeObserver(observer) }
            runCatching { m.destroy() }
        }
        mpv = null
        _state.update { it.copy(isPlaying = false, positionMs = 0, currentIndex = -1) }
    }

    fun loadFile(path: String, index: Int) {
        val m = mpv ?: return
        _state.update { it.copy(currentIndex = index, positionMs = 0, durationMs = 0) }
        m.command(arrayOf("loadfile", path, "replace"))
    }

    fun setPlaylist(tracks: List<AudioTrack>) {
        _state.update { it.copy(playlist = tracks) }
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
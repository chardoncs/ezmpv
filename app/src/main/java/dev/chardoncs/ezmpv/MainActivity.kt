package dev.chardoncs.ezmpv

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import dev.chardoncs.ezmpv.player.VideoTarget
import dev.chardoncs.ezmpv.player.PlayerService
import dev.chardoncs.ezmpv.ui.EzmpvApp
import dev.chardoncs.ezmpv.ui.theme.EzmpvTheme
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val controller by lazy { (application as EzmpvApplication).playerController }

    private var hasVideo = false
    private var audioOnly = false
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { s ->
                    hasVideo = s.hasVideo
                    audioOnly = s.audioOnly
                    isPlaying = s.isPlaying
                    updatePipParams()
                }
            }
        }
        setContent {
            EzmpvTheme {
                EzmpvApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.setVideoDecodeEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        if (!controller.state.value.inPip) {
            controller.setVideoDecodeEnabled(false)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (hasVideo && !audioOnly && isPlaying) {
            runCatching { enterPictureInPictureMode(buildPipParams()) }
        }
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val shouldAutoEnter = hasVideo && !audioOnly && isPlaying
        val builder = PictureInPictureParams.Builder()
            .setAutoEnterEnabled(shouldAutoEnter)
            .setActions(listOf(prevAction(), playPauseAction(), nextAction()))
        val w = controller.player.videoWidth
        val h = controller.player.videoHeight
        if (w > 0 && h > 0) builder.setAspectRatio(Rational(w, h))
        else builder.setAspectRatio(Rational(16, 9))
        runCatching { setPictureInPictureParams(builder.build()) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setActions(listOf(prevAction(), playPauseAction(), nextAction()))
        val w = controller.player.videoWidth
        val h = controller.player.videoHeight
        if (w > 0 && h > 0) builder.setAspectRatio(Rational(w, h))
        else builder.setAspectRatio(Rational(16, 9))
        return builder.build()
    }

    private fun prevAction(): RemoteAction = RemoteAction(
        Icon.createWithResource(this, android.R.drawable.ic_media_previous),
        "Previous",
        "Previous track",
        controlPendingIntent(PlayerService.ACTION_PREV, PREV_REQUEST_CODE),
    )

    private fun playPauseAction(): RemoteAction {
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause
                      else android.R.drawable.ic_media_play
        val title = if (isPlaying) "Pause" else "Play"
        return RemoteAction(
            Icon.createWithResource(this, iconRes),
            title,
            "Play or pause",
            controlPendingIntent(PlayerService.ACTION_PLAY_PAUSE, PLAY_PAUSE_REQUEST_CODE),
        )
    }

    private fun nextAction(): RemoteAction = RemoteAction(
        Icon.createWithResource(this, android.R.drawable.ic_media_next),
        "Next",
        "Next track",
        controlPendingIntent(PlayerService.ACTION_NEXT, NEXT_REQUEST_CODE),
    )

    private fun controlPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlayerService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        controller.setInPip(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            controller.videoHost.clearTarget(VideoTarget.PIP)
        }
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    companion object {
        private const val PREV_REQUEST_CODE = 11
        private const val PLAY_PAUSE_REQUEST_CODE = 12
        private const val NEXT_REQUEST_CODE = 13
    }
}
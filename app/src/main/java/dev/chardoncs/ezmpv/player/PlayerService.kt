package dev.chardoncs.ezmpv.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.chardoncs.ezmpv.EzmpvApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@UnstableApi
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var adapter: MpvPlayerAdapter? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildPlaceholderNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        val controller = (application as EzmpvApplication).playerController
        controller.player.start()
        val a = MpvPlayerAdapter(controller, mainLooper)
        adapter = a
        mediaSession = MediaSession.Builder(this, a).build()
        scope.launch {
            controller.state.collect { a.refresh() }
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun buildPlaceholderNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ezmpv")
            .setContentText("Playing media")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        adapter = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "ezmpv_playback"
        private const val NOTIFICATION_ID = 1
    }
}

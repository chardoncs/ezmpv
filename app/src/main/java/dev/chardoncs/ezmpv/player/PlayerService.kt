package dev.chardoncs.ezmpv.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.chardoncs.ezmpv.MainActivity
import dev.chardoncs.ezmpv.EzmpvApplication
import dev.chardoncs.ezmpv.R
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
        val notificationProvider =
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_small) }
        setMediaNotificationProvider(notificationProvider)
        val controller = (application as EzmpvApplication).playerController
        controller.player.start()
        val a = MpvPlayerAdapter(controller, mainLooper)
        adapter = a
        val session = MediaSession.Builder(this, a)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        mediaSession = session
        runCatching {
            startForeground(
                NOTIFICATION_ID,
                buildMediaNotification(session),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }
        addSession(session)
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

    private fun buildMediaNotification(session: MediaSession): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ezmpv")
            .setContentText("Playing media")
            .setSmallIcon(R.drawable.ic_notification_small)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(Notification.MediaStyle().setMediaSession(session.platformToken))
            .setOngoing(true)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as EzmpvApplication).playerController.stopPlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        (application as EzmpvApplication).playerController.stopPlayback()
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

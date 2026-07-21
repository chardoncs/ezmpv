package dev.chardoncs.ezmpv.player

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
        val controller = (application as EzmpvApplication).playerController
        controller.player.start()
        val a = MpvPlayerAdapter(controller, mainLooper)
        adapter = a
        mediaSession = MediaSession.Builder(this, a).build()
        scope.launch {
            controller.state.collect { a.refresh() }
        }
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
}

package dev.chardoncs.ezmpv

import android.app.Application
import dev.chardoncs.ezmpv.player.PlayerController

class EzmpvApplication : Application() {
    val playerController: PlayerController by lazy { PlayerController(this) }
}
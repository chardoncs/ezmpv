package dev.chardoncs.ezmpv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chardoncs.ezmpv.ui.EzmpvApp
import dev.chardoncs.ezmpv.ui.theme.EzmpvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EzmpvTheme {
                EzmpvApp()
            }
        }
    }
}
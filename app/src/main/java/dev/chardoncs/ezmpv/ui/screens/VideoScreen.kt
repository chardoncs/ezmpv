package dev.chardoncs.ezmpv.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chardoncs.ezmpv.player.MpvSurface
import dev.chardoncs.ezmpv.player.rememberMpvController
import dev.jdtech.mpv.MPVLib
import java.io.File

private const val TAG = "mpv"

@Composable
fun VideoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Pick a video to play.") }
    var pickedFile by remember { mutableStateOf<File?>(null) }

    val controller = rememberMpvController(
        context = context,
        onConfigure = { mpv ->
            mpv.setOptionString("config", "yes")
            mpv.setOptionString("force-window", "no")
            mpv.setOptionString("idle", "once")
        },
    )

    // Forward mpv's internal log messages to Android logcat so we always have
    // visibility into what libmpv is doing (replaces `adb logcat -s mpv`).
    DisposableEffect(controller.mpv) {
        val mpv = controller.mpv
        if (mpv == null) {
            onDispose { }
        } else {
            val observer = object : MPVLib.LogObserver {
                override fun logMessage(prefix: String, level: Int, text: String) {
                    val priority = when (level) {
                        MPVLib.MpvLogLevel.MPV_LOG_LEVEL_FATAL, MPVLib.MpvLogLevel.MPV_LOG_LEVEL_ERROR -> Log.ERROR
                        MPVLib.MpvLogLevel.MPV_LOG_LEVEL_WARN -> Log.WARN
                        MPVLib.MpvLogLevel.MPV_LOG_LEVEL_INFO -> Log.INFO
                        else -> Log.VERBOSE
                    }
                    Log.println(priority, TAG, "[$prefix] ${text.trimEnd()}")
                }
            }
            mpv.addLogObserver(observer)
            onDispose { mpv.removeLogObserver(observer) }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            status = "No file selected."
            return@rememberLauncherForActivityResult
        }
        status = "Copying file to app storage…"
        val copied = copyContentUriToFile(context, uri)
        if (copied == null) {
            status = "Failed to copy file."
            return@rememberLauncherForActivityResult
        }
        pickedFile = copied
        status = "Ready: ${copied.name}"
    }

    LaunchedEffect(controller.isCreated, pickedFile) {
        if (controller.isCreated && pickedFile != null) {
            status = "Playing: ${pickedFile!!.name}"
            controller.mpv!!.command(arrayOf("loadfile", pickedFile!!.absolutePath))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        MpvSurface(
            controller = controller,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                enabled = controller.isCreated,
                onClick = { pickLauncher.launch(arrayOf("video/*", "audio/*")) },
            ) {
                Text("Pick media file")
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Copies a [content://] (or any openable) [Uri] into the app's private files dir
 * and returns the on-disk [File] mpv can open with a plain path. Overwritten on
 * each pick so we don't accumulate copies.
 */
private fun copyContentUriToFile(context: Context, uri: Uri): File? {
    return try {
        val ext = context.contentResolver.getType(uri)?.let { mime ->
            mime.substringAfter('/').takeIf { it.isNotEmpty() }?.let { ".$it" }
        } ?: ".bin"
        val outFile = File(context.filesDir, "picked$ext")
        outFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        outFile
    } catch (t: Throwable) {
        Log.e(TAG, "copyContentUriToFile failed", t)
        null
    }
}
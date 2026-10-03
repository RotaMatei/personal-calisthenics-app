package com.personal.calisthenicsguide.ui.guide

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.personal.calisthenicsguide.data.MediaOverrideEntity
import com.personal.calisthenicsguide.ui.theme.AppColors

/**
 * Lets the user replace the built-in 3D clip with a GIF or MP4 from device storage. The file is not copied; the app
 * keeps a persistable read permission on the chosen document.
 */
@Composable
fun MediaOverrideSection(
    override: MediaOverrideEntity?,
    onPick: (uri: String, mimeType: String) -> Unit,
    onClear: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var showMine by remember(override?.uri) { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            onPick(uri.toString(), mime)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (override != null && showMine) {
            UserClip(override)
        } else {
            content()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/gif", "video/mp4", "video/*")) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (override == null) "Attach my GIF / MP4" else "Replace my clip") }
            if (override != null) {
                OutlinedButton(onClick = { showMine = !showMine }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showMine) "Show 3D clip" else "Show my clip")
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove") }
            }
        }
        Text(
            "Optional: your own GIF or MP4 from the device replaces the built-in clip for this exercise.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
    }
}

@Composable
private fun UserClip(override: MediaOverrideEntity) {
    val context = LocalContext.current
    val uri = Uri.parse(override.uri)
    val shape = RoundedCornerShape(18.dp)
    if (override.mimeType.contains("gif")) {
        val loader = remember {
            ImageLoader.Builder(context)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                }
                .build()
        }
        AsyncImage(
            model = uri,
            imageLoader = loader,
            contentDescription = "Your exercise clip",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(340.dp).clip(shape),
        )
    } else {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(uri)
                    setOnPreparedListener { player ->
                        player.isLooping = true
                        player.setVolume(0f, 0f)
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(340.dp).clip(shape),
        )
    }
}

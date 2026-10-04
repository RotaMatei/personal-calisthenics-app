package com.personal.calisthenicsguide.ui.guide

import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * The user's own GIF or MP4 for an exercise, shown instead of the built-in 3D clip. GIFs go through Coil, videos
 * through a muted, looping [VideoView].
 */
@Composable
fun MediaOverrideView(uri: String, mimeType: String, modifier: Modifier = Modifier) {
    if (mimeType.startsWith("video")) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(Uri.parse(uri))
                    setOnPreparedListener { player ->
                        player.isLooping = true
                        player.setVolume(0f, 0f)
                        start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                }
            },
        )
    } else {
        val context = LocalContext.current
        val loader = remember(context) {
            ImageLoader.Builder(context)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                }
                .build()
        }
        AsyncImage(
            model = uri,
            imageLoader = loader,
            contentDescription = "Your own exercise animation",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize(),
        )
    }
}

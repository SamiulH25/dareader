package dareader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Fixed-aspect cover slot. While loading shows a flat surface-variant box
 * with the title-initial letter (no shimmer); null URLs and load failures
 * show the same slot in a different flat tone. Loaded covers fill and crop.
 */
@Composable
fun CoverImage(
    imageUrl: String?,
    http: HttpSource?,
    modifier: Modifier = Modifier,
    contentDescription: String?,
    aspect: Float = 3f / 4f,
) {
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(imageUrl) { mutableStateOf(false) }

    LaunchedEffect(imageUrl) {
        bitmap = null
        failed = false
        val url = imageUrl
        val source = http
        if (url.isNullOrBlank() || source == null) {
            failed = true
        } else {
            bitmap = runCatching { PageImages.url(source, url) }
                .onFailure { failed = true }
                .getOrNull()
        }
    }

    val loaded = bitmap
    val initial = contentDescription?.trim()?.firstOrNull()?.uppercase() ?: "?"
    val error = failed || (loaded == null && imageUrl.isNullOrBlank())
    val container = if (error) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (error) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(MaterialTheme.shapes.medium)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(initial, style = MaterialTheme.typography.titleLarge, color = content)
        }
    }
}

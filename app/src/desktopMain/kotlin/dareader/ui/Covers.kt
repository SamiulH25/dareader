package dareader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Fixed-aspect cover slot. While loading, and for null URLs or failed loads,
 * it shows a flat surface-container tile with the title initial — no shimmer,
 * no broken-image chrome. Loaded covers fill and crop.
 *
 * [badge] renders a pill on the cover's bottom-left (unread/read counters).
 */
@Composable
fun CoverImage(
    imageUrl: String?,
    http: HttpSource?,
    modifier: Modifier = Modifier,
    contentDescription: String?,
    aspect: Float = 3f / 4f,
    badge: String? = null,
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
    val placeholder = if (failed && loaded == null) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(MaterialTheme.shapes.medium)
            .background(placeholder),
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
            Text(
                initial,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (badge != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    badge,
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

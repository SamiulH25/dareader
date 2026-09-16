package dareader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dareader.library.LibraryEntry
import dareader.library.MangaRef
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.model.displayTitle
import eu.kanade.tachiyomi.source.online.HttpSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Rebuilds a detached snapshot into a usable model (never a lateinit ref). */
fun MangaRef.toSManga(): SManga = SMangaImpl().apply {
    url = this@toSManga.url
    title = this@toSManga.title
    author = this@toSManga.author
    thumbnail_url = this@toSManga.thumbnail
}

/** All installed sources, for Detail back-navigation. Never throws. */
fun allSources(state: AppState): List<Source> =
    runCatching { state.extensions.installed.value.flatMap { it.sources } }.getOrDefault(emptyList())

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

private fun formatAdded(addedAt: Long): String =
    runCatching { dateFormat.format(Instant.ofEpochMilli(addedAt)) }.getOrDefault("")

@Composable
fun LibraryScreen(state: AppState) {
    val entries by state.library.entries.collectAsState()
    val history by state.library.history.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Library (${entries.size})", style = MaterialTheme.typography.titleMedium)
        if (entries.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Your library is empty. Titles you save will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ state.screen = Screen.Setup }) { Text("Browse extensions") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(entries, key = { "${it.sourceId}:${it.manga.url}" }) { entry ->
                    LibraryRow(state, entry)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History (${history.size})", style = MaterialTheme.typography.titleMedium)
            if (history.isNotEmpty()) {
                Button({ runCatching { state.library.clearHistory() } }) { Text("Clear") }
            }
        }
        if (history.isEmpty()) {
            Text(
                "Nothing read yet. Open a title to start a history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { "${it.sourceId}:${it.manga.url}:${it.at}" }) { item ->
                    HistoryRow(state, item.sourceId, item.manga, item.at)
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(state: AppState, entry: LibraryEntry) {
    val manga = remember(entry.sourceId, entry.manga.url) { entry.manga.toSManga() }
    val inLibrary = remember(entry.manga.url) { state.library.isInLibrary(entry.manga.url) }
    Row(
        Modifier.fillMaxWidth().clickable {
            val source = state.extensions.findSource(entry.sourceId)
            if (source == null) {
                state.reportError("Source for ${entry.manga.title} is not installed")
            } else {
                state.openDetail(source, allSources(state).ifEmpty { listOf(source) }, manga)
            }
        }.padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LibraryCover(state, entry.sourceId, entry.manga)
        Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
            Text(manga.displayTitle(), style = MaterialTheme.typography.titleSmall)
            entry.manga.author?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            val added = formatAdded(entry.addedAt)
            if (added.isNotEmpty()) {
                Text(
                    "Added $added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (inLibrary) {
            Button({ runCatching { state.library.toggleInLibrary(entry.sourceId, manga) } }) { Text("Remove") }
        }
    }
}

@Composable
private fun HistoryRow(state: AppState, sourceId: Long, ref: MangaRef, at: Long) {
    val manga = remember(sourceId, ref.url) { ref.toSManga() }
    Row(
        Modifier.fillMaxWidth().clickable {
            val source = state.extensions.findSource(sourceId)
            if (source == null) {
                state.reportError("Source for ${ref.title} is not installed")
            } else {
                state.openDetail(source, allSources(state).ifEmpty { listOf(source) }, manga)
            }
        }.padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryCover(state, sourceId, ref)
        Column(Modifier.weight(1f)) {
            Text(manga.displayTitle(), style = MaterialTheme.typography.titleSmall)
            val whenText = formatAdded(at)
            if (whenText.isNotEmpty()) {
                Text(
                    whenText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LibraryCover(state: AppState, sourceId: Long, ref: MangaRef) {
    val http = remember(sourceId) { state.extensions.findSource(sourceId) as? HttpSource }
    CoverImage(
        imageUrl = ref.thumbnail,
        http = http,
        modifier = Modifier.width(56.dp),
        contentDescription = ref.title,
    )
}

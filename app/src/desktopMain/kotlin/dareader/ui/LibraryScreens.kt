package dareader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dareader.library.HistoryEntry
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

private fun formatDate(at: Long): String =
    runCatching { dateFormat.format(Instant.ofEpochMilli(at)) }.getOrDefault("")

private fun formatRelative(at: Long): String {
    val minutes = (System.currentTimeMillis() - at).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} d ago"
        else -> formatDate(at)
    }
}

private enum class LibraryFilter(val label: String) {
    All("All"),
    Reading("Reading"),
    Unread("Unread"),
}

/**
 * Library as a cover grid, with an in-memory title filter, reading-state chips
 * and a sort toggle. Badges show how many chapters of a title are marked read;
 * nothing is shown for titles with no reading progress.
 */
@Composable
fun LibraryScreen(state: AppState) {
    val entries by state.library.entries.collectAsState()
    val readCounts by state.library.readCounts.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.All) }
    var sortByTitle by remember { mutableStateOf(false) }

    val shown = remember(entries, query, filter, sortByTitle, readCounts) {
        entries
            .asSequence()
            .filter { entry ->
                val q = query.trim()
                q.isEmpty() ||
                    entry.manga.title.contains(q, ignoreCase = true) ||
                    (entry.manga.author?.contains(q, ignoreCase = true) ?: false)
            }
            .filter { entry ->
                val reads = readCounts[entry.manga.url] ?: 0
                when (filter) {
                    LibraryFilter.All -> true
                    LibraryFilter.Reading -> reads > 0
                    LibraryFilter.Unread -> reads == 0
                }
            }
            .sortedWith(
                if (sortByTitle) {
                    compareBy { it.manga.title.lowercase() }
                } else {
                    compareByDescending { it.addedAt }
                },
            )
            .toList()
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Library", "${entries.size} titles")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                label = { Text("Search library") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            TextButton({ sortByTitle = !sortByTitle }) {
                Text(if (sortByTitle) "Sort: title" else "Sort: added")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                )
            }
        }
        when {
            entries.isEmpty() -> EmptyNotice("Your library is empty. Titles you save will appear here.") {
                OutlinedButton({ state.screen = Screen.Setup }) { Text("Browse extensions") }
            }
            shown.isEmpty() -> EmptyNotice("No titles match this filter.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(shown, key = { "${it.sourceId}:${it.manga.url}" }) { entry ->
                    LibraryCell(state, entry, readCounts[entry.manga.url] ?: 0)
                }
            }
        }
    }
}

@Composable
private fun LibraryCell(state: AppState, entry: LibraryEntry, readCount: Int) {
    val manga = remember(entry.sourceId, entry.manga.url) { entry.manga.toSManga() }
    val http = remember(entry.sourceId, state.installedKey()) {
        state.extensions.findSource(entry.sourceId) as? HttpSource
    }
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                val source = state.extensions.findSource(entry.sourceId)
                if (source == null) {
                    state.reportError("Source for ${entry.manga.title} is not installed")
                } else {
                    state.openDetail(source, allSources(state).ifEmpty { listOf(source) }, manga)
                }
            }
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CoverImage(
            imageUrl = entry.manga.thumbnail,
            http = http,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = entry.manga.title,
            badge = if (readCount > 0) "$readCount read" else null,
        )
        Text(
            entry.manga.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.manga.author?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Recomposition key for source handles that come and go with extension loads. */
private fun AppState.installedKey(): Int = extensions.installed.value.size

@Composable
fun HistoryScreen(state: AppState) {
    val history by state.library.history.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("History", "${history.size} entries")
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                OutlinedButton({ runCatching { state.library.clearHistory() } }) { Text("Clear") }
            }
        }
        if (history.isEmpty()) {
            EmptyNotice("Nothing read yet. Open a title to start a history.")
        } else {
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { "${it.sourceId}:${it.manga.url}:${it.at}" }) { item ->
                    HistoryRow(state, item)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(state: AppState, entry: HistoryEntry) {
    val manga = remember(entry.sourceId, entry.manga.url) { entry.manga.toSManga() }
    val http = remember(entry.sourceId, state.installedKey()) {
        state.extensions.findSource(entry.sourceId) as? HttpSource
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                val source = state.extensions.findSource(entry.sourceId)
                if (source == null) {
                    state.reportError("Source for ${entry.manga.title} is not installed")
                } else {
                    state.openDetail(source, allSources(state).ifEmpty { listOf(source) }, manga)
                }
            }
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            imageUrl = entry.manga.thumbnail,
            http = http,
            modifier = Modifier.width(56.dp),
            contentDescription = entry.manga.title,
        )
        Column(Modifier.weight(1f)) {
            Text(
                manga.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatRelative(entry.at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

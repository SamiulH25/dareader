package dareader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import dareader.ext.di.DareaderGraph
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.displayName
import eu.kanade.tachiyomi.source.model.displayTitle
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

const val DEFAULT_APK_URL =
    "https://github.com/keiyoushi/extensions/releases/download/6ca40f6-0/tachiyomi-all.comicfury-v1.4.8.apk"

private enum class TopDest { Library, Store, Extensions }

private fun destOf(screen: Screen): TopDest? = when (screen) {
    is Screen.Library -> TopDest.Library
    is Screen.Store -> TopDest.Store
    is Screen.Setup -> TopDest.Extensions
    else -> null
}

@Composable
fun ReaderApp(state: AppState) {
    val dark by ThemeMode.dark.collectAsState()
    DareaderTheme(darkTheme = dark) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar(state, dark)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val screen = state.screen) {
                    is Screen.Setup -> SetupScreen(state)
                    is Screen.Browse -> key(screen.sources) { BrowseScreen(state, screen.sources) }
                    is Screen.Detail -> DetailScreen(state, screen.source, screen.sources, screen.manga)
                    is Screen.Reader -> ReaderScreen(state, screen.source, screen.sources, screen.manga, screen.chapter)
                    is Screen.Settings -> SettingsScreen(state, screen.source, screen.sources)
                    is Screen.Library -> LibraryScreen(state)
                    is Screen.Store -> StoreScreen(state)
                }
            }
            StatusFooter(state)
        }
        state.error?.let { message ->
            AlertDialog(
                onDismissRequest = { state.dismissError() },
                confirmButton = { Button({ state.dismissError() }) { Text("Dismiss") } },
                title = { Text("Something went wrong") },
                text = { Text(message) },
            )
        }
        TrustDialog(state)
    }
}

@Composable
private fun TopBar(state: AppState, dark: Boolean) {
    val current = destOf(state.screen)
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 10.dp, height = 22.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text("dareader", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            TopNavItem("Library", selected = current == TopDest.Library) { state.screen = Screen.Library }
            TopNavItem("Store", selected = current == TopDest.Store) { state.screen = Screen.Store }
            TopNavItem("Extensions", selected = current == TopDest.Extensions) { state.screen = Screen.Setup }
            Spacer(Modifier.weight(1f))
            TextButton({ ThemeMode.toggle() }) {
                Text(
                    if (dark) "Light" else "Dark",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun TopNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun StatusFooter(state: AppState) {
    Column {
        HorizontalDivider()
        Text(
            state.status,
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SetupScreen(state: AppState) {
    var apkUrl by remember { mutableStateOf(DEFAULT_APK_URL) }
    val installed by state.extensions.installed.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Welcome to dareader", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Load an extension to start reading. Installed extensions are listed below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            apkUrl,
            { apkUrl = it },
            Modifier.fillMaxWidth(),
            label = { Text("Extension APK address") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ state.loadExtension(apkUrl) }) { Text("Load extension") }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Installed (${installed.size})", style = MaterialTheme.typography.titleMedium)
        if (installed.isEmpty()) {
            Text(
                "No extensions installed yet. Load one above, or find more in the store.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ state.screen = Screen.Store }) { Text("Open store") }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(installed, key = { it.pkg }) { ext ->
                    Row(
                        Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ext.pkg, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${ext.sources.size} sources · ${ext.versionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val first = ext.sources.firstOrNull()
                        TextButton(
                            onClick = { first?.let { state.screen = Screen.Browse(listOf(it)) } },
                            enabled = first != null,
                        ) { Text("Open") }
                        TextButton({ state.uninstallExtension(ext.pkg) }) { Text("Uninstall") }
                    }
                }
            }
        }
    }
}

private enum class BrowseMode { POPULAR, LATEST, SEARCH }

@Composable
private fun BrowseScreen(state: AppState, sources: List<Source>) {
    var selected by remember { mutableStateOf(sources.firstOrNull()) }
    var mode by remember { mutableStateOf(BrowseMode.POPULAR) }
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(selected?.let { state.sourceFilters(it) } ?: FilterList()) }
    var filterTick by remember { mutableStateOf(0) }
    var mangas by remember { mutableStateOf<List<SManga>>(emptyList()) }
    var pageNo by remember { mutableStateOf(1) }
    var hasNext by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val gen = remember { AtomicInteger(0) }

    fun loadPage(source: Source, targetMode: BrowseMode, page: Int, append: Boolean, currentQuery: String, currentFilters: FilterList) {
        val id = gen.incrementAndGet()
        loading = true
        if (!append) error = null
        scope.launch {
            val result = when (targetMode) {
                BrowseMode.POPULAR -> state.popular(source, page)
                BrowseMode.LATEST -> state.latest(source, page)
                BrowseMode.SEARCH -> state.search(source, page, currentQuery, currentFilters)
            }
            if (id != gen.get()) return@launch
            if (result != null) {
                mangas = if (append) mangas + result.mangas else result.mangas
                hasNext = result.hasNextPage
                pageNo = page
            } else {
                if (!append) {
                    mangas = emptyList()
                    hasNext = false
                }
                error = state.error ?: "browse error"
            }
            loading = false
        }
    }

    fun loadFirst() {
        val source = selected ?: return
        mangas = emptyList()
        hasNext = false
        pageNo = 1
        loadPage(source, mode, 1, append = false, query, filters)
    }

    fun pick(source: Source) {
        if (source == selected) {
            loadFirst()
            return
        }
        selected = source
        filters = state.sourceFilters(source)
        filterTick++
        mode = BrowseMode.POPULAR
    }

    fun switchMode(next: BrowseMode) {
        if (next == mode) return
        mode = next
    }

    LaunchedEffect(selected, mode) {
        val source = selected ?: return@LaunchedEffect
        mangas = emptyList()
        hasNext = false
        pageNo = 1
        error = null
        loadPage(source, mode, 1, append = false, query, filters)
    }

    val latestSupported = selected?.supportsLatest == true
    val configurable = selected as? ConfigurableSource

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        val settingsTarget = selected
        if (configurable != null && settingsTarget != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ state.screen = Screen.Settings(settingsTarget, sources) }) { Text("Source settings") }
            }
        }
        Text("Sources (${sources.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(sources, key = { it.id }) { source ->
                val isSelected = source == selected
                Row(
                    Modifier.fillMaxWidth().clickable { pick(source) }.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        source.name,
                        Modifier.weight(1f, fill = true),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                    if (source.lang.isNotBlank()) {
                        LangChip(source.lang)
                    }
                    Text(
                        "#${source.id}",
                        Modifier.widthIn(max = 140.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeTab("Popular", selected = mode == BrowseMode.POPULAR) { switchMode(BrowseMode.POPULAR) }
            if (latestSupported) {
                ModeTab("Latest", selected = mode == BrowseMode.LATEST) { switchMode(BrowseMode.LATEST) }
            }
            ModeTab("Search", selected = mode == BrowseMode.SEARCH) { switchMode(BrowseMode.SEARCH) }
        }
        if (mode == BrowseMode.SEARCH) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.weight(1f),
                    label = { Text("Search titles") },
                    singleLine = true,
                )
                Button({ loadFirst() }, Modifier.align(Alignment.CenterVertically)) { Text("Go") }
            }
            key(filterTick) {
                FilterListView(filters) { filterTick++ }
            }
        }
        if (loading && mangas.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error?.let { message ->
            if (mangas.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    Button({ loadFirst() }) { Text("Retry") }
                }
            } else {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (mangas.isEmpty() && !loading && error == null) {
            Text("No titles found. Try another source or search.", style = MaterialTheme.typography.bodySmall)
        }
        if (mangas.isNotEmpty()) {
            Text(
                "Showing ${mangas.size} titles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val current = selected
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(mangas, key = { it.url }) { manga ->
                if (current != null) {
                    MangaRow(current, manga) { state.openDetail(current, sources, manga) }
                }
            }
            if (hasNext) {
                item {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Button({ selected?.let { loadPage(it, mode, pageNo + 1, append = true, query, filters) } }, enabled = !loading) {
                            Text(if (loading) "Loading…" else "Load more")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick, enabled = !selected) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun LangChip(lang: String) {
    Box(
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(lang, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FilterListView(filters: FilterList, onMutate: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        filters.forEach { filter -> FilterRow(filter, onMutate) }
    }
}

@Composable
private fun FilterRow(filter: Filter<*>, onMutate: () -> Unit) {
    when (filter) {
        is Filter.Header -> Text(filter.name, style = MaterialTheme.typography.titleSmall)
        is Filter.Separator -> HorizontalDivider()
        is Filter.Select<*> -> {
            var expanded by remember { mutableStateOf(false) }
            val labels = filter.displayValues
            val current = filter.state.takeIf { it in labels.indices } ?: 0
            Column {
                Text(filter.name, style = MaterialTheme.typography.bodySmall)
                Box {
                    Button({ expanded = true }) { Text(labels.getOrElse(current) { "?" }) }
                    DropdownMenu(expanded, { expanded = false }) {
                        labels.forEachIndexed { index, label ->
                            DropdownMenuItem({ Text(label) }, {
                                filter.state = index
                                expanded = false
                                onMutate()
                            })
                        }
                    }
                }
            }
        }
        is Filter.Text -> {
            OutlinedTextField(
                filter.state,
                { filter.state = it; onMutate() },
                Modifier.fillMaxWidth(),
                label = { Text(filter.name) },
                singleLine = true,
            )
        }
        is Filter.CheckBox -> {
            Row(Modifier.fillMaxWidth().clickable { filter.state = !filter.state; onMutate() }.padding(4.dp)) {
                Checkbox(filter.state, null)
                Text(filter.name, Modifier.align(Alignment.CenterVertically))
            }
        }
        is Filter.TriState -> {
            val label = when (filter.state) {
                Filter.TriState.STATE_INCLUDE -> "Include"
                Filter.TriState.STATE_EXCLUDE -> "Exclude"
                else -> "Ignore"
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(filter.name, Modifier.align(Alignment.CenterVertically))
                Button({ filter.state = (filter.state + 1) % 3; onMutate() }) { Text(label) }
            }
        }
        is Filter.Group<*> -> {
            Column(Modifier.padding(start = 8.dp)) {
                Text(filter.name, style = MaterialTheme.typography.titleSmall)
                filter.state.forEach { member ->
                    if (member is Filter<*>) FilterRow(member, onMutate)
                }
            }
        }
        is Filter.Sort -> {
            var expanded by remember { mutableStateOf(false) }
            val selection = filter.state
            val currentLabel = selection?.let { filter.values.getOrNull(it.index)?.let { v -> "$v ${if (it.ascending) "↑" else "↓"}" } } ?: "None"
            Column {
                Text(filter.name, style = MaterialTheme.typography.bodySmall)
                Box {
                    Button({ expanded = true }) { Text(currentLabel) }
                    DropdownMenu(expanded, { expanded = false }) {
                        DropdownMenuItem({ Text("None") }, {
                            filter.state = null
                            expanded = false
                            onMutate()
                        })
                        filter.values.forEachIndexed { index, value ->
                            DropdownMenuItem({ Text(value) }, {
                                filter.state = Filter.Sort.Selection(index, selection?.ascending ?: true)
                                expanded = false
                                onMutate()
                            })
                        }
                    }
                }
                if (selection != null) {
                    Row(Modifier.clickable {
                        filter.state = selection.copy(ascending = !selection.ascending)
                        onMutate()
                    }) {
                        Checkbox(selection.ascending, null)
                        Text("Ascending", Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
        }
        else -> Text(filter.name, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MangaRow(source: Source?, manga: SManga, onClick: () -> Unit) {
    val http = source as? HttpSource
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CoverImage(
            imageUrl = manga.thumbnail_url,
            http = http,
            modifier = Modifier.width(84.dp),
            contentDescription = manga.displayTitle(),
        )
        Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
            Text(manga.displayTitle(), style = MaterialTheme.typography.titleSmall)
            manga.author?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailScreen(state: AppState, source: Source, sources: List<Source>, manga: SManga) {
    var update by remember { mutableStateOf<SMangaUpdate?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var ascending by remember { mutableStateOf(true) }
    val entries by state.library.entries.collectAsState()
    val inLibrary = remember(entries, manga.url) { entries.any { it.manga.url == manga.url } }
    LaunchedEffect(manga.url, attempt) {
        detailError = null
        update = state.detail(source, manga)
        if (update == null) detailError = state.error ?: "detail error"
    }
    val http = source as? HttpSource
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ state.screen = Screen.Browse(sources) }) { Text("Back") }
            Text(
                manga.displayTitle(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverImage(
                imageUrl = manga.thumbnail_url,
                http = http,
                modifier = Modifier.width(120.dp),
                contentDescription = manga.displayTitle(),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(manga.displayTitle(), style = MaterialTheme.typography.titleMedium)
                manga.author?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                val infoStatus = update?.manga?.status
                if (infoStatus != null) {
                    Text(
                        mangaStatusText(infoStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                update?.manga?.genre?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ runCatching { state.library.toggleInLibrary(source.id, manga) } }) {
                Text(if (inLibrary) "In library" else "Add to library")
            }
            Button({ openMangaInBrowser(state, source, manga) }) { Text("Open in browser") }
        }
        val u = update
        when {
            u == null && detailError != null -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(detailError ?: "detail error", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Button({ attempt++ }) { Text("Retry") }
            }
            u == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                u.manga.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        Modifier.widthIn(max = 640.dp).padding(end = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (u.chapters.isEmpty()) {
                    Text("No chapters yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Chapters (${u.chapters.size})", style = MaterialTheme.typography.titleSmall)
                        TextButton({ ascending = !ascending }) { Text(if (ascending) "Newest first" else "Oldest first") }
                    }
                    val shown = remember(u.chapters, ascending) { if (ascending) u.chapters else u.chapters.reversed() }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.url }) { chapter: SChapter ->
                            val read = remember(manga.url, chapter.url, entries) {
                                runCatching { state.library.isChapterRead(manga.url, chapter.url) }.getOrDefault(false)
                            }
                            val label = (if (read) "✓ " else "") + chapter.displayName()
                            Text(
                                label,
                                Modifier.fillMaxWidth().clickable {
                                    state.screen = Screen.Reader(source, sources, manga, chapter)
                                }.padding(6.dp),
                                color = if (read) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun mangaStatusText(status: Int): String = when (status) {
    SManga.ONGOING -> "Ongoing"
    SManga.COMPLETED -> "Completed"
    SManga.LICENSED -> "Licensed"
    SManga.PUBLISHING_FINISHED -> "Publishing finished"
    SManga.CANCELLED -> "Cancelled"
    SManga.ON_HIATUS -> "On hiatus"
    else -> "Unknown"
}

private fun openMangaInBrowser(state: AppState, source: Source, manga: SManga) {
    runCatching {
        val raw = manga.url
        val http = source as? HttpSource
        val full = if (raw.startsWith("http")) raw else "${http?.baseUrl?.trimEnd('/') ?: ""}/${raw.trimStart('/')}"
        check(full.startsWith("http")) { "no web URL for ${manga.displayTitle()}" }
        check(Desktop.isDesktopSupported()) { "desktop browsing unsupported" }
        Desktop.getDesktop().browse(URI(full))
    }.onFailure { state.reportError("open-in-browser failed: ${it.message}") }
}

@Composable
private fun ReaderScreen(state: AppState, source: Source, sources: List<Source>, manga: SManga, chapter: SChapter) {
    val http = source as? HttpSource
    var pages by remember { mutableStateOf<List<Page>?>(null) }
    var readerError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var chapters by remember { mutableStateOf<List<SChapter>?>(null) }
    LaunchedEffect(chapter.url, attempt) {
        if (http != null) {
            readerError = null
            pages = state.pages(source, chapter)
            if (pages == null) readerError = state.error ?: "reader error"
        } else {
            readerError = "reader needs an online source"
        }
    }
    LaunchedEffect(manga.url) {
        chapters = state.detail(source, manga)?.chapters
    }
    val startIndex = remember(manga.url, chapter.url) {
        runCatching { state.library.getProgress(manga.url, chapter.url) }.getOrDefault(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val totalPages = pages?.size
    LaunchedEffect(listState.firstVisibleItemIndex, totalPages) {
        val index = listState.firstVisibleItemIndex
        state.saveProgress(manga.url, chapter.url, index)
        if (totalPages != null && totalPages > 0 && index >= totalPages - 1) {
            runCatching { state.library.markChapterRead(manga.url, chapter.url, true) }
        }
    }
    val siblingIndex = remember(chapters, chapter.url) {
        chapters?.indexOfFirst { it.url == chapter.url } ?: -1
    }
    val chapterList = chapters
    val prev = if (siblingIndex > 0) chapterList?.get(siblingIndex - 1) else null
    val next = if (siblingIndex >= 0 && chapterList != null && siblingIndex < chapterList.size - 1) {
        chapterList[siblingIndex + 1]
    } else {
        null
    }
    val visiblePage = listState.firstVisibleItemIndex + 1
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ state.screen = Screen.Detail(source, sources, manga) }) { Text("Back") }
            Button({ prev?.let { state.screen = Screen.Reader(source, sources, manga, it) } }, enabled = prev != null) {
                Text("Prev")
            }
            Button({ next?.let { state.screen = Screen.Reader(source, sources, manga, it) } }, enabled = next != null) {
                Text("Next")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val total = totalPages
                if (total != null && total > 0) {
                    Text(
                        "Page ${visiblePage.coerceAtMost(total)} / $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val list = pages
        val failure = readerError
        when {
            failure != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(failure, style = MaterialTheme.typography.bodySmall)
                    Button({ attempt++ }) { Text("Retry") }
                }
            }
            http == null || list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pages in this chapter.") }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(list, key = { it.index }) { page ->
                    PageView(http, page)
                }
            }
        }
    }
}

@Composable
private fun PageView(source: HttpSource, page: Page) {
    var bitmap by remember(page.url, page.index) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(page.url, page.index) { mutableStateOf(false) }
    var attempt by remember(page.url, page.index) { mutableStateOf(0) }
    LaunchedEffect(page.url, page.index, attempt) {
        failed = false
        bitmap = runCatching { PageImages.page(source, page) }
            .onFailure { failed = true }
            .getOrNull()
    }
    val image = bitmap
    when {
        image != null -> androidx.compose.foundation.Image(image, null, Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
        failed -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Failed to load page ${page.index + 1}", style = MaterialTheme.typography.bodySmall)
                Button({ attempt++ }) { Text("Retry") }
            }
        }
        else -> Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun SettingsScreen(state: AppState, source: Source, sources: List<Source>) {
    val configurable = source as? ConfigurableSource
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ state.screen = Screen.Browse(sources) }) { Text("Back") }
            Text("${source.name} settings", style = MaterialTheme.typography.titleMedium)
        }
        if (configurable == null) {
            Text("This source has no settings.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ state.screen = Screen.Browse(sources) }) { Text("Back to browse") }
            }
            return
        }
        val built = remember(source) {
            runCatching {
                PreferenceScreen(DareaderGraph.application).also { prefScreen ->
                    prefScreen.setSharedPreferences(configurable.sourcePreferences())
                    configurable.setupPreferenceScreen(prefScreen)
                }
            }
        }
        val screen = built.getOrNull()
        val buildError = built.exceptionOrNull()?.message
        buildError?.let {
            Text("Settings error: $it", style = MaterialTheme.typography.bodySmall)
            return
        }
        val count = remember(source) { runCatching { screen?.preferenceCount ?: 0 }.getOrDefault(0) }
        if (screen == null || count == 0) {
            Text("No settings for this source.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ state.screen = Screen.Browse(sources) }) { Text("Back to browse") }
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(count) { index ->
                screen.getPreference(index)?.let { pref -> PrefRow(pref, index) }
            }
        }
    }
}

@Composable
private fun PrefRow(pref: Preference, index: Int) {
    val title = pref.title?.toString() ?: pref.key ?: "preference"
    val summary = pref.summary?.toString()
    when (pref) {
        is SwitchPreferenceCompat -> {
            var checked by remember(pref.key ?: "switch-$index") { mutableStateOf(pref.isChecked()) }
            Row(Modifier.fillMaxWidth().clickable {
                checked = !checked
                pref.setChecked(checked)
            }.padding(6.dp)) {
                Checkbox(checked, null)
                Column(Modifier.align(Alignment.CenterVertically)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        is EditTextPreference -> {
            var text by remember(pref.key ?: "text-$index") { mutableStateOf(pref.getText() ?: "") }
            Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(text, {
                    text = it
                    pref.setText(it)
                }, Modifier.fillMaxWidth(), singleLine = true)
            }
        }
        is ListPreference -> {
            var expanded by remember { mutableStateOf(false) }
            var value by remember(pref.key ?: "list-$index") { mutableStateOf(pref.getValue()) }
            val entries = pref.getEntries() ?: emptyArray()
            val entryValues = pref.getEntryValues() ?: emptyArray()
            val currentLabel = value?.let { v ->
                val i = entryValues.indexOfFirst { it.toString() == v }
                entries.getOrNull(i)?.toString()
            } ?: value
            Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Box {
                    Button({ expanded = true }) { Text(currentLabel ?: "Select") }
                    DropdownMenu(expanded, { expanded = false }) {
                        entryValues.forEachIndexed { i, entryValue ->
                            DropdownMenuItem({ Text(entries.getOrNull(i)?.toString() ?: entryValue.toString()) }, {
                                value = entryValue.toString()
                                pref.setValue(entryValue.toString())
                                expanded = false
                            })
                        }
                    }
                }
            }
        }
        else -> {
            Column(Modifier.fillMaxWidth().padding(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

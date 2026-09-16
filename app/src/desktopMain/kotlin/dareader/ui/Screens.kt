package dareader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import dareader.ext.di.DareaderGraph
import eu.kanade.tachiyomi.AppInfo
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
import kotlin.math.roundToInt

const val DEFAULT_APK_URL =
    "https://github.com/keiyoushi/extensions/releases/download/6ca40f6-0/tachiyomi-all.comicfury-v1.4.8.apk"

/**
 * Desktop shell: a Material 3 navigation rail on the left, the active screen
 * beside it, and a status strip along the bottom. The reader hides the rail
 * to stay immersive.
 */
@Composable
fun ReaderApp(state: AppState) {
    val dark by ThemeMode.dark.collectAsState()
    DareaderTheme(darkTheme = dark) {
        val screen = state.screen
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (screen !is Screen.Reader) {
                DareaderNavRail(state)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        is Screen.Setup -> SetupScreen(state)
                        is Screen.Library -> LibraryScreen(state)
                        is Screen.History -> HistoryScreen(state)
                        is Screen.Store -> StoreScreen(state)
                        is Screen.More -> MoreScreen(state, dark)
                        is Screen.Browse -> key(screen.sources) { BrowseScreen(state, screen.sources) }
                        is Screen.Detail -> DetailScreen(state, screen.source, screen.sources, screen.manga)
                        is Screen.Reader -> ReaderScreen(state, screen.source, screen.sources, screen.manga, screen.chapter)
                        is Screen.Settings -> SettingsScreen(state, screen.source, screen.sources)
                    }
                }
                StatusBar(state, dark)
            }
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

private enum class Destination(val label: String) {
    Library("Library"),
    History("History"),
    Browse("Browse"),
    Store("Store"),
    Extensions("Extensions"),
    More("More"),
}

@Composable
private fun DareaderNavRail(state: AppState) {
    val selected = when (state.screen) {
        is Screen.Library -> Destination.Library
        is Screen.History -> Destination.History
        is Screen.Store -> Destination.Store
        is Screen.Setup -> Destination.Extensions
        is Screen.More -> Destination.More
        else -> null
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(216.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 14.dp),
    ) {
        BrandHeader()
        Spacer(Modifier.height(18.dp))
        RailItem(Destination.Library, DareaderIcons.Library, selected) { state.screen = Screen.Library }
        RailItem(Destination.History, DareaderIcons.History, selected) { state.screen = Screen.History }
        RailItem(Destination.Browse, DareaderIcons.Browse, selected) {
            state.screen = Screen.Browse(state.availableSources)
        }
        RailItem(Destination.Store, DareaderIcons.Store, selected) { state.screen = Screen.Store }
        RailItem(Destination.Extensions, DareaderIcons.Extensions, selected) { state.screen = Screen.Setup }
        RailItem(Destination.More, DareaderIcons.More, selected) { state.screen = Screen.More }
        Spacer(Modifier.weight(1f))
        LoadedExtensionFooter(state)
    }
}

@Composable
private fun BrandHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 6.dp, height = 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Text("dareader", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RailItem(
    destination: Destination,
    icon: ImageVector,
    selected: Destination?,
    onClick: () -> Unit,
) {
    val isSelected = destination == selected
    val container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Single-shot extensions are not installed; surface them with an unload action. */
@Composable
private fun LoadedExtensionFooter(state: AppState) {
    val sources = state.transientSources
    if (sources.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Loaded extension",
            Modifier.padding(start = 12.dp, top = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${sources.size} sources",
            Modifier.padding(start = 12.dp, bottom = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton({ state.closeExtension() }) { Text("Unload") }
    }
}

@Composable
private fun StatusBar(state: AppState, dark: Boolean) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.status,
                Modifier.weight(1f).padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton({ ThemeMode.toggle() }) {
                Text(if (dark) "Light" else "Dark", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let {
            Text(
                it,
                Modifier.padding(bottom = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyNotice(message: String, action: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

// ---------------------------------------------------------------- extensions

@Composable
private fun SetupScreen(state: AppState) {
    var apkUrl by remember { mutableStateOf(DEFAULT_APK_URL) }
    val installed by state.extensions.installed.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Extensions", "${installed.size} installed")
        Text(
            "Load an extension APK to read with it. Installed extensions are staged on disk and reload at startup; " +
                "loading is a temporary handle that goes away with the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            apkUrl,
            { apkUrl = it },
            Modifier.fillMaxWidth(),
            label = { Text("Extension APK address") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ state.loadExtension(apkUrl) }) { Text("Load") }
            OutlinedButton({ state.installExtension(apkUrl) }) { Text("Install") }
            TextButton({ state.screen = Screen.Store }) { Text("Open store") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Installed", style = MaterialTheme.typography.titleMedium)
        if (installed.isEmpty()) {
            EmptyNotice("No extensions installed yet. Load an APK above, or find one in the store.") {
                Button({ state.screen = Screen.Store }) { Text("Open store") }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(installed, key = { it.pkg }) { ext ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExtensionIcon(
                            ext.iconUrl?.ifBlank { null },
                            Modifier.size(40.dp),
                            fallbackText = ext.pkg.substringAfterLast('.').take(1).uppercase(),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(ext.pkg, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${ext.sources.size} sources · v${ext.versionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton({ state.screen = Screen.Browse(ext.sources) }) { Text("Open") }
                        TextButton({ state.uninstallExtension(ext.pkg) }) { Text("Uninstall") }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- browse

private enum class BrowseMode(val label: String) { POPULAR("Popular"), LATEST("Latest"), SEARCH("Search") }

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

    fun loadPage(
        source: Source,
        targetMode: BrowseMode,
        page: Int,
        append: Boolean,
        currentQuery: String,
        currentFilters: FilterList,
    ) {
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

    LaunchedEffect(selected, mode) {
        val source = selected ?: return@LaunchedEffect
        mangas = emptyList()
        hasNext = false
        pageNo = 1
        error = null
        loadPage(source, mode, 1, append = false, query, filters)
    }

    if (sources.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle("Browse")
            EmptyNotice("No sources loaded. Load or install an extension first.") {
                Button({ state.screen = Screen.Setup }) { Text("Go to Extensions") }
            }
        }
        return
    }

    val latestSupported = selected?.supportsLatest == true
    val configurable = selected as? ConfigurableSource

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Browse", "${sources.size} sources")
            Spacer(Modifier.weight(1f))
            val settingsTarget = selected
            if (configurable != null && settingsTarget != null) {
                TextButton({ state.screen = Screen.Settings(settingsTarget, sources) }) { Text("Source settings") }
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 132.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(sources, key = { it.id }) { source ->
                val isSelected = source == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { pick(source) }
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        source.name,
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (source.lang.isNotBlank()) {
                        Chip(source.lang.uppercase())
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == BrowseMode.POPULAR, { mode = BrowseMode.POPULAR }, label = { Text(BrowseMode.POPULAR.label) })
            if (latestSupported) {
                FilterChip(mode == BrowseMode.LATEST, { mode = BrowseMode.LATEST }, label = { Text(BrowseMode.LATEST.label) })
            }
            FilterChip(mode == BrowseMode.SEARCH, { mode = BrowseMode.SEARCH }, label = { Text(BrowseMode.SEARCH.label) })
        }
        if (mode == BrowseMode.SEARCH) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.weight(1f),
                    label = { Text("Search titles") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                Button({ loadFirst() }) { Text("Go") }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button({ loadFirst() }) { Text("Retry") }
            }
        }
        if (mangas.isEmpty() && !loading && error == null) {
            EmptyNotice("No titles found. Try another source or search.")
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
                        Button(
                            { selected?.let { loadPage(it, mode, pageNo + 1, append = true, query, filters) } },
                            enabled = !loading,
                        ) {
                            Text(if (loading) "Loading…" else "Load more")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FilterListView(filters: FilterList, onMutate: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(filters) { filter ->
            FilterRow(filter, onMutate)
        }
    }
}

@Composable
private fun FilterRow(filter: Filter<*>, onMutate: () -> Unit) {
    when (filter) {
        is Filter.Header -> Text(filter.name, style = MaterialTheme.typography.titleSmall)
        is Filter.Separator -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        is Filter.Select<*> -> {
            var expanded by remember { mutableStateOf(false) }
            val labels = filter.displayValues
            val current = filter.state.takeIf { it in labels.indices } ?: 0
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(filter.name, style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton({ expanded = true }) { Text(labels.getOrElse(current) { "?" }) }
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
                shape = MaterialTheme.shapes.large,
            )
        }
        is Filter.CheckBox -> {
            Row(
                Modifier.fillMaxWidth().clickable { filter.state = !filter.state; onMutate() }.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(filter.state, null)
                Text(filter.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is Filter.TriState -> {
            val label = when (filter.state) {
                Filter.TriState.STATE_INCLUDE -> "Include"
                Filter.TriState.STATE_EXCLUDE -> "Exclude"
                else -> "Ignore"
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(filter.name, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton({ filter.state = (filter.state + 1) % 3; onMutate() }) { Text(label) }
            }
        }
        is Filter.Group<*> -> {
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(filter.name, style = MaterialTheme.typography.titleSmall)
                filter.state.forEach { member ->
                    if (member is Filter<*>) FilterRow(member, onMutate)
                }
            }
        }
        is Filter.Sort -> {
            var expanded by remember { mutableStateOf(false) }
            val selection = filter.state
            val currentLabel = selection?.let {
                filter.values.getOrNull(it.index)?.let { v -> "$v ${if (it.ascending) "↑" else "↓"}" }
            } ?: "None"
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(filter.name, style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton({ expanded = true }) { Text(currentLabel) }
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
                    Row(
                        Modifier.clickable {
                            filter.state = selection.copy(ascending = !selection.ascending)
                            onMutate()
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(selection.ascending, null)
                        Text("Ascending", style = MaterialTheme.typography.bodyMedium)
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
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            imageUrl = manga.thumbnail_url,
            http = http,
            modifier = Modifier.width(64.dp),
            contentDescription = manga.displayTitle(),
        )
        Column(Modifier.weight(1f)) {
            Text(
                manga.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            manga.author?.takeIf { it.isNotBlank() }?.let {
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
}

// -------------------------------------------------------------------- detail

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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton({ state.screen = Screen.Browse(sources) }) { Icon(DareaderIcons.Back, "Back") }
            Text(
                manga.displayTitle(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CoverImage(
                imageUrl = manga.thumbnail_url,
                http = http,
                modifier = Modifier.width(140.dp),
                contentDescription = manga.displayTitle(),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(manga.displayTitle(), style = MaterialTheme.typography.titleMedium)
                manga.author?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val infoStatus = update?.manga?.status
                if (infoStatus != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(percent = 50),
                    ) {
                        Text(
                            mangaStatusText(infoStatus),
                            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                update?.manga?.genre?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ runCatching { state.library.toggleInLibrary(source.id, manga) } }) {
                Text(if (inLibrary) "In library" else "Add to library")
            }
            OutlinedButton({ openMangaInBrowser(state, source, manga) }) { Text("Open in browser") }
        }
        val u = update
        when {
            u == null && detailError != null -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    detailError ?: "detail error",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button({ attempt++ }) { Text("Retry") }
            }
            u == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                u.manga.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        Modifier.widthIn(max = 720.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (u.chapters.isEmpty()) {
                    EmptyNotice("No chapters yet.")
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
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { state.screen = Screen.Reader(source, sources, manga, chapter) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    chapter.displayName(),
                                    Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (read) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (read) {
                                    Icon(
                                        DareaderIcons.Check,
                                        contentDescription = "Read",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
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

// -------------------------------------------------------------------- reader

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
    val scope = rememberCoroutineScope()
    var sliderPos by remember(pages) { mutableStateOf(listState.firstVisibleItemIndex.toFloat()) }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        sliderPos = listState.firstVisibleItemIndex.toFloat()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton({ state.screen = Screen.Detail(source, sources, manga) }) {
                Icon(DareaderIcons.Back, "Back to chapters")
            }
            Text(
                chapter.displayName(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val total = totalPages
            if (total != null && total > 0) {
                Text(
                    "${visiblePage.coerceAtMost(total)} / $total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val list = pages
        val failure = readerError
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                failure != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            failure,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button({ attempt++ }) { Text("Retry") }
                    }
                }
                http == null || list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pages in this chapter.", style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(list, key = { it.index }) { page ->
                        PageView(http, page)
                    }
                }
            }
        }
        val total = totalPages
        if (list != null && total != null && total > 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    { prev?.let { state.screen = Screen.Reader(source, sources, manga, it) } },
                    enabled = prev != null,
                ) { Text("Prev chapter") }
                Slider(
                    value = sliderPos.coerceIn(0f, (total - 1).toFloat()),
                    onValueChange = { sliderPos = it },
                    valueRange = 0f..(total - 1).toFloat(),
                    onValueChangeFinished = { scope.launch { listState.scrollToItem(sliderPos.roundToInt()) } },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    { next?.let { state.screen = Screen.Reader(source, sources, manga, it) } },
                    enabled = next != null,
                ) { Text("Next chapter") }
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
        image != null -> androidx.compose.foundation.Image(
            image,
            null,
            Modifier.fillMaxWidth(),
            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
        )
        failed -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Failed to load page ${page.index + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button({ attempt++ }) { Text("Retry") }
            }
        }
        else -> Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

// ------------------------------------------------------------------ settings

@Composable
private fun SettingsScreen(state: AppState, source: Source, sources: List<Source>) {
    val configurable = source as? ConfigurableSource
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton({ state.screen = Screen.Browse(sources) }) { Icon(DareaderIcons.Back, "Back") }
            ScreenTitle("${source.name} settings")
        }
        if (configurable == null) {
            EmptyNotice("This source has no settings.") {
                OutlinedButton({ state.screen = Screen.Browse(sources) }) { Text("Back to browse") }
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
        built.exceptionOrNull()?.message?.let { message ->
            Text(
                "Settings error: $message",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            return
        }
        val count = remember(source) { runCatching { screen?.preferenceCount ?: 0 }.getOrDefault(0) }
        if (screen == null || count == 0) {
            EmptyNotice("No settings for this source.") {
                OutlinedButton({ state.screen = Screen.Browse(sources) }) { Text("Back to browse") }
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Row(
                Modifier.fillMaxWidth().clickable {
                    checked = !checked
                    pref.setChecked(checked)
                }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    summary?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        is EditTextPreference -> {
            var text by remember(pref.key ?: "text-$index") { mutableStateOf(pref.getText() ?: "") }
            Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(text, {
                    text = it
                    pref.setText(it)
                }, Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.large)
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
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    OutlinedButton({ expanded = true }) { Text(currentLabel ?: "Select") }
                    DropdownMenu(expanded, { expanded = false }) {
                        entryValues.forEachIndexed { i, entryValue ->
                            DropdownMenuItem(
                                { Text(entries.getOrNull(i)?.toString() ?: entryValue.toString()) },
                                {
                                    value = entryValue.toString()
                                    pref.setValue(entryValue.toString())
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        else -> {
            Column(Modifier.fillMaxWidth().padding(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- more

@Composable
private fun MoreScreen(state: AppState, dark: Boolean) {
    val installed by state.extensions.installed.collectAsState()
    val entries by state.library.entries.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("More", "dareader ${AppInfo.getVersionName()}")
        SettingRow("Dark theme", if (dark) "Enabled" else "Disabled") {
            Switch(dark, { ThemeMode.set(it) })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingRow(
            "Loaded extension",
            if (state.transientSources.isEmpty()) {
                "None — nothing loaded for this session"
            } else {
                "${state.transientSources.size} sources: ${state.transientSources.joinToString { it.name }.take(80)}"
            },
        ) {
            OutlinedButton(
                { state.closeExtension() },
                enabled = state.transientSources.isNotEmpty(),
            ) { Text("Unload") }
        }
        SettingRow("Installed extensions", "${installed.size} staged in the data directory") {
            OutlinedButton({ state.screen = Screen.Setup }) { Text("Manage") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoLine("Data directory", state.dataDir.toString())
        InfoLine("Library", "${entries.size} titles")
        InfoLine("Runtime", "extensions-lib 1.3 – 1.7")
    }
}

@Composable
private fun SettingRow(title: String, summary: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        control()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            Modifier.width(160.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

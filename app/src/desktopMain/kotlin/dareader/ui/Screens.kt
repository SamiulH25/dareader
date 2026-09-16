package dareader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import dareader.ext.di.DareaderGraph
import dareader.library.MangaKey
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
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
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
                        is Screen.Extensions -> ExtensionsScreen(state)
                        is Screen.Library -> LibraryScreen(state)
                        is Screen.History -> HistoryScreen(state)
                        is Screen.More -> MoreScreen(state, dark)
                        is Screen.Browse -> key(screen.sources) { BrowseScreen(state, screen.sources) }
                        is Screen.Detail -> DetailScreen(state, screen.source, screen.sources, screen.manga)
                        is Screen.Reader -> ReaderScreen(state, screen.source, screen.sources, screen.manga, screen.chapter)
                        is Screen.Settings -> SettingsScreen(state, screen.source, screen.sources)
                    }
                    ErrorBanner(state, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                }
                StatusBar(state, dark)
            }
        }
        TrustDialog(state)
    }
}

/**
 * Non-blocking failure banner for background actions (installs, extension
 * loads, demos). Screen-bound fetch failures render inline instead; the two
 * sinks never show the same failure twice.
 */
@Composable
private fun ErrorBanner(state: AppState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.error != null,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
        modifier = modifier,
    ) {
        val message = state.error ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.widthIn(max = 640.dp).padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    message,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton({ state.dismissError() }) { Text("Dismiss") }
            }
        }
    }
}

private enum class Destination(val label: String) {
    Library("Library"),
    History("History"),
    Browse("Browse"),
    Extensions("Extensions"),
    More("More"),
}

@Composable
private fun DareaderNavRail(state: AppState) {
    val selected = when (state.rootScreen()) {
        is Screen.Library -> Destination.Library
        is Screen.History -> Destination.History
        is Screen.Extensions -> Destination.Extensions
        is Screen.More -> Destination.More
        is Screen.Browse -> Destination.Browse
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
        RailItem(Destination.Library, DareaderIcons.Library, selected) { state.navigateRoot(Screen.Library) }
        RailItem(Destination.History, DareaderIcons.History, selected) { state.navigateRoot(Screen.History) }
        RailItem(Destination.Browse, DareaderIcons.Browse, selected) {
            state.navigateRoot(Screen.Browse(state.availableSources))
        }
        RailItem(Destination.Extensions, DareaderIcons.Extensions, selected) { state.navigateRoot(Screen.Extensions) }
        RailItem(Destination.More, DareaderIcons.More, selected) { state.navigateRoot(Screen.More) }
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

// The extensions screen (index + installs + manual APK) lives in
// ExtensionsScreens.kt.

// -------------------------------------------------------------------- browse

private enum class BrowseMode(val label: String) { POPULAR("Popular"), LATEST("Latest"), SEARCH("Search") }

@Composable
private fun BrowseScreen(state: AppState, sources: List<Source>) {
    var selected by remember {
        mutableStateOf(
            sources.firstOrNull { sourceMatchesLanguage(it.lang, AppSettings.language.value) }
                ?: sources.firstOrNull(),
        )
    }
    var sourceQuery by remember { mutableStateOf("") }
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
                error = state.screenError ?: "browse error"
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
        // Switching to SEARCH must not fire a request before the user submits a query.
        if (mode == BrowseMode.SEARCH && query.isBlank()) return@LaunchedEffect
        loadPage(source, mode, 1, append = false, query, filters)
    }

    if (sources.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle("Browse")
            EmptyNotice("No sources loaded. Load or install an extension first.") {
                Button({ state.navigateRoot(Screen.Extensions) }) { Text("Go to Extensions") }
            }
        }
        return
    }

    val latestSupported = selected?.supportsLatest == true

    Row(Modifier.fillMaxSize()) {
        SourcePane(
            sources = sources,
            selected = selected,
            query = sourceQuery,
            onQueryChange = { sourceQuery = it },
            onPick = { pick(it) },
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                selected?.let { SourceAvatar(it, 32.dp) }
                Text(
                    selected?.name ?: "Browse",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val settingsTarget = selected as? ConfigurableSource
                if (settingsTarget != null) {
                    TextButton({ state.navigate(Screen.Settings(settingsTarget, sources)) }) { Text("Source settings") }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(mode == BrowseMode.POPULAR, { mode = BrowseMode.POPULAR }, label = { Text(BrowseMode.POPULAR.label) })
                if (latestSupported) {
                    FilterChip(mode == BrowseMode.LATEST, { mode = BrowseMode.LATEST }, label = { Text(BrowseMode.LATEST.label) })
                }
                FilterChip(mode == BrowseMode.SEARCH, { mode = BrowseMode.SEARCH }, label = { Text(BrowseMode.SEARCH.label) })
                if (mangas.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${mangas.size} titles",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (mode == BrowseMode.SEARCH) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        query,
                        { query = it },
                        Modifier.weight(1f),
                        placeholder = { Text("Search titles") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                    Button(
                        { loadFirst() },
                        enabled = query.isNotBlank() || filters.isNotEmpty(),
                    ) { Text("Search") }
                }
                key(filterTick) {
                    FilterListView(filters) { filterTick++ }
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
                    TextButton({ loadFirst() }) { Text("Retry") }
                }
            }
            val current = selected
            when {
                loading && mangas.isEmpty() -> MangaGridSkeleton()

                mangas.isEmpty() && error == null && current != null ->
                    EmptyNotice(
                        if (mode == BrowseMode.SEARCH) {
                            "Nothing yet. Search ${current.name} for a title."
                        } else {
                            "No titles from ${current.name} right now."
                        },
                    )

                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(mangas, key = { it.url }) { manga ->
                            if (current != null) {
                                MangaCard(current, manga) { state.openDetail(current, sources, manga) }
                            }
                        }
                        if (hasNext) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    } else {
                                        OutlinedButton(
                                            { selected?.let { loadPage(it, mode, pageNo + 1, append = true, query, filters) } },
                                        ) { Text("Load more") }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

/** Deterministic per-source tint pairs; identity, not decoration. */
private val SourceTints = listOf(
    Color(0xFF2F4A73) to Color(0xFFD7E3FF),
    Color(0xFF463A6B) to Color(0xFFE6DCFF),
    Color(0xFF2F5347) to Color(0xFFCFEADC),
    Color(0xFF61394A) to Color(0xFFFFD8E1),
    Color(0xFF57492E) to Color(0xFFF3E1C0),
    Color(0xFF39485C) to Color(0xFFD8E2F0),
)

/** Letter tile that gives each source a stable visual identity. */
@Composable
private fun SourceAvatar(source: Source, size: Dp = 30.dp) {
    val tint = SourceTints[(source.id % SourceTints.size).toInt()]
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(tint.first),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            source.name.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = tint.second,
        )
    }
}

/** Source rail: search, language filter, naturally sorted list. */
@Composable
private fun SourcePane(
    sources: List<Source>,
    selected: Source?,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (Source) -> Unit,
) {
    val language by AppSettings.language.collectAsState()
    var languageMenuOpen by remember { mutableStateOf(false) }
    val languages =
        remember(sources) {
            sources.map { it.lang.lowercase() }.filter { it.isNotBlank() }.distinct().sorted()
        }
    val shown =
        remember(sources, query, language) {
            val needle = query.trim()
            sources
                .filter { sourceMatchesLanguage(it.lang, language) }
                .filter {
                    needle.isBlank() ||
                        it.name.contains(needle, ignoreCase = true) ||
                        it.lang.contains(needle, ignoreCase = true)
                }
                .sortedWith { a, b -> a.name.compareToCaseInsensitiveNaturalOrder(b.name) }
        }

    Column(
        Modifier
            .width(264.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Sources", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Box {
                TextButton({ languageMenuOpen = true }) {
                    Text(language?.uppercase() ?: "All", style = MaterialTheme.typography.labelLarge)
                }
                DropdownMenu(languageMenuOpen, { languageMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(ALL_LANGUAGES) },
                        onClick = {
                            AppSettings.setLanguage(null)
                            languageMenuOpen = false
                        },
                        trailingIcon = {
                            if (language == null) Icon(DareaderIcons.Check, null, Modifier.size(16.dp))
                        },
                    )
                    languages.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.uppercase()) },
                            onClick = {
                                AppSettings.setLanguage(option)
                                languageMenuOpen = false
                            },
                            trailingIcon = {
                                if (option.equals(language, ignoreCase = true)) {
                                    Icon(DareaderIcons.Check, null, Modifier.size(16.dp))
                                }
                            },
                        )
                    }
                }
            }
            Text(
                if (query.isBlank() && language == null) "${sources.size}" else "${shown.size} / ${sources.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            query,
            onQueryChange,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search sources") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(shown, key = { it.id }) { source ->
                SourceRow(source, source == selected) { onPick(source) }
            }
            if (shown.isEmpty()) {
                item {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            when {
                                query.isNotBlank() -> "No sources match \"$query\""
                                language != null -> "No ${language?.uppercase()} sources installed."
                                else -> "No sources yet."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (query.isBlank() && language != null) {
                            TextButton({ AppSettings.setLanguage(null) }) { Text("Show all languages") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: Source, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> Color.Transparent
        },
        label = "sourceRowBackground",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceAvatar(source)
        Text(
            source.name,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        if (source.lang.isNotBlank()) {
            Text(
                source.lang.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Quiet indeterminate placeholder for the title grid. */
@Composable
private fun MangaGridSkeleton(cells: Int = 12) {
    val transition = rememberInfiniteTransition(label = "mangaSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "mangaSkeletonAlpha",
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(cells) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)),
            )
        }
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

/** Cover-first grid card: the title rides on the cover itself. */
@Composable
private fun MangaCard(source: Source?, manga: SManga, onClick: () -> Unit) {
    val http = source as? HttpSource
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scrim by animateFloatAsState(if (hovered) 0.94f else 0.8f, label = "cardScrim")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        CoverImage(
            imageUrl = manga.thumbnail_url,
            http = http,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = manga.displayTitle(),
        )
        // The scrim keeps the title legible over any artwork; white on it is deliberate.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = scrim),
                    ),
                ),
        )
        Text(
            manga.displayTitle(),
            Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    val readCounts by state.library.readCounts.collectAsState()
    val progressRevision by state.library.progressRevision.collectAsState()
    val inLibrary = remember(entries, source.id, manga.url) {
        entries.any { it.sourceId == source.id && it.manga.url == manga.url }
    }
    LaunchedEffect(manga.url, attempt) {
        detailError = null
        update = state.detail(source, manga)
        if (update == null) detailError = state.screenError ?: "detail error"
    }
    val http = source as? HttpSource
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton({ state.goBack() }) { Icon(DareaderIcons.Back, "Back") }
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
                if (infoStatus != null && infoStatus != SManga.UNKNOWN) {
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
                    val readCount = readCounts[MangaKey(source.id, manga.url)] ?: 0
                    val allRead = readCount >= u.chapters.size
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Chapters (${u.chapters.size})", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            {
                                runCatching {
                                    state.library.markChaptersRead(
                                        sourceId = source.id,
                                        mangaUrl = manga.url,
                                        chapterUrls = u.chapters.map { it.url },
                                        read = !allRead,
                                    )
                                }
                            },
                        ) { Text(if (allRead) "Mark all unread" else "Mark all read") }
                        TextButton({ ascending = !ascending }) { Text(if (ascending) "Newest first" else "Oldest first") }
                    }
                    val shown = remember(u.chapters, ascending) { if (ascending) u.chapters else u.chapters.reversed() }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.url }) { chapter: SChapter ->
                            val read = remember(source.id, manga.url, chapter.url, progressRevision) {
                                runCatching { state.library.isChapterRead(source.id, manga.url, chapter.url) }
                                    .getOrDefault(false)
                            }
                            val interaction = remember { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            val background by animateColorAsState(
                                if (hovered) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                                label = "chapterRowBackground",
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .hoverable(interaction)
                                    .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                                        state.navigate(Screen.Reader(source, sources, manga, chapter))
                                    }
                                    .background(background)
                                    .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    chapter.displayName(),
                                    Modifier.weight(1f).padding(vertical = 6.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (read) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                IconButton(
                                    {
                                        runCatching {
                                            state.library.markChapterRead(source.id, manga.url, chapter.url, !read)
                                        }
                                    },
                                ) {
                                    Icon(
                                        DareaderIcons.Check,
                                        contentDescription = if (read) "Mark unread" else "Mark read",
                                        tint = if (read) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        },
                                        modifier = Modifier.size(18.dp),
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
    val readerMode by ReaderSettings.mode.collectAsState()
    val autoAdvance by ReaderSettings.autoAdvance.collectAsState()
    var pages by remember { mutableStateOf<List<Page>?>(null) }
    var readerError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var chapters by remember { mutableStateOf<List<SChapter>?>(null) }
    var modeMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(chapter.url, attempt) {
        // A new chapter (or a retry) starts clean: never show the previous
        // chapter's pages under the new title.
        pages = null
        readerError = null
        if (http != null) {
            pages = state.pages(source, chapter)
            if (pages == null) readerError = state.screenError ?: "reader error"
        } else {
            readerError = "reader needs an online source"
        }
    }
    LaunchedEffect(manga.url) {
        chapters = state.detail(source, manga)?.chapters
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
    val startIndex = remember(source.id, manga.url, chapter.url) {
        runCatching { state.library.getProgress(source.id, manga.url, chapter.url) }.getOrDefault(0)
    }
    val webtoonState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val pagerState = rememberPagerState(initialPage = startIndex) { pages?.size ?: 0 }
    val totalPages = pages?.size
    val currentPage =
        if (readerMode == ReaderMode.WEBTOON) webtoonState.firstVisibleItemIndex else pagerState.currentPage

    // Switching modes resumes at the same page in the other container.
    LaunchedEffect(readerMode) {
        val target = runCatching { state.library.getProgress(source.id, manga.url, chapter.url) }.getOrDefault(0)
        if (readerMode == ReaderMode.WEBTOON) webtoonState.scrollToItem(target) else pagerState.scrollToPage(target)
    }

    LaunchedEffect(currentPage, totalPages, readerMode) {
        // Coalesce rapid scrolls: a new page cancels this block before it writes.
        kotlinx.coroutines.delay(500)
        state.saveProgress(source.id, manga.url, chapter.url, currentPage)
        if (totalPages != null && totalPages > 0 && currentPage >= totalPages - 1) {
            runCatching { state.library.markChapterRead(source.id, manga.url, chapter.url, true) }
            // Advance only when the end was *read into*: a chapter that opens
            // already at its last page (resume, or a one-page chapter) stays.
            if (autoAdvance && next != null && startIndex < totalPages - 1) {
                kotlinx.coroutines.delay(1200)
                state.navigate(Screen.Reader(source, sources, manga, next))
            }
        }
    }

    val visiblePage = currentPage + 1
    val scope = rememberCoroutineScope()
    var sliderPos by remember(pages) { mutableStateOf(startIndex.toFloat()) }
    LaunchedEffect(currentPage) {
        sliderPos = currentPage.toFloat()
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
            IconButton({ state.goBack() }) {
                Icon(DareaderIcons.Back, "Back to chapters")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    manga.displayTitle(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val total = totalPages
            if (total != null && total > 0) {
                Text(
                    "${visiblePage.coerceAtMost(total)} / $total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                TextButton({ modeMenuOpen = true }) { Text(readerMode.label) }
                DropdownMenu(modeMenuOpen, { modeMenuOpen = false }) {
                    ReaderMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                ReaderSettings.setMode(option)
                                modeMenuOpen = false
                            },
                            trailingIcon = {
                                if (option == readerMode) {
                                    Icon(DareaderIcons.Check, null, Modifier.size(16.dp))
                                }
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text(if (autoAdvance) "Auto-advance on" else "Auto-advance off") },
                        onClick = {
                            ReaderSettings.setAutoAdvance(!autoAdvance)
                            modeMenuOpen = false
                        },
                        trailingIcon = {
                            if (autoAdvance) {
                                Icon(DareaderIcons.Check, null, Modifier.size(16.dp))
                            }
                        },
                    )
                }
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
                readerMode == ReaderMode.WEBTOON -> LazyColumn(Modifier.fillMaxSize(), state = webtoonState) {
                    items(list, key = { it.index }) { page ->
                        PageView(http, page, ContentScale.FillWidth, Modifier.fillMaxWidth())
                    }
                }
                else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                    PageView(
                        http,
                        list[index],
                        if (readerMode == ReaderMode.FIT) ContentScale.FillHeight else ContentScale.Fit,
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
        val total = totalPages
        val hasChapterNav = prev != null || next != null
        if (list != null && ((total != null && total > 1) || hasChapterNav)) {
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
                    { prev?.let { state.navigate(Screen.Reader(source, sources, manga, it)) } },
                    enabled = prev != null,
                ) { Text("Prev chapter") }
                if (total != null && total > 1) {
                    Slider(
                        value = sliderPos.coerceIn(0f, (total - 1).toFloat()),
                        onValueChange = { sliderPos = it },
                        valueRange = 0f..(total - 1).toFloat(),
                        onValueChangeFinished = {
                            scope.launch {
                                val target = sliderPos.roundToInt()
                                if (readerMode == ReaderMode.WEBTOON) {
                                    webtoonState.scrollToItem(target)
                                } else {
                                    pagerState.scrollToPage(target)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(
                    { next?.let { state.navigate(Screen.Reader(source, sources, manga, it)) } },
                    enabled = next != null,
                ) { Text("Next chapter") }
            }
        }
    }
}

@Composable
private fun PageView(source: HttpSource, page: Page, scale: ContentScale, modifier: Modifier) {
    var bitmap by remember(page.url, page.index) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(page.url, page.index) { mutableStateOf(false) }
    var attempt by remember(page.url, page.index) { mutableStateOf(0) }
    LaunchedEffect(page.url, page.index, attempt) {
        failed = false
        bitmap = runCatching { PageImages.page(source, page) }
            .onFailure { failed = true }
            .getOrNull()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        when {
            image != null -> androidx.compose.foundation.Image(
                image,
                "Page ${page.index + 1}",
                Modifier.fillMaxSize(),
                contentScale = scale,
            )
            failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Page ${page.index + 1} did not load.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton({ attempt++ }) { Text("Retry") }
            }
            else -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
        }
    }
}

// ------------------------------------------------------------------ settings

@Composable
private fun SettingsScreen(state: AppState, source: Source, sources: List<Source>) {
    val configurable = source as? ConfigurableSource
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton({ state.goBack() }) { Icon(DareaderIcons.Back, "Back") }
            ScreenTitle("${source.name} settings")
        }
        if (configurable == null) {
            EmptyNotice("This source has no settings.") {
                OutlinedButton({ state.goBack() }) { Text("Back to browse") }
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
                OutlinedButton({ state.goBack() }) { Text("Back to browse") }
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
        is MultiSelectListPreference -> {
            var values by remember(pref.key ?: "multi-$index") { mutableStateOf(pref.getValues()) }
            val entries = pref.getEntries() ?: emptyArray()
            val entryValues = pref.getEntryValues() ?: emptyArray()
            Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entryValues.forEachIndexed { i, entryValue ->
                    val entry = entryValue.toString()
                    val checked = entry in values
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            values = if (checked) values - entry else values + entry
                            pref.setValues(values)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked, null)
                        Text(
                            entries.getOrNull(i)?.toString() ?: entry,
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
            OutlinedButton({ state.navigateRoot(Screen.Extensions) }) { Text("Manage") }
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

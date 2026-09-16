package dareader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dareader.ext.di.DareaderGraph
import dareader.ext.load.LoadedExtension
import dareader.ext.load.dexToJar
import dareader.ext.load.loadExtensionSources
import dareader.ext.manager.ExtensionManager
import dareader.ext.pkg.downloadApk
import dareader.ext.pkg.parseApkManifest
import dareader.ext.pkg.requireAccepted
import dareader.ext.store.NetworkExtensionStore
import dareader.ext.store.defaultHttpClient
import dareader.ext.store.fetchSplitExtensionList
import dareader.ext.store.fetchStore
import dareader.library.LibraryStore
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.displayTitle
import eu.kanade.tachiyomi.source.model.displayName
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
/** One store index, fetched once per open: extension list plus its signing key. */
data class StoreIndex(
    val name: String,
    val signingKey: String,
    val extensions: List<NetworkExtensionStore.Extension>,
)

/** Pending untrusted-install decision surfaced to the UI; resolved via [AppState.answerTrust]. */
data class TrustPrompt(
    val pkg: String,
    val versionCode: Long,
    val certHashes: List<String>,
    val artifactSha256: String? = null,
    val storeKey: String,
)

sealed interface Screen {
    data object Extensions : Screen
    data object Library : Screen
    data object History : Screen
    data object More : Screen
    data class Browse(val sources: List<Source>) : Screen
    data class Detail(val source: Source, val sources: List<Source>, val manga: SManga) : Screen
    data class Reader(val source: Source, val sources: List<Source>, val manga: SManga, val chapter: SChapter) : Screen
    data class Settings(val source: Source, val sources: List<Source>) : Screen
}

/** Deepest history the shell keeps; older pushes fall off the bottom. */
private const val MAX_BACK_STACK = 32

/** Default Keiyoushi store index; the Store screen and kiosk entry share it. */
internal const val DEFAULT_STORE_INDEX_URL =
    "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"

/** Kiosk stop points for the `--ui … --at=<target>` verification path. */
enum class DemoTarget {
    BROWSE,
    DETAIL,
    READER,
    ;

    companion object {
        fun parse(name: String?): DemoTarget =
            when (name?.lowercase()) {
                "browse" -> BROWSE
                "detail" -> DETAIL
                "reader", null -> READER
                else -> READER
            }
    }
}

/** XDG-aware data dir, overridable via `-Ddareader.data.dir=…`. */
fun defaultDataDir(): Path {
    System.getProperty("dareader.data.dir")?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
    val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
    val base = if (xdg != null) Path.of(xdg) else Path.of(System.getProperty("user.home"), ".local", "share")
    return base.resolve("dareader")
}

class AppState(val dataDir: Path = defaultDataDir()) {
    private var currentScreen: Screen by mutableStateOf(Screen.Extensions)

    /**
     * Currently shown screen. Direct assignment replaces the screen without
     * touching the back stack; [navigate] pushes a screen Back should undo,
     * [navigateRoot] switches section and drops the stack.
     */
    var screen: Screen
        get() = currentScreen
        set(value) {
            if (value != currentScreen) {
                error = null
                screenError = null
            }
            currentScreen = value
        }

    /**
     * Store index the Store screen should open as soon as it appears; set by
     * the kiosk entry (`--at=store`) and cleared once consumed.
     */
    var pendingStoreUrl: String? by mutableStateOf(null)

    private val backStack = mutableStateListOf<Screen>()

    var status: String by mutableStateOf("pick an extension APK to load")

    /** Last user-visible failure; cleared via [dismissError]. Never throws to composition. */
    var error: String? by mutableStateOf(null)
        private set

    /**
     * Failure of the current screen's own fetch (browse/detail/reader/store
     * load). Screens render it inline next to a retry action; it is cleared on
     * navigation and on the next successful screen-bound call.
     */
    var screenError: String? by mutableStateOf(null)
        private set

    fun dismissError() {
        error = null
    }

    /** UI-thread-safe failure sink for screens that cannot handle the error locally. */
    fun reportError(message: String) {
        status = message
        error = message
    }

    /** Shows [to] and remembers the current screen for [goBack]. */
    fun navigate(to: Screen) {
        if (to == screen) return
        backStack.add(screen)
        if (backStack.size > MAX_BACK_STACK) backStack.removeAt(0)
        screen = to
    }

    /** Rail switch: shows [to] as a fresh section, dropping the back stack. */
    fun navigateRoot(to: Screen) {
        backStack.clear()
        screen = to
    }

    /** Pops to the screen the current flow came from; a no-op when the stack is empty. */
    fun goBack() {
        val previous = backStack.removeLastOrNull() ?: return
        screen = previous
    }

    /** Screen the rail highlights: the section pushed screens came from. */
    fun rootScreen(): Screen = backStack.firstOrNull() ?: screen

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = defaultHttpClient()
    private val loadLock = Any()
    private val sourceCalls = SourceCallTracker()
    private var loaded: LoadedExtension? = null
    private var loadedJar: Path? = null

    /**
     * Sources of the single-shot (uninstalled) extension currently open, if
     * any. Mirrors [loaded] so the shell can offer Browse/unload without
     * reaching into the load lock from composition.
     */
    var transientSources: List<Source> by mutableStateOf(emptyList())
        private set

    val library = LibraryStore(dataDir)

    /** Set when the manager's trust gate needs a user decision; resolved via [answerTrust]. */
    var trustRequest: TrustPrompt? by mutableStateOf(null)
        private set
    private val trustLock = Any()
    private var pendingTrust: CompletableFuture<Boolean>? = null

    /**
     * The manager call runs on the IO scope and blocks inside [onTrustRequest]
     * until the UI answers via [answerTrust].
     */
    val extensions = ExtensionManager(dataDir, client) { request ->
        val future = CompletableFuture<Boolean>()
        synchronized(trustLock) {
            pendingTrust = future
            trustRequest =
                TrustPrompt(
                    request.pkg,
                    request.versionCode,
                    request.certHashes,
                    request.artifactSha256,
                    request.storeKey,
                )
        }
        try {
            future.get()
        } catch (_: Exception) {
            false
        }
    }

    /** Every source the shell can browse: installed extensions plus the loaded one. */
    val availableSources: List<Source>
        get() = (extensions.installed.value.flatMap { it.sources } + transientSources)
            .distinctBy { it.id }

    fun answerTrust(approve: Boolean) {
        val future = synchronized(trustLock) {
            val pending = pendingTrust
            pendingTrust = null
            trustRequest = null
            pending
        }
        future?.complete(approve)
    }

    init {
        DareaderGraph.ensureCore()
        // Staged extensions load off the UI thread; the installed StateFlow
        // populates when the scan finishes.
        scope.launch { extensions.rescan() }
    }

    /** Window-close hook: releases the transient handle and every installed loader. */
    fun shutdown() {
        closeExtension()
        extensions.close()
    }

    private fun report(op: String, e: Throwable) {
        val message = "$op: ${e.message ?: e.javaClass.simpleName}"
        status = message
        error = message
    }

    /**
     * Retains [ext] + [jar]; closes and deletes the previous handle first.
     * The jar MUST outlive the loader (lazy class loads memory-map it), so
     * deletion happens only here, after [LoadedExtension.close] — never while
     * the loader is open.
     */
    private fun setLoaded(ext: LoadedExtension, jar: Path) {
        val old: LoadedExtension?
        val oldJar: Path?
        synchronized(loadLock) {
            old = loaded
            oldJar = loadedJar
            loaded = ext
            loadedJar = jar
        }
        transientSources = ext.sources
        releaseHandle(old, oldJar)
    }

    /** Closes [handle]/deletes [jar] only after every in-flight source call drained. */
    private fun releaseHandle(handle: LoadedExtension?, jar: Path?) {
        if (handle == null && jar == null) return
        scope.launch {
            drainSourceCalls()
            if (handle != null) runCatching { handle.close() }
            if (jar != null) runCatching { Files.deleteIfExists(jar) }
        }
    }

    private suspend fun drainSourceCalls() {
        withContext(Dispatchers.IO) { sourceCalls.awaitIdle() }
    }

    fun closeExtension() {
        val old: LoadedExtension?
        val oldJar: Path?
        synchronized(loadLock) {
            old = loaded
            oldJar = loadedJar
            loaded = null
            loadedJar = null
        }
        transientSources = emptyList()
        releaseHandle(old, oldJar)
    }

    /** Single-shot temp load (no install, no trust gate); jar/apk are deleted after use. */
    fun loadExtension(apkUrl: String) {
        status = "downloading…"
        scope.launch {
            runCatching {
                val apk = downloadApk(client, apkUrl)
                try {
                    val manifest = parseApkManifest(apk)
                    status = manifest.judge()
                    manifest.requireAccepted()
                    val raw = requireNotNull(manifest.entryClass) { "no source class or factory" }
                    val fqcn = if (raw.startsWith(".")) manifest.packageName + raw else raw
                    val jar = dexToJar(apk)
                    var retained = false
                    try {
                        val ext = loadExtensionSources(jar, fqcn, manifest.packageName)
                        setLoaded(ext, jar)
                        retained = true
                        navigateRoot(Screen.Browse(ext.sources))
                    } finally {
                        if (!retained) Files.deleteIfExists(jar)
                    }
                } finally {
                    Files.deleteIfExists(apk)
                }
            }.onFailure { report("error", it) }
        }
    }

    /**
     * Kiosk path for headless verification: loads [apkUrl], then walks
     * browse → detail → reader, stopping at [stopAt] (default `reader`).
     */
    fun autoDemo(apkUrl: String, stopAt: String? = null) {
        val target = DemoTarget.parse(stopAt)
        status = "demo: loading…"
        scope.launch {
            runCatching {
                val apk = downloadApk(client, apkUrl)
                try {
                    val manifest = parseApkManifest(apk)
                    manifest.requireAccepted()
                    val raw = requireNotNull(manifest.entryClass) { "no source class or factory" }
                    val fqcn = if (raw.startsWith(".")) manifest.packageName + raw else raw
                    val jar = dexToJar(apk)
                    var retained = false
                    try {
                        val ext = loadExtensionSources(jar, fqcn, manifest.packageName)
                        setLoaded(ext, jar)
                        retained = true
                        val source = ext.sources.firstOrNull()
                        if (source == null || target == DemoTarget.BROWSE) {
                            navigateRoot(Screen.Browse(ext.sources))
                            status = "kiosk: ${ext.sources.size} sources"
                            return@runCatching
                        }
                        val titles = popularTitles(source)
                        val manga = titles.mangas.firstOrNull()
                        if (manga == null) {
                            navigateRoot(Screen.Browse(ext.sources))
                            status = "demo: no titles"
                            return@runCatching
                        }
                        val detail = mangaUpdate(source, manga)
                        if (target == DemoTarget.DETAIL) {
                            navigate(Screen.Detail(source, ext.sources, detail.manga))
                            status = "kiosk: ${manga.displayTitle()}"
                            return@runCatching
                        }
                        val chapter = detail.chapters.firstOrNull()
                        if (chapter == null) {
                            navigate(Screen.Detail(source, ext.sources, detail.manga))
                            status = "demo: ${manga.displayTitle()} has no chapters"
                            return@runCatching
                        }
                        chapterPages(source, chapter)
                        navigate(Screen.Reader(source, ext.sources, detail.manga, chapter))
                        status = "demo: ${manga.displayTitle()} / ${chapter.displayName()}"
                    } finally {
                        if (!retained) Files.deleteIfExists(jar)
                    }
                } finally {
                    Files.deleteIfExists(apk)
                }
            }.onFailure { report("demo error", it) }
        }
    }

    /** Kiosk entry that lands directly on a shell screen, loading nothing. */
    fun openScreen(name: String) {
        when (name.lowercase()) {
            "browse" -> navigateRoot(Screen.Browse(availableSources))
            "library" -> navigateRoot(Screen.Library)
            "history" -> navigateRoot(Screen.History)
            "store", "setup", "extensions" -> {
                pendingStoreUrl = DEFAULT_STORE_INDEX_URL
                navigateRoot(Screen.Extensions)
            }
            "more" -> navigateRoot(Screen.More)
            else -> Unit
        }
        status = "kiosk: $name"
    }

    /**
     * Persisted install via the manager. When the trust gate fires, [trustRequest]
     * is set and this suspends until [answerTrust]; a rejection lands in error/status.
     */
    fun installExtension(apkUrl: String, storeKey: String? = null, iconUrl: String? = null) {
        status = "installing…"
        scope.launch {
            runCatching { extensions.installFromApkUrl(apkUrl, storeKey, iconUrl) }
                .onSuccess {
                    status = "installed ${it.pkg} (${it.sources.size} sources)"
                    navigateRoot(Screen.Browse(it.sources))
                }
                .onFailure { report("install error", it) }
        }
    }

    /**
     * Store install: prefers the index's prebuilt jar (skips dex2jar, fetches
     * the entry class by scan) and falls back to the APK when the store only
     * publishes one.
     */
    fun installStoreExtension(ext: NetworkExtensionStore.Extension, storeKey: String? = null) {
        val jarUrl = ext.resources.jarUrl?.takeUnless { it.isBlank() }
        val iconUrl = ext.resources.iconUrl.ifBlank { null }
        status = "installing ${ext.name}…"
        scope.launch {
            runCatching {
                if (jarUrl != null) {
                    extensions.installFromJarUrl(
                        jarUrl = jarUrl,
                        pkg = ext.packageName,
                        versionName = ext.versionName,
                        versionCode = ext.versionCode,
                        extensionLib = ext.extensionLib,
                        storeKey = storeKey,
                        iconUrl = iconUrl,
                    )
                } else {
                    extensions.installFromApkUrl(ext.resources.apkUrl, storeKey, iconUrl)
                }
            }
                .onSuccess {
                    status = "installed ${it.pkg} (${it.sources.size} sources)"
                    navigateRoot(Screen.Browse(it.sources))
                }
                .onFailure { report("install error", it) }
        }
    }

    fun uninstallExtension(pkg: String) {
        scope.launch {
            drainSourceCalls()
            runCatching { extensions.uninstall(pkg) }
                .onSuccess { status = "uninstalled $pkg" }
                .onFailure { report("uninstall error", it) }
        }
    }

    /** Open detail and record it in history; never throws. */
    fun openDetail(source: Source, sources: List<Source>, manga: SManga) {
        runCatching { library.recordHistory(source.id, manga) }
        navigate(Screen.Detail(source, sources, manga))
    }

    /** Reader progress hook; delegates to the library store, never throws. */
    fun saveProgress(sourceId: Long, mangaUrl: String, chapterUrl: String, page: Int) {
        runCatching { library.saveProgress(sourceId, mangaUrl, chapterUrl, page) }
    }

    /**
     * Composition-safe source call that counts as in-flight for
     * [drainSourceCalls]: handle close/uninstall wait for it to finish, so a
     * fetch can never hit a classloader closed underneath it.
     */
    private suspend fun <T> safeSourceCall(op: String, block: suspend () -> T): T? {
        sourceCalls.enter()
        return try {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // leaving a screen cancels its fetches; that is not a failure
            } catch (e: Exception) {
                report(op, e)
                null
            }
        } finally {
            sourceCalls.exit()
        }
    }

    /**
     * Screen-bound call (browse/detail/reader/store load): the failure lands
     * in [screenError] for the screen's inline row, not in the global banner.
     */
    private suspend fun <T> screenCall(op: String, block: suspend () -> T): T? {
        sourceCalls.enter()
        return try {
            val result = block()
            screenError = null
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // leaving a screen cancels its fetches; that is not a failure
        } catch (e: Exception) {
            val message = "$op: ${e.message ?: e.javaClass.simpleName}"
            status = message
            screenError = message
            null
        } finally {
            sourceCalls.exit()
        }
    }

    /** Composition-safe source wrappers: failures land in error/status, never escape. */
    suspend fun popular(source: Source, page: Int = 1): MangasPage? =
        screenCall("browse error") { popularTitles(source, page) }

    suspend fun latest(source: Source, page: Int): MangasPage? =
        screenCall("browse error") { latestTitles(source, page) }

    suspend fun search(source: Source, page: Int, query: String, filters: FilterList): MangasPage? =
        screenCall("browse error") { searchTitles(source, page, query, filters) }

    /**
     * Detail fetch with a short memo: opening the reader right after the
     * detail screen (or switching chapters) reuses the same result instead of
     * re-running the full `getMangaUpdate`.
     */
    suspend fun detail(source: Source, manga: SManga): SMangaUpdate? {
        val mangaUrl = runCatching { manga.url }.getOrDefault("")
        val now = System.currentTimeMillis()
        detailCache?.let { (key, cached) ->
            if (key.first == source.id && key.second == mangaUrl && now - key.third <= detailTtlMillis) {
                return cached
            }
        }
        val update = screenCall("detail error") { mangaUpdate(source, manga) }
        if (update != null) detailCache = Triple(source.id, mangaUrl, now) to update
        return update
    }

    suspend fun pages(source: Source, chapter: SChapter): List<Page>? =
        screenCall("reader error") { chapterPages(source, chapter) }

    /** Signing keys of stores opened this session, keyed by index URL. */
    private val storeKeys = mutableMapOf<String, String>()

    /** Last detail fetch, reused for reader/chapter switches. */
    private var detailCache: Pair<Triple<Long, String, Long>, SMangaUpdate>? = null

    /** Test seam: age at which a cached detail fetch is considered stale. */
    internal var detailTtlMillis: Long = 60_000

    /**
     * Composition-safe store index fetch: one request for the index (split
     * lists follow their own URL), signing key cached for installs. Failures
     * land in error/status and never escape.
     */
    suspend fun fetchStoreIndex(indexUrl: String, packageFilter: String? = null): StoreIndex? =
        screenCall("store error") {
            withContext(Dispatchers.IO) {
                val store = fetchStore(client, indexUrl)
                if (store.signingKey.isNotBlank()) storeKeys[indexUrl] = store.signingKey
                val list = store.extensionList
                    ?: store.extensionListUrl?.let { fetchSplitExtensionList(client, it) }
                val extensions = list?.extensions ?: emptyList()
                StoreIndex(
                    name = store.name,
                    signingKey = store.signingKey,
                    extensions = if (packageFilter == null) {
                        extensions
                    } else {
                        extensions.filter { it.packageName.contains(packageFilter) }
                    },
                )
            }
        }

    /** Signing key of a store opened earlier this session, if any. */
    fun storeKey(indexUrl: String): String? = storeKeys[indexUrl]

    fun sourceFilters(source: Source): FilterList =
        runCatching { source.getFilterList() }.getOrDefault(FilterList())
}

suspend fun popularTitles(source: Source, page: Int = 1): MangasPage =
    withContext(Dispatchers.IO) { source.getPopularManga(page) }

suspend fun latestTitles(source: Source, page: Int): MangasPage =
    withContext(Dispatchers.IO) { source.getLatestUpdates(page) }

suspend fun searchTitles(source: Source, page: Int, query: String, filters: FilterList): MangasPage =
    withContext(Dispatchers.IO) { source.getSearchManga(page, query, filters) }

suspend fun mangaUpdate(source: Source, manga: SManga): SMangaUpdate =
    withContext(Dispatchers.IO) {
        val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)
        // Detail responses may be fresh instances missing fields the browse entry had; backfill.
        val fresh = update.manga
        if (runCatching { fresh.title }.getOrNull().isNullOrBlank()) fresh.title = manga.displayTitle()
        if (fresh.thumbnail_url.isNullOrBlank()) fresh.thumbnail_url = runCatching { manga.thumbnail_url }.getOrNull()
        if (fresh.author.isNullOrBlank()) fresh.author = runCatching { manga.author }.getOrNull()
        if (fresh.artist.isNullOrBlank()) fresh.artist = runCatching { manga.artist }.getOrNull()
        if (fresh.genre.isNullOrBlank()) fresh.genre = runCatching { manga.genre }.getOrNull()
        update
    }

suspend fun chapterPages(source: Source, chapter: SChapter): List<Page> =
    withContext(Dispatchers.IO) { source.getPageList(chapter) }

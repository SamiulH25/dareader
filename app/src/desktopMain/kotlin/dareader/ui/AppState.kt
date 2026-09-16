package dareader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dareader.ext.di.DareaderGraph
import dareader.ext.load.LoadedExtension
import dareader.ext.load.dexToJar
import dareader.ext.load.loadExtensionSources
import dareader.ext.manager.ExtensionManager
import dareader.ext.pkg.downloadApk
import dareader.ext.pkg.parseApkManifest
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
/** Pending untrusted-install decision surfaced to the UI; resolved via [AppState.answerTrust]. */
data class TrustPrompt(
    val pkg: String,
    val versionCode: Long,
    val certHashes: List<String>,
    val storeKey: String,
)

sealed interface Screen {
    data object Setup : Screen
    data object Library : Screen
    data object History : Screen
    data object Store : Screen
    data object More : Screen
    data class Browse(val sources: List<Source>) : Screen
    data class Detail(val source: Source, val sources: List<Source>, val manga: SManga) : Screen
    data class Reader(val source: Source, val sources: List<Source>, val manga: SManga, val chapter: SChapter) : Screen
    data class Settings(val source: Source, val sources: List<Source>) : Screen
}

/** XDG-aware data dir, overridable via `-Ddareader.data.dir=…`. */
fun defaultDataDir(): Path {
    System.getProperty("dareader.data.dir")?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
    val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
    val base = if (xdg != null) Path.of(xdg) else Path.of(System.getProperty("user.home"), ".local", "share")
    return base.resolve("dareader")
}

class AppState(val dataDir: Path = defaultDataDir()) {
    var screen: Screen by mutableStateOf(Screen.Setup)
    var status: String by mutableStateOf("pick an extension APK to load")

    /** Last user-visible failure; cleared via [dismissError]. Never throws to composition. */
    var error: String? by mutableStateOf(null)
        private set

    fun dismissError() {
        error = null
    }

    /** UI-thread-safe failure sink for screens that cannot handle the error locally. */
    fun reportError(message: String) {
        status = message
        error = message
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = defaultHttpClient()
    private val loadLock = Any()
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
    val extensions = ExtensionManager(dataDir, client) { pkg, versionCode, certHashes, storeKey ->
        val future = CompletableFuture<Boolean>()
        synchronized(trustLock) {
            pendingTrust = future
            trustRequest = TrustPrompt(pkg, versionCode, certHashes, storeKey)
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
        if (old != null) runCatching { old.close() }
        if (oldJar != null) runCatching { Files.deleteIfExists(oldJar) }
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
        if (old != null) runCatching { old.close() }
        if (oldJar != null) runCatching { Files.deleteIfExists(oldJar) }
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
                    val raw = requireNotNull(manifest.entryClass) { "no source class or factory" }
                    val fqcn = if (raw.startsWith(".")) manifest.packageName + raw else raw
                    val jar = dexToJar(apk)
                    var retained = false
                    try {
                        val ext = loadExtensionSources(jar, fqcn, manifest.packageName)
                        setLoaded(ext, jar)
                        retained = true
                        screen = Screen.Browse(ext.sources)
                    } finally {
                        if (!retained) Files.deleteIfExists(jar)
                    }
                } finally {
                    Files.deleteIfExists(apk)
                }
            }.onFailure { report("error", it) }
        }
    }

    /** Kiosk path for headless verification: setup → browse → detail → reader. */
    fun autoDemo(apkUrl: String) {
        status = "demo: loading…"
        scope.launch {
            runCatching {
                val apk = downloadApk(client, apkUrl)
                try {
                    val manifest = parseApkManifest(apk)
                    val raw = requireNotNull(manifest.entryClass) { "no source class or factory" }
                    val fqcn = if (raw.startsWith(".")) manifest.packageName + raw else raw
                    val jar = dexToJar(apk)
                    var retained = false
                    try {
                        val ext = loadExtensionSources(jar, fqcn, manifest.packageName)
                        setLoaded(ext, jar)
                        retained = true
                        val source = ext.sources.firstOrNull()
                        if (source == null) {
                            screen = Screen.Browse(ext.sources)
                            status = "demo: extension has no sources"
                            return@runCatching
                        }
                        val titles = popularTitles(source)
                        val manga = titles.mangas.firstOrNull()
                        if (manga == null) {
                            screen = Screen.Browse(ext.sources)
                            status = "demo: no titles"
                            return@runCatching
                        }
                        val detail = mangaUpdate(source, manga)
                        val chapter = detail.chapters.firstOrNull()
                        if (chapter == null) {
                            screen = Screen.Detail(source, ext.sources, detail.manga)
                            status = "demo: ${manga.displayTitle()} has no chapters"
                            return@runCatching
                        }
                        chapterPages(source, chapter)
                        screen = Screen.Reader(source, ext.sources, detail.manga, chapter)
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

    /**
     * Persisted install via the manager. When the trust gate fires, [trustRequest]
     * is set and this suspends until [answerTrust]; a rejection lands in error/status.
     */
    fun installExtension(apkUrl: String, storeKey: String? = null) {
        status = "installing…"
        scope.launch {
            runCatching { extensions.installFromApkUrl(apkUrl, storeKey) }
                .onSuccess {
                    status = "installed ${it.pkg} (${it.sources.size} sources)"
                    screen = Screen.Browse(it.sources)
                }
                .onFailure { report("install error", it) }
        }
    }

    fun uninstallExtension(pkg: String) {
        scope.launch {
            runCatching { extensions.uninstall(pkg) }
                .onSuccess { status = "uninstalled $pkg" }
                .onFailure { report("uninstall error", it) }
        }
    }

    /** Navigate to the browse screen for a manager-installed or single-shot-loaded source. */
    fun openSource(id: Long) {
        scope.launch {
            val source = runCatching {
                extensions.findSource(id)
                    ?: synchronized(loadLock) { loaded }?.sources?.firstOrNull { it.id == id }
            }.getOrNull()
            if (source == null) {
                report("open source error", NoSuchElementException("no source with id $id"))
                return@launch
            }
            screen = Screen.Browse(listOf(source))
            status = "browsing ${source.name}"
        }
    }

    /** Open detail and record it in history; never throws. */
    fun openDetail(source: Source, sources: List<Source>, manga: SManga) {
        runCatching { library.recordHistory(source.id, manga) }
        screen = Screen.Detail(source, sources, manga)
    }

    /** Reader progress hook; delegates to the library store, never throws. */
    fun saveProgress(mangaUrl: String, chapterUrl: String, page: Int) {
        runCatching { library.saveProgress(mangaUrl, chapterUrl, page) }
    }

    private suspend fun <T> safeSourceCall(op: String, block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            report(op, e)
            null
        }
    }

    /** Composition-safe source wrappers: failures land in error/status, never escape. */
    suspend fun popular(source: Source, page: Int = 1): MangasPage? =
        safeSourceCall("browse error") { popularTitles(source, page) }

    suspend fun latest(source: Source, page: Int): MangasPage? =
        safeSourceCall("browse error") { latestTitles(source, page) }

    suspend fun search(source: Source, page: Int, query: String, filters: FilterList): MangasPage? =
        safeSourceCall("browse error") { searchTitles(source, page, query, filters) }

    suspend fun detail(source: Source, manga: SManga): SMangaUpdate? =
        safeSourceCall("detail error") { mangaUpdate(source, manga) }

    suspend fun pages(source: Source, chapter: SChapter): List<Page>? =
        safeSourceCall("reader error") { chapterPages(source, chapter) }

    /** Composition-safe store index fetch: failures land in error/status, never escape. */
    suspend fun fetchStoreIndex(indexUrl: String, packageFilter: String? = null): List<NetworkExtensionStore.Extension>? =
        safeSourceCall("store error") {
            withContext(Dispatchers.IO) {
                val store = fetchStore(client, indexUrl)
                val list = store.extensionList
                    ?: store.extensionListUrl?.let { fetchSplitExtensionList(client, it) }
                val extensions = list?.extensions ?: emptyList()
                if (packageFilter == null) extensions else extensions.filter { it.packageName.contains(packageFilter) }
            }
        }

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

package dareader.ext.manager

import dareader.ext.load.LoadedExtension
import dareader.ext.load.dexToJar
import dareader.ext.load.loadExtensionSources
import dareader.ext.pkg.downloadApk
import dareader.ext.pkg.parseApkManifest
import dareader.ext.store.downloadJar
import dareader.ext.trust.apkCertificateHashes
import dareader.ext.trust.isTrusted
import dareader.ext.trust.pin
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class Installed(
    val pkg: String,
    val mainClass: String,
    val versionName: String,
    val versionCode: Long,
    val sources: List<Source>,
    val iconUrl: String? = null,
)

@Serializable
private data class StagedMeta(
    val pkg: String,
    val mainClass: String,
    val versionName: String,
    val versionCode: Long,
    val iconUrl: String? = null,
)

private val metaJson = Json { ignoreUnknownKeys = true; prettyPrint = false }

/**
 * Staged extension installs under `dataDir/extensions/<pkg>/`.
 *
 * The staged `extension.jar` is memory-mapped by its classloader for the
 * loader's whole lifetime, so it is deleted only after [LoadedExtension.close]
 * — never while the loader is open. APKs are temp files discarded right after
 * dex2jar; direct-jar downloads are moved into the staged dir and retained.
 * Staged dirs survive restarts and are reloaded (best-effort) on init.
 */
class ExtensionManager(
    private val dataDir: Path,
    private val client: OkHttpClient,
    private val onTrustRequest: (pkg: String, versionCode: Long, certHashes: List<String>, storeKey: String) -> Boolean,
) {
    private val extRoot: Path = dataDir.resolve("extensions")
    private val lock = Any()
    private val loaded = mutableMapOf<String, LoadedExtension>()
    private val metas = mutableMapOf<String, StagedMeta>()
    private val _installed = MutableStateFlow<List<Installed>>(emptyList())
    val installed: StateFlow<List<Installed>> = _installed.asStateFlow()

    init {
        runCatching { Files.createDirectories(extRoot) }
        rescan()
    }

    /** Reloads every staged package; per-pkg failures are skipped, never thrown. */
    private fun rescan() {
        val dirs = runCatching {
            Files.list(extRoot).use { stream -> stream.filter { Files.isDirectory(it) }.toList() }
        }.getOrDefault(emptyList())
        for (dir in dirs) {
            try {
                val metaFile = dir.resolve("meta.json")
                val jar = dir.resolve("extension.jar")
                if (!Files.isRegularFile(metaFile) || !Files.isRegularFile(jar)) continue
                val meta = metaJson.decodeFromString(
                    StagedMeta.serializer(),
                    Files.readString(metaFile),
                )
                val handle = loadExtensionSources(jar, meta.mainClass, meta.pkg)
                synchronized(lock) {
                    loaded[meta.pkg] = handle
                    metas[meta.pkg] = meta
                }
            } catch (e: Exception) {
                System.err.println("dareader: skipping extension ${dir.fileName}: ${e.message}")
            }
        }
        refreshInstalled()
    }

    suspend fun installFromApkUrl(
        apkUrl: String,
        storeKey: String? = null,
        iconUrl: String? = null,
    ): Installed = withContext(Dispatchers.IO) {
        val apk = downloadApk(client, apkUrl)
        try {
            val manifest = parseApkManifest(apk)
            val verdict = manifest.judge()
            check(verdict.startsWith("accepted")) { verdict }
            val raw = requireNotNull(manifest.entryClass) {
                "rejected: missing source class or factory"
            }
            val fqcn = if (raw.startsWith(".")) manifest.packageName + raw else raw
            val certHashes = runCatching { apkCertificateHashes(apk) }.getOrDefault(emptyList())
            trustGate(manifest.packageName, manifest.versionCode, certHashes, storeKey)
            val jar = dexToJar(apk)
            try {
                stageAndLoad(
                    StagedMeta(
                        pkg = manifest.packageName,
                        mainClass = fqcn,
                        versionName = manifest.versionName,
                        versionCode = manifest.versionCode,
                        iconUrl = iconUrl,
                    ),
                    jar,
                )
            } catch (e: Exception) {
                Files.deleteIfExists(jar)
                throw e
            }
        } finally {
            Files.deleteIfExists(apk)
        }
    }

    suspend fun installFromJarUrl(
        jarUrl: String,
        pkg: String,
        mainClass: String,
        versionName: String,
        versionCode: Long,
        storeKey: String? = null,
        iconUrl: String? = null,
    ): Installed = withContext(Dispatchers.IO) {
        val tmp = downloadJar(client, jarUrl)
        try {
            // Jars carry no signing certs, so the gate falls through to the
            // pinned set or the caller's trust prompt — same gate, no certs.
            trustGate(pkg, versionCode, emptyList(), storeKey)
            stageAndLoad(StagedMeta(pkg, mainClass, versionName, versionCode, iconUrl), tmp)
        } catch (e: Exception) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    /**
     * Closes the loader (which unregisters its sources), then deletes staged
     * files. Unknown packages are a no-op. Never throws.
     */
    fun uninstall(pkg: String) {
        val handle = synchronized(lock) {
            metas.remove(pkg)
            loaded.remove(pkg)
        }
        if (handle != null) runCatching { handle.close() }
        runCatching {
            val dir = stagedDir(pkg)
            if (dir != null) deleteRecursively(dir)
        }
        refreshInstalled()
    }

    fun findSource(id: Long): Source? = GetSource.findById(id)

    private fun trustGate(
        pkg: String,
        versionCode: Long,
        certHashes: List<String>,
        storeKey: String?,
    ) {
        val key = storeKey ?: ""
        if (isTrusted(pkg, versionCode, certHashes, key)) return
        if (onTrustRequest(pkg, versionCode, certHashes, key)) {
            pin(pkg, versionCode, certHashes)
            return
        }
        throw SecurityException("untrusted extension $pkg v$versionCode")
    }

    /**
     * Moves [tmpJar] into the staged dir, writes meta, then loads. The staged
     * jar MUST outlive the loader, so on load failure the staged dir is
     * removed (no open loader references it) and the error rethrown.
     */
    private fun stageAndLoad(meta: StagedMeta, tmpJar: Path): Installed {
        // Close + delete any previous install first so reinstalls replace cleanly.
        uninstall(meta.pkg)
        val dir = extRoot.resolve(safePkgDir(meta.pkg))
        Files.createDirectories(dir)
        val stagedJar = dir.resolve("extension.jar")
        var staged = false
        try {
            Files.move(tmpJar, stagedJar, StandardCopyOption.REPLACE_EXISTING)
            staged = true
            writeMeta(dir, meta)
            val handle = loadExtensionSources(stagedJar, meta.mainClass, meta.pkg)
            synchronized(lock) {
                loaded[meta.pkg] = handle
                metas[meta.pkg] = meta
            }
            refreshInstalled()
            return Installed(
                pkg = meta.pkg,
                mainClass = meta.mainClass,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                sources = handle.sources,
                iconUrl = meta.iconUrl,
            )
        } catch (e: Exception) {
            synchronized(lock) {
                loaded.remove(meta.pkg)?.let { runCatching { it.close() } }
                metas.remove(meta.pkg)
            }
            if (staged) runCatching { deleteRecursively(dir) }
            refreshInstalled()
            throw e
        }
    }

    private fun refreshInstalled() {
        val snapshot = synchronized(lock) {
            metas.values.mapNotNull { meta ->
                val handle = loaded[meta.pkg] ?: return@mapNotNull null
                Installed(
                    pkg = meta.pkg,
                    mainClass = meta.mainClass,
                    versionName = meta.versionName,
                    versionCode = meta.versionCode,
                    sources = handle.sources,
                    iconUrl = meta.iconUrl,
                )
            }
        }
        _installed.value = snapshot
    }

    /** Null when [pkg] is not a safe single path segment. */
    private fun stagedDir(pkg: String): Path? {
        val seg = pkgDirSegment(pkg) ?: return null
        val dir = extRoot.resolve(seg)
        if (!dir.startsWith(extRoot)) return null
        return dir
    }

    private fun safePkgDir(pkg: String): String =
        requireNotNull(pkgDirSegment(pkg)) { "unsafe package name: $pkg" }

    private fun pkgDirSegment(pkg: String): String? {
        if (pkg.isBlank() || pkg == "." || pkg == "..") return null
        if (pkg.contains('/') || pkg.contains('\\')) return null
        return pkg
    }

    private fun writeMeta(dir: Path, meta: StagedMeta) {
        val file = dir.resolve("meta.json")
        val tmp = Files.createTempFile(dir, "meta-", ".json")
        try {
            Files.writeString(tmp, metaJson.encodeToString(StagedMeta.serializer(), meta))
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}

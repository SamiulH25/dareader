package dareader.ext.manager

import dareader.ext.ExtensionContract
import dareader.ext.load.LoadedExtension
import dareader.ext.load.dexToJar
import dareader.ext.load.findExtensionEntryClass
import dareader.ext.load.loadExtensionSources
import dareader.ext.pkg.downloadApk
import dareader.ext.pkg.parseApkManifest
import dareader.ext.pkg.requireAccepted
import dareader.ext.store.downloadJar
import dareader.ext.trust.apkCertificateHashes
import dareader.ext.trust.isTrusted
import dareader.ext.trust.isTrustedArtifact
import dareader.ext.trust.pin
import dareader.ext.trust.pinArtifact
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.Hash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

data class Installed(
    val pkg: String,
    val mainClass: String,
    val versionName: String,
    val versionCode: Long,
    val sources: List<Source>,
    val iconUrl: String? = null,
)

/**
 * Pending trust decision handed to [ExtensionManager]'s prompt callback. APK
 * installs carry [certHashes]; jar installs (no signing certificate) carry
 * [artifactSha256] instead.
 */
data class TrustRequest(
    val pkg: String,
    val versionCode: Long,
    val certHashes: List<String> = emptyList(),
    val artifactSha256: String? = null,
    val storeKey: String = "",
)

@Serializable
internal data class StagedMeta(
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
    private val onTrustRequest: (TrustRequest) -> Boolean,
) {
    private val extRoot: Path = dataDir.resolve("extensions")
    private val lock = Any()
    private val pkgLocks = ConcurrentHashMap<String, Mutex>()

    /** Serializes staged mutations (install vs uninstall) for one package. */
    private fun pkgLock(pkg: String): Mutex = pkgLocks.computeIfAbsent(pkg) { Mutex() }
    private val loaded = mutableMapOf<String, LoadedExtension>()
    private val metas = mutableMapOf<String, StagedMeta>()
    private val _installed = MutableStateFlow<List<Installed>>(emptyList())
    val installed: StateFlow<List<Installed>> = _installed.asStateFlow()

    init {
        runCatching { Files.createDirectories(extRoot) }
    }

    /** Reloads staged packages that are not already loaded; per-pkg failures are skipped, never thrown. */
    suspend fun rescan() = withContext(Dispatchers.IO) {
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
                if (synchronized(lock) { loaded.containsKey(meta.pkg) }) continue
                val handle = loadExtensionSources(jar, meta.mainClass, meta.pkg)
                synchronized(lock) {
                    loaded[meta.pkg] = handle
                    metas[meta.pkg] = meta
                }
            } catch (e: Throwable) {
                // LinkageError from a broken jar must skip this package, not abort startup.
                System.err.println(
                    "dareader: skipping extension ${dir.fileName}: ${e.message ?: e.javaClass.simpleName}",
                )
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
            manifest.requireAccepted()
            val raw = requireNotNull(manifest.entryClass) {
                "rejected: missing source class or factory"
            }
            val fqcn = if (raw.startsWith(".")) manifest.packageName + raw else raw
            // Fail closed: a verifier failure must not downgrade to a trust prompt.
            val certHashes = apkCertificateHashes(apk)
            trustGate(
                TrustRequest(
                    pkg = manifest.packageName,
                    versionCode = manifest.versionCode,
                    certHashes = certHashes,
                    storeKey = storeKey ?: "",
                ),
            )
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

    /**
     * Installs a store-provided jar (no APK, no dex2jar). Jars carry no
     * signing certificate, so the trust gate pins the artifact SHA-256: the
     * same bytes reinstall silently, any other bytes re-prompt. Library
     * metadata from the store index is judged like an APK manifest would be.
     */
    suspend fun installFromJarUrl(
        jarUrl: String,
        pkg: String,
        versionName: String,
        versionCode: Long,
        extensionLib: String? = null,
        storeKey: String? = null,
        iconUrl: String? = null,
    ): Installed = withContext(Dispatchers.IO) {
        val lib =
            extensionLib?.takeUnless { it == "0" }?.toDoubleOrNull()
                ?: ExtensionContract.libVersionFromVersionName(versionName)
        if (lib == null || !ExtensionContract.isSupportedLibVersion(lib)) {
            error(
                "rejected: lib $lib not in ${ExtensionContract.LIB_VERSION_MIN}..${ExtensionContract.LIB_VERSION_MAX}",
            )
        }
        val tmp = downloadJar(client, jarUrl)
        try {
            trustGate(
                TrustRequest(
                    pkg = pkg,
                    versionCode = versionCode,
                    artifactSha256 = Hash.sha256(Files.readAllBytes(tmp)),
                    storeKey = storeKey ?: "",
                ),
            )
            val mainClass =
                findExtensionEntryClass(tmp)
                    ?: error("rejected: no loadable Source or SourceFactory in '$pkg' jar")
            stageAndLoad(StagedMeta(pkg, mainClass, versionName, versionCode, iconUrl), tmp)
        } catch (e: Exception) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    /** Uninstalls [pkg]: closes its loader, deletes staged files. Never throws. */
    suspend fun uninstall(pkg: String) = pkgLock(pkg).withLock { removeStaged(pkg) }

    /** Closes every loaded extension (unregistering its sources). Idempotent. */
    fun close() {
        val handles = synchronized(lock) {
            val all = loaded.values.toList()
            loaded.clear()
            metas.clear()
            all
        }
        handles.forEach { runCatching { it.close() } }
        refreshInstalled()
    }

    /**
     * Removes staged state for [pkg]; callers hold [pkgLock], so it cannot
     * race a load of the same package. Never throws.
     */
    private fun removeStaged(pkg: String) {
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

    private fun trustGate(request: TrustRequest) {
        val trusted =
            if (request.artifactSha256 != null) {
                isTrustedArtifact(request.pkg, request.versionCode, request.artifactSha256)
            } else {
                isTrusted(request.pkg, request.versionCode, request.certHashes, request.storeKey)
            }
        if (trusted) return
        if (onTrustRequest(request)) {
            if (request.artifactSha256 != null) {
                pinArtifact(request.pkg, request.versionCode, request.artifactSha256)
            } else {
                pin(request.pkg, request.versionCode, request.certHashes)
            }
            return
        }
        throw SecurityException("untrusted extension ${request.pkg} v${request.versionCode}")
    }

    /**
     * Moves [tmpJar] into the staged dir, writes meta, then loads. The staged
     * jar MUST outlive the loader, so on load failure the staged dir is
     * removed (no open loader references it) and the error rethrown.
     */
    internal suspend fun stageAndLoad(meta: StagedMeta, tmpJar: Path): Installed =
        pkgLock(meta.pkg).withLock { stageAndLoadLocked(meta, tmpJar) }

    /** Caller holds [pkgLock]. */
    private fun stageAndLoadLocked(meta: StagedMeta, tmpJar: Path): Installed {
        // Close + delete any previous install first so reinstalls replace cleanly.
        removeStaged(meta.pkg)
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

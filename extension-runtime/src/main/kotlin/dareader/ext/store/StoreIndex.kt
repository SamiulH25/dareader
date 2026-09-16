@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package dareader.ext.store


import eu.kanade.tachiyomi.util.lang.Hash
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.GzipSource
import okio.buffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Keiyoushi extension store index (index.pb / index.json), mirroring
 * Suwayomi's NetworkExtensionStore field numbers so both encodings decode.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NetworkExtensionStore(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: Contact = Contact(""),
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String,
        @ProtoNumber(2) val discord: String? = null,
    )

    @Serializable
    data class ExtensionList(
        @ProtoNumber(1) val extensions: List<Extension>,
    )

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String = "",

        @ProtoNumber(5) val versionCode: Long = 0,
        @ProtoNumber(6) val versionName: String = "",
        @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String,
        @ProtoNumber(2) val iconUrl: String = "",
        @ProtoNumber(501) val jarUrl: String? = null,
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(7) val message: String? = null,
    )

    @Serializable
    enum class ContentWarning {
        @ProtoNumber(0)
        @JsonNames("CONTENT_WARNING_UNSPECIFIED")
        UNSPECIFIED,

        @ProtoNumber(1)
        @JsonNames("CONTENT_WARNING_SAFE")
        SAFE,

        @ProtoNumber(2)
        @JsonNames("CONTENT_WARNING_MIXED")
        MIXED,

        @ProtoNumber(3)
        @JsonNames("CONTENT_WARNING_NSFW")
        NSFW,
    }
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Fetches and decodes a store index; sniffs JSON (`{`) vs protobuf, ungzips
 * when needed. A disk copy plus ETag revalidation keeps repeat opens cheap:
 * a 304 reuses the cached body, and the cache also survives a failed refetch.
 */
fun fetchStore(client: OkHttpClient, indexUrl: String): NetworkExtensionStore {
    val cacheFile = storeCacheFile(indexUrl)
    val etag = readCachedEtag(cacheFile)
    val request =
        Request.Builder().url(indexUrl)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
    client.newCall(request).execute().use { response ->
        if (response.code == 304) {
            val cached = readCachedBody(cacheFile)
            if (cached != null) return decodeStore(cached)
        }
        check(response.isSuccessful) { "store fetch failed: HTTP ${response.code}" }
        val bytes = response.body.bytes().ungzipIfNeeded()
        writeCachedIndex(cacheFile, bytes, response.header("ETag"))
        return decodeStore(bytes)
    }
}

internal fun decodeStore(bytes: ByteArray): NetworkExtensionStore =
    if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
        json.decodeFromString(NetworkExtensionStore.serializer(), bytes.decodeToString())
    } else {
        ProtoBuf.decodeFromByteArray(NetworkExtensionStore.serializer(), bytes)
    }

/** Index cache root; overridable via `dareader.store.cache.dir` (tests/embedders). */
internal fun storeCacheDir(): Path {
    System.getProperty("dareader.store.cache.dir")?.takeIf { it.isNotBlank() }?.let {
        return Paths.get(it)
    }
    val base = System.getenv("XDG_CACHE_HOME")?.ifBlank { null }
        ?: (System.getProperty("user.home") + "/.cache")
    return Paths.get(base, "dareader", "store")
}

private fun storeCacheFile(indexUrl: String): Path = storeCacheDir().resolve(Hash.sha256(indexUrl))

private fun etagFile(cacheFile: Path): Path =
    cacheFile.resolveSibling(cacheFile.fileName.toString() + ".etag")

private fun readCachedEtag(cacheFile: Path): String? =
    runCatching { Files.readString(etagFile(cacheFile)).trim().ifBlank { null } }.getOrNull()

private fun readCachedBody(cacheFile: Path): ByteArray? =
    runCatching { Files.readAllBytes(cacheFile).takeIf { it.isNotEmpty() } }.getOrNull()

private fun writeCachedIndex(
    cacheFile: Path,
    bytes: ByteArray,
    etag: String?,
) {
    runCatching {
        Files.createDirectories(cacheFile.parent)
        val tmp = Files.createTempFile(cacheFile.parent, "index", ".part")
        try {
            Files.write(tmp, bytes)
            Files.move(tmp, cacheFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
        if (etag != null) Files.writeString(etagFile(cacheFile), etag)
    }
}

/** Follows [NetworkExtensionStore.extensionListUrl] when the index splits the list out. */
fun fetchSplitExtensionList(client: OkHttpClient, listUrl: String): NetworkExtensionStore.ExtensionList {
    val request = Request.Builder().url(listUrl).build()
    client.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "extension list fetch failed: HTTP ${response.code}" }
        val bytes = response.body.bytes().ungzipIfNeeded()
        return if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
            json.decodeFromString(NetworkExtensionStore.ExtensionList.serializer(), bytes.decodeToString())
        } else {
            ProtoBuf.decodeFromByteArray(NetworkExtensionStore.ExtensionList.serializer(), bytes)
        }
    }
}

private fun ByteArray.ungzipIfNeeded(): ByteArray {
    if (size < 2 || this[0] != 0x1f.toByte() || this[1] != 0x8b.toByte()) return this
    return GzipSource(Buffer().write(this)).buffer().readByteArray()
}

private val sharedHttpClient: OkHttpClient by lazy { OkHttpClient() }

/**
 * Shared HTTP client for store/APK fetches; owned here so callers need no
 * OkHttp dep. One instance per process: reusing it keeps the connection pool
 * and dispatcher threads instead of rebuilding them per call site.
 */
fun defaultHttpClient(): OkHttpClient = sharedHttpClient

/**
 * One entry of a `repo.json` store list. Kept lenient: unknown fields are
 * ignored so both `{name, url}` objects and bare URL strings decode.
 */
@Serializable
data class RepoEntry(val name: String = "", val url: String = "")

/**
 * Fetches a `repo.json` store list: a JSON array of `{name, url}` objects
 * (or bare URL strings, or `{"repos": [...]}`). Each entry's `url` points at
 * a store index consumable by [fetchStore].
 */
fun fetchRepos(client: OkHttpClient, repoJsonUrl: String): List<RepoEntry> {
    val request = Request.Builder().url(repoJsonUrl).build()
    client.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "repo list fetch failed: HTTP ${response.code}" }
        val text = response.body.string()
        return parseRepos(text)
    }
}

internal fun parseRepos(text: String): List<RepoEntry> {
    runCatching {
        return json.decodeFromString<List<RepoEntry>>(text)
    }
    runCatching {
        return json.decodeFromString<List<String>>(text).map { RepoEntry(url = it) }
    }
    return json.decodeFromString(RepoWrapper.serializer(), text).repos
}

@Serializable
private data class RepoWrapper(val repos: List<RepoEntry> = emptyList())

/** Direct-jar URL for an extension when the store provides one, else null. */
fun NetworkExtensionStore.Extension.directJarUrl(): String? =
    resources.jarUrl?.takeUnless { it.isBlank() }

/**
 * Downloads a store-provided jar to a temp file. The jar is memory-mapped by
 * the classloader, so the caller must retain it and delete it only after
 * [dareader.ext.load.LoadedExtension.close]. Prefer this over the APK+dex2jar
 * path whenever [directJarUrl] is present.
 */
fun downloadJar(client: OkHttpClient, url: String): java.nio.file.Path {
    val tmp = java.nio.file.Files.createTempFile("dareader-ext-", ".jar")
    val request = Request.Builder().url(url).build()
    try {
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "jar download failed: HTTP ${response.code}" }
            response.body.byteStream().use { input ->
                java.nio.file.Files.newOutputStream(tmp).use { output -> input.copyTo(output) }
            }
        }
        return tmp
    } catch (e: Exception) {
        java.nio.file.Files.deleteIfExists(tmp)
        throw e
    }
}

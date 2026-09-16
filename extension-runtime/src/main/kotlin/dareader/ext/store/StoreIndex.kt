@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package dareader.ext.store


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

/** Fetches and decodes a store index; sniffs JSON (`{`) vs protobuf, ungzips when needed. */
fun fetchStore(client: OkHttpClient, indexUrl: String): NetworkExtensionStore {
    val request = Request.Builder().url(indexUrl).build()
    client.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "store fetch failed: HTTP ${response.code}" }
        val bytes = response.body.bytes().ungzipIfNeeded()
        return if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
            json.decodeFromString(NetworkExtensionStore.serializer(), bytes.decodeToString())
        } else {
            ProtoBuf.decodeFromByteArray(NetworkExtensionStore.serializer(), bytes)
        }
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

/** Shared HTTP client for store/APK fetches; owned here so callers need no OkHttp dep. */
fun defaultHttpClient(): OkHttpClient = OkHttpClient()

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

package dareader.ext.pkg

import dareader.ext.ExtensionContract
import net.dongliu.apk.parser.ApkFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

/** Manifest metadata judged by the same rules as ExtensionLoader on Android. */
data class ExtensionManifest(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val label: String?,
    val isExtension: Boolean,
    val sourceClass: String?,
    val sourceFactory: String?,
    val extensionName: String?,
    val extensionLib: String?,
    val contentWarning: Int,
    val signed: Boolean,
) {
    val libVersion: Double? =
        extensionLib?.takeUnless { it == "0" }?.toDoubleOrNull()
            ?: ExtensionContract.libVersionFromVersionName(versionName)

    /** Resolved loader entry: single-source class first, multi-source factory otherwise. */
    val entryClass: String?
        get() = sourceClass?.ifBlank { null } ?: sourceFactory?.ifBlank { null }

    val entryIsFactory: Boolean
        get() = sourceClass.isNullOrBlank() && !sourceFactory.isNullOrBlank()

    fun judge(): String = when {
        !signed -> "rejected: unsigned"
        !isExtension -> "rejected: missing ${ExtensionContract.EXTENSION_FEATURE} feature"
        versionName.isEmpty() -> "rejected: missing versionName"
        entryClass.isNullOrBlank() ->
            "rejected: missing ${ExtensionContract.METADATA_SOURCE_CLASS} or ${ExtensionContract.METADATA_SOURCE_FACTORY}"
        libVersion == null || !ExtensionContract.isSupportedLibVersion(libVersion) ->
            "rejected: lib $libVersion not in ${ExtensionContract.LIB_VERSION_MIN}..${ExtensionContract.LIB_VERSION_MAX}"
        else -> "accepted: lib=$libVersion sources=[$entryClass]"
    }
}

/** Downloads an APK to a temp file. Caller deletes when done. */
fun downloadApk(client: OkHttpClient, url: String): Path {
    val tmp = Files.createTempFile("dareader-ext-", ".apk")
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "apk download failed: HTTP ${response.code}" }
        response.body.byteStream().use { input ->
            Files.newOutputStream(tmp).use { output -> input.copyTo(output) }
        }
    }
    return tmp
}

fun parseApkManifest(apk: Path): ExtensionManifest {
    ApkFile(apk.toFile()).use { parser ->
        val meta = parser.apkMeta
        val values = manifestMetaData(parser.manifestXml)
        fun meta(key: String): String? = values[key]

        val signed = verifyApkSignature(apk)

        return ExtensionManifest(
            packageName = meta.packageName,
            versionName = meta.versionName.orEmpty(),
            versionCode = meta.versionCode ?: 0L,
            label = meta.label,
            isExtension = meta.usesFeatures.orEmpty().any { it.name == ExtensionContract.EXTENSION_FEATURE },
            sourceFactory = meta(ExtensionContract.METADATA_SOURCE_FACTORY),
            sourceClass = meta(ExtensionContract.METADATA_SOURCE_CLASS),
            extensionName = meta(ExtensionContract.METADATA_NAME),
            extensionLib = meta(ExtensionContract.METADATA_EXTENSION_LIB),
            contentWarning = meta(ExtensionContract.METADATA_CONTENT_WARNING)?.toIntOrNull() ?: 0,
            signed = signed,
        )
    }
}


/** True when v1/v2/v3 verification passes; mirrors PackageTools signature gating. */
fun verifyApkSignature(apk: Path): Boolean =
    runCatching {
        com.android.apksig.ApkVerifier.Builder(apk.toFile()).build().verify().isVerified
    }.getOrDefault(false)
/** Decodes `<meta-data android:name android:value>` pairs from the manifest XML. */
private fun manifestMetaData(manifestXml: String): Map<String, String> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(ByteArrayInputStream(manifestXml.toByteArray()))
    val out = mutableMapOf<String, String>()
    val nodes = doc.getElementsByTagName("meta-data")
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as? Element ?: continue
        val name = el.getAttribute("android:name")
        if (name.isEmpty()) continue
        val value = el.getAttribute("android:value")
            .ifEmpty { el.getAttribute("android:resource") }
        out[name] = value
    }
    return out
}

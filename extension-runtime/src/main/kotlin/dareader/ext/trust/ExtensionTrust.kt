package dareader.ext.trust

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * Trust gating for extensions, mirroring Mihon's model: an APK is trusted
 * when its signing certificate matches a store's pinned signingKey, or when
 * the user explicitly trusted that exact package+version+cert before (TOFU).
 *
 * TOFU pins are file-backed under [trustRoot] (default
 * `~/.cache/dareader/trust/pins.json`) so they persist across processes.
 * Tests can point [trustRoot] elsewhere via the `dareader.trust.dir` system
 * property or the `DAREADER_TRUST_DIR` environment variable.
 */
fun apkCertificateHashes(apk: Path): List<String> {
    val result = com.android.apksig.ApkVerifier.Builder(apk.toFile()).build().verify()
    check(result.isVerified) { "APK signature does not verify" }
    val digest = MessageDigest.getInstance("SHA-256")
    return result.signerCertificates.map { cert ->
        digest.digest(cert.encoded).toHex()
    }
}

fun matchesStoreKey(certHashes: List<String>, storeSigningKey: String): Boolean {
    if (storeSigningKey.isBlank()) return false
    val normalized = storeSigningKey.filter { it.isLetterOrDigit() }.lowercase()
    if (normalized.isEmpty()) return false
    return certHashes.any { it.filter { c -> c.isLetterOrDigit() }.lowercase() == normalized }
}

/**
 * Trust-on-first-use check: true when the cert matches the store's signing
 * key, or when this exact package+version+cert set was pinned before via
 * [pin].
 */
fun isTrusted(pkg: String, versionCode: Long, certHashes: List<String>, storeKey: String): Boolean {
    if (matchesStoreKey(certHashes, storeKey)) return true
    return loadPins().any { pin ->
        pin.pkg == pkg &&
            pin.versionCode == versionCode &&
            pin.certHashes.normalizedSet() == certHashes.normalizedSet()
    }
}

/** Pins this exact package+version+cert set as user-trusted (TOFU). */
fun pin(pkg: String, versionCode: Long, certHashes: List<String>) {
    val pins = loadPins().filterNot { it.pkg == pkg && it.versionCode == versionCode }.toMutableList()
    pins += TrustPin(pkg, versionCode, certHashes.map { it.lowercase() }.sorted())
    savePins(pins)
}

@Serializable
internal data class TrustPin(
    val pkg: String,
    val versionCode: Long,
    val certHashes: List<String>,
)

@Serializable
private data class TrustStore(val pins: List<TrustPin> = emptyList())

private val trustJson = Json { ignoreUnknownKeys = true; prettyPrint = false }

internal fun trustRoot(): Path {
    val override = System.getProperty("dareader.trust.dir")
        ?: System.getenv("DAREADER_TRUST_DIR")
    if (!override.isNullOrBlank()) return Paths.get(override)
    val cache = System.getenv("XDG_CACHE_HOME")?.ifBlank { null }
        ?: (System.getProperty("user.home") + "/.cache")
    return Paths.get(cache, "dareader", "trust")
}

private fun pinsFile(): Path = trustRoot().resolve("pins.json")

internal fun loadPins(): List<TrustPin> {
    val file = pinsFile()
    if (!Files.isRegularFile(file)) return emptyList()
    return runCatching {
        trustJson.decodeFromString(TrustStore.serializer(), Files.readString(file)).pins
    }.getOrDefault(emptyList())
}

private fun savePins(pins: List<TrustPin>) {
    val file = pinsFile()
    Files.createDirectories(file.parent)
    val tmp = Files.createTempFile(file.parent, "pins-", ".json")
    try {
        Files.writeString(tmp, trustJson.encodeToString(TrustStore.serializer(), TrustStore(pins)))
        Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        Files.deleteIfExists(tmp)
        throw e
    }
}

private fun List<String>.normalizedSet(): Set<String> = map { it.lowercase() }.toSet()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

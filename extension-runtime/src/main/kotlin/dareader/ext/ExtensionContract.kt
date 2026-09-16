package dareader.ext

/**
 * Contract constants shared with Android extensions (tachiyomix) and the
 * Keiyoushi store index. Mirrors PackageTools + ExtensionLoader metadata
 * keys so manifests parsed on desktop judge extensions identically.
 */
object ExtensionContract {
    const val EXTENSION_FEATURE = "tachiyomi.extension"
    const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    const val METADATA_NAME = "tachiyomix.name"
    const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

    /** Mirrors Suwayomi PackageTools LIB_VERSION_MIN/MAX. Accepts 1.6 and 1.7 during transition. */
    const val LIB_VERSION_MIN = 1.3
    const val LIB_VERSION_MAX = 1.7

    fun isSupportedLibVersion(version: Double): Boolean =
        version in LIB_VERSION_MIN..LIB_VERSION_MAX

    /** Extension lib is encoded as MAJOR.MINOR in versionName's first two components. */
    fun libVersionFromVersionName(versionName: String): Double? =
        versionName.substringBeforeLast('.').toDoubleOrNull()
}

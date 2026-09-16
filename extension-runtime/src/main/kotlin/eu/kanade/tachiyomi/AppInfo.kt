package eu.kanade.tachiyomi

/**
 * Dareader-owned host info (link-compatible with extension-lib 1.3+ AppInfo).
 * Upstream Suwayomi backs this with generated BuildConfig; we report the
 * dareader runtime version instead.
 */
object AppInfo {
    fun getVersionCode(): Int = 10

    fun getVersionName(): String = "0.1.0"

    fun getSupportedImageMimeTypes(): List<String> = listOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/avif",
        "image/heif",
    )
}

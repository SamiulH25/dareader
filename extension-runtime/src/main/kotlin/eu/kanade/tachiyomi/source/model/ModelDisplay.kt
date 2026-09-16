@file:Suppress("ktlint:standard:property-naming")

package eu.kanade.tachiyomi.source.model

/**
 * Dareader-owned hardening: extensions sometimes return fresh [SManga]/[SChapter]
 * instances with `lateinit` fields never assigned (e.g. a detail response that
 * sets description/chapters but forgets `title`). Direct reads then throw
 * `UninitializedPropertyAccessException` on the UI thread and kill the app.
 * Prefer these accessors anywhere user-visible text is needed.
 */
fun SManga.displayTitle(): String =
    runCatching { title }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: runCatching { url }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "Untitled"

fun SChapter.displayName(): String =
    runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: runCatching { url }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "Chapter"

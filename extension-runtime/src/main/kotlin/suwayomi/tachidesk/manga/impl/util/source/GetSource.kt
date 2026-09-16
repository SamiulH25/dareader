package suwayomi.tachidesk.manga.impl.util.source

import eu.kanade.tachiyomi.source.Source
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Dareader-owned source registry, mirroring Suwayomi's GetSource.
 *
 * Sources are keyed by extension package name. [unregisterAllSources] clears
 * the registry and notifies reset listeners (e.g. NetworkHelper, which must
 * rebuild clients when the UA changes).
 */
object GetSource {
    private val sourcesByPkg = ConcurrentHashMap<String, List<Source>>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun onUnregisterAll(listener: () -> Unit) {
        listeners += listener
    }

    fun register(pkg: String, sources: List<Source>) {
        sourcesByPkg[pkg] = sources.toList()
    }

    fun unregister(pkg: String) {
        sourcesByPkg.remove(pkg)
    }

    fun findById(id: Long): Source? =
        sourcesByPkg.values.flatten().firstOrNull { it.id == id }

    fun allSources(): List<Source> = sourcesByPkg.values.flatten()

    fun unregisterAllSources() {
        sourcesByPkg.clear()
        listeners.forEach { it() }
    }
}

package suwayomi.tachidesk.manga.impl.util.source

import eu.kanade.tachiyomi.source.Source
import java.util.concurrent.ConcurrentHashMap

/**
 * Dareader-owned source registry, mirroring Suwayomi's GetSource.
 *
 * Sources are keyed by extension package name. There is deliberately no
 * bulk-clear hook: dareader's loaded handles are the only holders of live
 * `Source` instances, so wiping the registry could never be repaired by
 * anything (the old reset-on-UA-change hook made `findSource(id)` return null
 * for every source mid-session).
 */
object GetSource {
    private val sourcesByPkg = ConcurrentHashMap<String, List<Source>>()

    fun register(pkg: String, sources: List<Source>) {
        sourcesByPkg[pkg] = sources.toList()
    }

    fun unregister(pkg: String) {
        sourcesByPkg.remove(pkg)
    }

    fun findById(id: Long): Source? =
        sourcesByPkg.values.flatten().firstOrNull { it.id == id }

    fun allSources(): List<Source> = sourcesByPkg.values.flatten()
}

package dareader.ext.load

import dareader.ext.di.DareaderGraph
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

/** Child-first loader so extension classes win; API types come from the parent. */
private class ChildFirstLoader(
    urls: Array<URL>,
    parent: ClassLoader,
) : URLClassLoader(urls, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            if (name.startsWith("java.") || name.startsWith("jdk.")) {
                return super.loadClass(name, resolve)
            }
            return try {
                findClass(name).also { if (resolve) resolveClass(it) }
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
    }
}

data class LoadedExtension(
    val pkgName: String,
    val mainClass: String,
    val sources: List<Source>,
    val close: () -> Unit,
)

/**
 * Instantiates an extension's Source (or multi-source SourceFactory) from a
 * converted jar and returns its sources. Registers shared singletons on first
 * use and auto-registers the sources into [GetSource] under [pkgName].
 *
 * Lifetime: the jar at [jar] is memory-mapped and lazily read by the
 * classloader, so it MUST outlive the returned handle. This function never
 * deletes it; the caller retains the [jar] path and deletes it only after
 * calling [LoadedExtension.close], which unregisters the sources from
 * [GetSource] and closes the classloader.
 */
fun loadExtensionSources(jar: Path, className: String, pkgName: String): LoadedExtension {
    DareaderGraph.ensureCore()

    val loader = ChildFirstLoader(arrayOf(jar.toUri().toURL()), ExtensionLoaderAnchor::class.java.classLoader)
    var registered = false
    try {
        val clazz = Class.forName(className, true, loader)
        val instance = clazz.getDeclaredConstructor().newInstance()
        val sources: List<Source> = when (instance) {
            is Source -> listOf(instance)
            is SourceFactory -> instance.createSources()
            else -> error("unknown extension entry: ${instance.javaClass}")
        }
        GetSource.register(pkgName, sources)
        registered = true
        var closed = false
        return LoadedExtension(pkgName, className, sources) {
            synchronized(loader) {
                if (closed) return@LoadedExtension
                closed = true
            }
            try {
                GetSource.unregister(pkgName)
            } finally {
                loader.close()
            }
        }
    } catch (e: Throwable) {
        if (registered) GetSource.unregister(pkgName)
        loader.close()
        throw e
    }
}

private object ExtensionLoaderAnchor

/** What a jar class can serve as; null when it is not a usable entry. */
private enum class EntryKind { FACTORY, SOURCE }

/**
 * Classifies a jar class as an extension entry: concrete, publicly
 * no-arg-constructible, and a `SourceFactory` or `Source`. Loading without
 * initialization is enough for the scan, but linking resolves referenced
 * types, so callers must treat any [Throwable] from here as "not an entry".
 */
private fun classifyEntry(fqcn: String, loader: ClassLoader): EntryKind? {
    val cls = Class.forName(fqcn, false, loader)
    if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
    val instantiable = cls.declaredConstructors.any { Modifier.isPublic(it.modifiers) && it.parameterCount == 0 }
    if (!instantiable) return null
    return when {
        SourceFactory::class.java.isAssignableFrom(cls) -> EntryKind.FACTORY
        Source::class.java.isAssignableFrom(cls) -> EntryKind.SOURCE
        else -> null
    }
}

/**
 * Finds the loadable entry class of a converted extension jar: a concrete
 * `SourceFactory` implementation wins; otherwise a single concrete `Source`.
 * Classes load without initialization, so jars whose static init throws still
 * scan. Returns null when nothing loadable matches (caller rejects the
 * install).
 */
fun findExtensionEntryClass(jar: Path): String? {
    val loader =
        ChildFirstLoader(arrayOf(jar.toUri().toURL()), ExtensionLoaderAnchor::class.java.classLoader)
    return loader.use { cl ->
        val sources = mutableListOf<String>()
        val factories = mutableListOf<String>()
        java.util.jar.JarFile(jar.toFile()).use { jf ->
            for (entry in jf.entries()) {
                if (!entry.name.endsWith(".class")) continue
                val fqcn = entry.name.removeSuffix(".class").replace('/', '.')
                if ('$' in fqcn) continue // nested/anonymous impls are not extension entries
                val kind =
                    try {
                        classifyEntry(fqcn, cl)
                    } catch (_: Throwable) {
                        // Helper classes load but can fail to link (an Android
                        // activity catching a type this runtime lacks, say), and
                        // one unloadable helper must not abort the scan.
                        continue
                    }
                when (kind) {
                    EntryKind.FACTORY -> factories += fqcn
                    EntryKind.SOURCE -> sources += fqcn
                    null -> Unit
                }
            }
        }
        when {
            factories.size == 1 -> factories.single()
            factories.isEmpty() && sources.size == 1 -> sources.single()
            else -> null
        }
    }
}
